<#
================================================================================
 Smart Campus :: login-light-smoke-test.ps1
 --------------------------------------------------------------------------------
 REAL headless-browser test of the two newest features:

   1. the animated "pull the cord" login page (tests/_lighttest.html drives the
      real frontend/login.html inside an iframe)
   2. the circular global theme-colour picker on the dashboard

   ./tests/login-light-smoke-test.ps1

 What is asserted
   * the page opens with the LIGHT OFF and the form hidden
   * a drag on the cord stretches it and turns the light ON (form revealed)
   * a second pull turns it OFF again and hides the form - without clearing
     the typed credentials and without touching the session
   * the existing validation, the server's wrong-password error and the real
     redirect to the dashboard still work
   * circular swatches apply the colour, update the label/aria state, persist
     in localStorage (sc.theme.color) and are restored on reload
   * no JavaScript exception is raised anywhere

 Requires: backend on http://localhost:8080 and Chrome (falls back to Edge).
================================================================================
#>

$ErrorActionPreference = "Continue"
$chrome = "C:\Program Files\Google\Chrome\Application\chrome.exe"
if (-not (Test-Path $chrome)) { $chrome = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" }
if (-not (Test-Path $chrome)) { Write-Output "No Chrome/Edge found"; exit 1 }

$base    = "http://localhost:8080"
$root    = "D:\college pbl\frontend"
$dumpDir = Join-Path $env:TEMP "sc-light-dump"
if (-not (Test-Path $dumpDir)) { New-Item -ItemType Directory -Path $dumpDir | Out-Null }

# the harness lives with the tests and is copied into frontend/ for the run
$harnessSrc = Join-Path $PSScriptRoot "_lighttest.html"
$harnessDst = Join-Path (Split-Path $PSScriptRoot -Parent) "frontend\_lighttest.html"
if (-not (Test-Path $harnessSrc)) { Write-Output "missing $harnessSrc"; exit 1 }
Copy-Item $harnessSrc $harnessDst -Force

$script:pass = 0
$script:fail = 0
function Check($label, $ok, $detail = "") {
    if ($ok) { $script:pass++; Write-Output ("  [PASS] " + $label + $(if ($detail) { " -> " + $detail } else { "" })) }
    else     { $script:fail++; Write-Output ("  [FAIL] " + $label + $(if ($detail) { " -> " + $detail } else { "" })) }
}
function Section($t) { Write-Output "" ; Write-Output ("### " + $t) }

# --------------------------------------------------------------- backend up?
try {
    $probe = Invoke-WebRequest -Uri ($base + "/login.html") -UseBasicParsing -TimeoutSec 10
    Check "backend serves /login.html" ($probe.StatusCode -eq 200)
} catch {
    Check "backend serves /login.html" $false ("start it first: mvn spring-boot:run - " + $_.Exception.Message)
    if ($script:fail -gt 0) { exit 1 }
}

# ------------------------------------------------------------ static checks
Section "1. Light-cord login page (static)"
$html = [System.IO.File]::ReadAllText((Join-Path $root "login.html"))
Check "login.html links css/lightcord.css"  ($html -match 'css/lightcord\.css')
Check "login.html links js/lightcord.js"    ($html -match 'js/lightcord\.js')
Check "login.html links css/responsive.css" ($html -match 'css/responsive\.css')
Check "room starts with data-light=off"     ($html -match 'data-light="off"')
Check "cord handle markup present"          ($html -match 'id="lightHandle"')
Check "room layers present"                 ($html -match 'class="room-veil"' -and $html -match 'class="room-glow"')
Check "instruction text present"            ($html -match 'Pull the cord to turn on the light')
Check 'heading is "Welcome Back!"'          ($html -match 'Welcome Back!')
Check "existing form untouched"             ($html -match 'id="loginForm"' -and $html -match 'id="email"' -and $html -match 'id="password"')
Check "noscript fallback shows the form"    ($html -match '<noscript>')

$lcCss = [System.IO.File]::ReadAllText((Join-Path $root "css\lightcord.css"))
Check "reduced-motion alternative in CSS"   ($lcCss -match 'prefers-reduced-motion')
Check "touch drag never scrolls the page"   ($lcCss -match 'touch-action:\s*none')
Check "keyboard focus indicator"            ($lcCss -match 'focus-visible')

$lcJs = [System.IO.File]::ReadAllText((Join-Path $root "js\lightcord.js"))
Check "cord listens for pointer drags"      ($lcJs -match 'pointerdown')
Check "click on the handle toggles"         ($lcJs -match "addEventListener\('click'")
Check "never touches browser storage" (-not ($lcJs -match 'localStorage\.|sessionStorage\.|document\.cookie'))

# ------------------------------------------------------- headless interaction
function Run-Harness([string]$step, [string]$name) {
    $domFile = Join-Path $dumpDir ($name + ".html")
    $logFile = Join-Path $dumpDir ($name + ".log")
    foreach ($f in @($domFile, $logFile)) { if (Test-Path $f) { Remove-Item $f -Force } }

    # a fresh profile every time: no leftover session, no stored theme
    $profile = Join-Path $env:TEMP ("sc-light-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
    $args = @(
        "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
        "--user-data-dir=$profile",
        "--enable-logging=stderr", "--v=0",
        "--window-size=1440,900",
        "--virtual-time-budget=40000",
        "--dump-dom", ($base + "/_lighttest.html?step=" + $step)
    )
    try {
        Start-Process -FilePath $chrome -ArgumentList $args `
            -RedirectStandardOutput $domFile -RedirectStandardError $logFile `
            -NoNewWindow -Wait | Out-Null
    } catch {
        return @{ Dom = ""; Log = "LAUNCH FAILED: " + $_.Exception.Message }
    } finally {
        if (Test-Path $profile) { Remove-Item $profile -Recurse -Force -ErrorAction SilentlyContinue }
    }

    $dom = ""; if (Test-Path $domFile) { $dom = [System.IO.File]::ReadAllText($domFile) }
    $log = ""; if (Test-Path $logFile) { $log = [System.IO.File]::ReadAllText($logFile) }
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

Section "2. Live interaction (headless Chrome)"
$r = Run-Harness "login" "login-run"
$err = Find-JsError $r.Dom $r.Log
Check "no JavaScript exception" (-not $err) $(if ($err) { $err } else { "clean" })

$pre = [regex]::Match($r.Dom, '<pre id="out">([\s\S]*?)</pre>')
if (-not $pre.Success) {
    Check "harness reported results" $false ("no result block in " + $r.Dom.Length.ToString() + " chars")
} else {
    $text  = [System.Net.WebUtility]::HtmlDecode($pre.Groups[1].Value)
    $lines = @($text -split "\r?\n" | Where-Object { $_ -match '^(PASS|FAIL) ' })
    Check "harness finished the whole run" ($text -match 'DONE') ($lines.Count.ToString() + " assertions")
    foreach ($line in $lines) {
        $kind   = if ($line.StartsWith('PASS ')) { 'PASS' } else { 'FAIL' }
        $label  = $line.Substring(5)
        $detail = ""
        if ($label -match '^(.*?)\s\s\[(.*)\]$') { $label = $Matches[1]; $detail = $Matches[2] }
        Check $label ($kind -eq 'PASS') $detail
    }
}

# ------------------------------------------------------------------ summary
Write-Output ""
Write-Output "========================================"
Write-Output ("TOTAL: " + ($script:pass + $script:fail).ToString() + "  PASS: " + $script:pass.ToString() + "  FAIL: " + $script:fail.ToString())
Write-Output "========================================"
Write-Output ("DOM dump + console log: " + $dumpDir)

# tidy up: the harness must not ship as part of the site
if (Test-Path $harnessDst) { Remove-Item $harnessDst -Force -ErrorAction SilentlyContinue }
Write-Output ("Removed " + $harnessDst)
