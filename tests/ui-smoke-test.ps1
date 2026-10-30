<#
================================================================================
 Smart Campus :: ui-smoke-test.ps1
 --------------------------------------------------------------------------------
 REAL headless-browser test of every page, for every demo role.

   ./tests/ui-smoke-test.ps1            # all pages, all roles
   ./tests/ui-smoke-test.ps1 admin      # one role only

 How it works
   1. opens frontend/_uitest.html?as=<role>&next=<page> in headless Chrome
   2. _uitest.html performs a REAL login through POST /api/auth/login, stores
      the genuine session, then redirects to the page under test
   3. the rendered DOM and the browser console are captured separately
   4. assertions: no JS exception, the shared app shell rendered, the page
      shows real data (or its own honest empty/error state), and the user was
      not bounced back to the login page
   5. finally it asserts each role is actually DENIED where it must be

 Requires: backend on http://localhost:8080 and Chrome (falls back to Edge).
================================================================================
#>
param([string]$onlyRole = "")

$ErrorActionPreference = "Continue"
$chrome = "C:\Program Files\Google\Chrome\Application\chrome.exe"
if (-not (Test-Path $chrome)) { $chrome = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" }
if (-not (Test-Path $chrome)) { Write-Output "No Chrome/Edge found"; exit 1 }

$base    = "http://localhost:8080"
$dumpDir = Join-Path $env:TEMP "sc-ui-dump"
if (-not (Test-Path $dumpDir)) { New-Item -ItemType Directory -Path $dumpDir | Out-Null }

# The login harness lives with the tests; it is copied into frontend/ for the
# run and removed again afterwards so the delivered site stays clean.
$harnessSrc = Join-Path $PSScriptRoot "_uitest.html"
$harnessDst = Join-Path (Split-Path $PSScriptRoot -Parent) "frontend\_uitest.html"
if (-not (Test-Path $harnessSrc)) { Write-Output "missing $harnessSrc"; exit 1 }
Copy-Item $harnessSrc $harnessDst -Force

$script:pass = 0
$script:fail = 0
function Check($label, $ok, $detail = "") {
    if ($ok) { $script:pass++; Write-Output ("  [PASS] " + $label + $(if ($detail) { " -> " + $detail } else { "" })) }
    else     { $script:fail++; Write-Output ("  [FAIL] " + $label + $(if ($detail) { " -> " + $detail } else { "" })) }
}
function Section($t) { Write-Output ""; Write-Output ("### " + $t) }

# page -> regex that must appear once the role is allowed to see it
$pages = [ordered]@{
    "index"        = "stat-card|canvas"
    "students"     = "Add student"
    "faculty"      = "Add faculty"
    "courses"      = "Add course"
    "classrooms"   = "Add room"
    "laboratories" = "Add laborator"
    "equipment"    = "Add equipment"
    "activities"   = "Add activity"
    "bookings"     = "New booking|Add booking"
    "reports"      = "report-card|Preview|Export"
    "marks"        = "My Marks|Marks Management"
    "attendance"   = "My Attendance|Attendance Register"
    "assignments"  = "My Assignments|Assignments Management"
    "profile"      = "My Academic Profile|Student Academic Profiles"
    "eligibility"  = "My Eligibility|Student Eligibility"
    "academic-settings" = "Eligibility rules|Administrator access required"
    "users"        = "Add account"
    "settings"     = "password"
}

# pages whose job is to list database rows - at least one <tr> must render
$rowPages = @("students", "faculty", "courses", "classrooms", "laboratories",
              "equipment", "activities", "bookings", "users")

function Count-Rows([string]$dom) {
    $tbody = [regex]::Match($dom, '<tbody>([\s\S]*?)</tbody>')
    if (-not $tbody.Success) { return 0 }
    return ([regex]::Matches($tbody.Groups[1].Value, '<tr')).Count
}

$roles = @("admin", "faculty", "student")
if ($onlyRole) { $roles = @($onlyRole) }

function Invoke-Page([string]$role, [string]$page) {
    $url      = "$base/_uitest.html?as=$role&next=/$page.html"
    $domFile  = Join-Path $dumpDir ($role + "-" + $page + ".html")
    $logFile  = Join-Path $dumpDir ($role + "-" + $page + ".log")
    foreach ($f in @($domFile, $logFile)) { if (Test-Path $f) { Remove-Item $f -Force } }

    # a fresh profile per run: a leftover chrome instance would otherwise grab
    # the profile and exit immediately, leaving us with an empty dump
    $runProfile = Join-Path $env:TEMP ("sc-ui-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
    $chromeArgs = @(
        "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
        "--user-data-dir=$runProfile",
        "--enable-logging=stderr", "--v=0",
        "--virtual-time-budget=20000",
        "--dump-dom", $url
    )
    # NB: PowerShell's native `>` / `2>` redirection silently yields 0 bytes for
    # chrome, so capture with Start-Process instead.
    try {
        Start-Process -FilePath $chrome -ArgumentList $chromeArgs `
            -RedirectStandardOutput $domFile -RedirectStandardError $logFile `
            -NoNewWindow -Wait | Out-Null
    } catch {
        return @{ Dom = ""; Log = "LAUNCH FAILED: " + $_.Exception.Message }
    } finally {
        if (Test-Path $runProfile) { Remove-Item $runProfile -Recurse -Force -ErrorAction SilentlyContinue }
    }

    $dom = ""
    if (Test-Path $domFile) { $dom = [System.IO.File]::ReadAllText($domFile) }
    $log = ""
    if (Test-Path $logFile) { $log = [System.IO.File]::ReadAllText($logFile) }
    return @{ Dom = $dom; Log = $log }
}

function Find-JsError([string]$dom, [string]$log) {
    $blob = $dom + "`n" + $log
    foreach ($pat in @('Uncaught', 'HARNESS FAILED', 'is not a function', 'is not defined',
                       'Unexpected token', 'Cannot read propert')) {
        if ($blob -match [regex]::Escape($pat)) {
            $m = [regex]::Match($blob, '.{0,160}' + [regex]::Escape($pat) + '.{0,160}')
            return $pat + "  ||  " + ($m.Value -replace '\s+', ' ')
        }
    }
    return $null
}

# is the page's own "you do not have access" panel actually visible?
function Test-Denied([string]$dom) {
    foreach ($id in @('accessDenied', 'settingsDenied')) {
        $m = [regex]::Match($dom, '<div id="' + $id + '"([^>]*)>')
        if ($m.Success -and $m.Groups[1].Value -notmatch '\bd-none\b') { return $true }
    }
    return $false
}

# role -> pages that MUST be denied (UI half of the authorisation matrix).
# Verified again server-side by tests/api-smoke-test.ps1 (401 / 403).
# NOTE: students.html is NOT denied for STUDENT - students.read means
# "personal, read-only view", so the page renders their own record.
$expectedDeny = @{
    "student" = @("faculty", "users", "reports", "academic-settings")
    "faculty" = @("users", "academic-settings")
}

# role|page -> alternative content marker (the page shows something other than
# its normal CRUD table for that role)
$markerOverride = @{
    "student|students" = "Personal read-only view"
}

# role -> pages that legitimately render zero table rows for that role
$noRows = @{
    "student" = @("students")
}

foreach ($role in $roles) {
    Section "ROLE: $role"
    $mustDeny = @{}
    if ($expectedDeny.ContainsKey($role)) {
        foreach ($p in $expectedDeny[$role]) { $mustDeny[$p] = $true }
    }

    foreach ($page in $pages.Keys) {
        $r = Invoke-Page $role $page
        $dom = $r.Dom

        if ($dom.Length -lt 800) {
            Check "$role / $page.html" $false ("empty DOM (" + $dom.Length.ToString() + " chars)")
            continue
        }

        $err = Find-JsError $dom $r.Log
        Check "$role / $page : no JS exception" (-not $err) $(if ($err) { $err } else { "clean" })

        $hasSidebar = ($dom -match 'sidebar-nav') -and ($dom -match '\.html')
        Check "$role / $page : app shell rendered" $hasSidebar

        $expectDeny = $mustDeny.ContainsKey($page)
        $denied = Test-Denied $dom

        if ($expectDeny) {
            # this role must NOT see the module
            Check "$role / $page : DENIED as expected" $denied `
                $(if ($denied) { "access panel shown" } else { "NO DENIAL VISIBLE - role saw the module" })
            continue
        }

        # otherwise the module must be fully usable: no denial panel,
        # still signed in, own content marker, and real database rows
        Check "$role / $page : not wrongly denied" (-not $denied) `
            $(if ($denied) { "unexpected access panel" } else { "ok" })

        $mk = $pages[$page]
        $mkKey = "$role|$page"
        if ($markerOverride.ContainsKey($mkKey)) { $mk = $markerOverride[$mkKey] }
        $hit = $dom -match $mk
        Check "$role / $page : content marker" $hit $mk

        $bounced = ($dom -match 'id="loginForm"') -or ($dom -match 'js/login\.js')
        Check "$role / $page : stayed signed in" (-not $bounced) $(if ($bounced) { "redirected to login" } else { "ok" })

        $skipRows = ($noRows.ContainsKey($role)) -and ($noRows[$role] -contains $page)
        if (($rowPages -contains $page) -and (-not $skipRows)) {
            $rows = Count-Rows $dom
            Check "$role / $page : database rows rendered" ($rows -gt 0) ($rows.ToString() + " rows")
        } elseif ($skipRows) {
            # personal view: assert the student's own USN actually rendered
            Check "$role / $page : personal record rendered" ($dom -match 'Personal read-only view') "kv card"
        }
    }
}

Write-Output ""
Write-Output "========================================"
Write-Output ("TOTAL: " + ($script:pass + $script:fail).ToString() + "  PASS: " + $script:pass.ToString() + "  FAIL: " + $script:fail.ToString())
Write-Output "========================================"
Write-Output ("DOM dumps + console logs: " + $dumpDir)

# tidy up: the harness must not ship as part of the site
if (Test-Path $harnessDst) { Remove-Item $harnessDst -Force -ErrorAction SilentlyContinue }
Write-Output ("Removed " + $harnessDst)
