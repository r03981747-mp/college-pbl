<#
================================================================================
 Smart Campus :: frontend-check.ps1
 --------------------------------------------------------------------------------
 Static + served-side checks for the browser layer.  Run it while the backend
 is running on http://localhost:8080.

   ./tests/frontend-check.ps1

 It verifies:
   1. every page is actually served by Spring Boot (HTTP 200)
   2. every local <link href> / <script src> resolves to a real file
   3. every page has a matching js/<page>.js and every getElementById(...)
      target really exists in that HTML (catches the classic typo bug)
   4. every sidebar destination declared in js/shell.js exists
   5. shared CSS/JS files are present
================================================================================
#>

$ErrorActionPreference = "Continue"
$root     = "D:\college pbl\frontend"
$base     = "http://localhost:8080"
$script:pass = 0
$script:fail = 0

function Check($label, $ok, $detail = "") {
    if ($ok) { $script:pass++ ; Write-Output ("  [PASS] " + $label + $(if ($detail) { " -> " + $detail } else { "" })) }
    else     { $script:fail++ ; Write-Output ("  [FAIL] " + $label + $(if ($detail) { " -> " + $detail } else { "" })) }
}
function Section($t) { Write-Output "" ; Write-Output ("### " + $t) }

# skip the temporary test harnesses (copied in only while a UI test runs)
$pages = @(Get-ChildItem $root -Filter *.html | Where-Object { $_.Name -notlike '_*' } | Sort-Object Name)

# ------------------------------------------------------------------ 1. served
Section "1. Pages served by the backend"
foreach ($p in $pages) {
    try {
        $r = Invoke-WebRequest -Uri ($base + "/" + $p.Name) -UseBasicParsing -TimeoutSec 10
        Check ("GET /" + $p.Name) ($r.StatusCode -eq 200 -and $r.RawContentLength -gt 500) ($r.RawContentLength.ToString() + " bytes")
    } catch {
        Check ("GET /" + $p.Name) $false ("HTTP error: " + $_.Exception.Message)
    }
}

$assets = @(
    "css/style.css", "css/dashboard.css", "css/responsive.css", "css/theme.css",
    "css/login3d.css", "css/lightcord.css", "css/academic.css",
    "js/api.js", "js/common.js", "js/auth.js", "js/shell.js", "js/theme.js",
    "js/login.js", "js/lightcord.js", "js/academic.js"
)
foreach ($a in $assets) {
    Check ("asset " + $a) (Test-Path (Join-Path $root $a))
}

# ------------------------------------------------------ 2. local asset links
Section "2. Local <link href> and <script src> targets"
foreach ($p in $pages) {
    $html = [System.IO.File]::ReadAllText($p.FullName)
    $refs = @()
    $refs += [regex]::Matches($html, '<link[^>]+href="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
    $refs += [regex]::Matches($html, '<script[^>]+src="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }

    $local = @($refs | Where-Object { $_ -notmatch '^(https?:)?//' -and $_ -notmatch '^#' })
    $broken = @()
    foreach ($r in $local) {
        $clean = ($r -split '\?')[0]
        if (-not (Test-Path (Join-Path $root $clean))) { $broken += $r }
    }
    Check ($p.Name + " asset links") ($broken.Count -eq 0) `
        $(if ($broken.Count -gt 0) { "missing: " + ($broken -join ", ") } else { $local.Count.ToString() + " resolved" })
}

# ------------------------------------------- 3. page <-> script DOM id check
Section "3. Page script / DOM id consistency"
foreach ($p in $pages) {
    $scriptName = "js\" + $p.BaseName + ".js"
    if ($p.BaseName -eq "index") { $scriptName = "js\dashboard.js" }
    if ($p.BaseName -eq "login") { $scriptName = "js\login.js" }
    $scriptPath = Join-Path $root $scriptName
    if (-not (Test-Path $scriptPath)) {
        Check ($p.BaseName + " script exists") $false $scriptName
        continue
    }

    $html = [System.IO.File]::ReadAllText($p.FullName)
    $js   = [System.IO.File]::ReadAllText($scriptPath)

    $ids   = [regex]::Matches($html, 'id="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
    # ids created at runtime by the script itself (template strings) count too
    $ids  += [regex]::Matches($js, "id=[`"`']([^`"`']+)[`"`']") | ForEach-Object { $_.Groups[1].Value }
    $ids   = @($ids | Sort-Object -Unique)
    $used  = @()
    $used += [regex]::Matches($js, "getElementById\('([^']+)'\)") | ForEach-Object { $_.Groups[1].Value }
    # new academic modules use the local $('id') shortcut
    $used += [regex]::Matches($js, '\$\(\s*''([^'']+)''\s*\)') | ForEach-Object { $_.Groups[1].Value }
    $used  = @($used | Sort-Object -Unique)
    $miss  = @($used | Where-Object { $ids -notcontains $_ })

    Check ($p.BaseName + " DOM ids") ($miss.Count -eq 0) `
        $(if ($miss.Count -gt 0) { "missing ids: " + ($miss -join ", ") } else { $used.Count.ToString() + " ids ok" })

    # required skeleton parts (login.html has no sidebar by design)
    if ($p.BaseName -ne "login") {
        foreach ($need in @('id="sidebar"', 'id="topbar"', 'id="appFooter"', 'id="sidebarOverlay"')) {
            if ($html.IndexOf($need) -eq -1) {
                Check ($p.BaseName + " contains " + $need) $false
            }
        }
    }

    # login.html boots through Auth.bootLogin() instead of the module guard
    if ($p.BaseName -eq "login") {
        Check "login.js present" $true
        Check "light-cord scene linked" `
            (($html -match 'css/lightcord\.css') -and ($html -match 'js/lightcord\.js'))
    } else {
        Check ($p.BaseName + " calls Auth.requireAuth()") ($js -match 'Auth\.requireAuth\(\)')
        Check ($p.BaseName + " calls AppShell.init")       ($js -match 'AppShell\.init\(')
    }
}

# ------------------------------------------------------- 4. sidebar targets
Section "4. Sidebar destinations declared in js/shell.js"
$shell = [System.IO.File]::ReadAllText((Join-Path $root "js\shell.js"))
$targets = [regex]::Matches($shell, "key:\s*'([^']+\.html)'") | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
foreach ($t in $targets) {
    Check ("menu item " + $t) (Test-Path (Join-Path $root $t))
}

# ------------------------------------------------------------ 5. script order
Section "5. Script include order"
$requiredOrder = @("js/api.js", "js/common.js", "js/auth.js", "js/shell.js")
foreach ($p in $pages) {
    if ($p.BaseName -eq "login") { continue }
    $html = [System.IO.File]::ReadAllText($p.FullName)
    $lastPos = -1
    $bad = $false
    foreach ($s in $requiredOrder) {
        $pos = $html.IndexOf($s)
        if ($pos -eq -1) { $bad = $true; break }
        if ($pos -lt $lastPos) { $bad = $true; break }
        $lastPos = $pos
    }
    Check ($p.BaseName + " script order") (-not $bad) $(if ($bad) { "missing or out of order" } else { "ok" })
}

Write-Output ""
Write-Output "========================================"
Write-Output ("TOTAL: " + ($script:pass + $script:fail).ToString() + "  PASS: " + $script:pass.ToString() + "  FAIL: " + $script:fail.ToString())
Write-Output "========================================"
