$ErrorActionPreference = "Continue"
$base = "http://localhost:8080/api"
$script:pass = 0
$script:fail = 0

function Check($label, $ok, $detail = "") {
  if ($ok) { $script:pass++; Write-Output ("  [PASS] " + $label + $(if ($detail) { " -> " + $detail } else { "" })) }
  else     { $script:fail++; Write-Output ("  [FAIL] " + $label + $(if ($detail) { " -> " + $detail } else { "" })) }
}
function Section($t) { Write-Output ""; Write-Output ("### " + $t) }
function Call($method, $path, $headers, $body) {
  try {
    $p = @{ Uri = "$base$path"; Method = $method; UseBasicParsing = $true }
    if ($headers) { $p.Headers = $headers }
    if ($body -ne $null) { $p.ContentType = "application/json"; $p.Body = $body }
    $r = Invoke-WebRequest @p
    return @{ code = [int]$r.StatusCode; body = $r.Content }
  } catch {
    $resp = $_.Exception.Response
    $code = if ($resp) { [int]$resp.StatusCode } else { 0 }
    $text = ""
    try { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream()); $text = $sr.ReadToEnd() } catch {}
    return @{ code = $code; body = $text }
  }
}
function Json($r) { try { return ($r.body | ConvertFrom-Json) } catch { return $null } }

# ---------------------------------------------------------------- AUTH
Section "AUTH"
$r = Call "POST" "/auth/login" $null '{"email":"admin@smartcampus.com","password":"Admin@123"}'
$j = Json $r
Check "login admin with email field" ($r.code -eq 200 -and $j.data.token) ("HTTP " + $r.code)
$admin = @{ Authorization = "Bearer " + $j.data.token }
$adminRole = $j.data.user.role
Check "role comes from server" ($adminRole -eq "ADMIN") $adminRole
Check "permissions returned" ($j.data.permissions.Count -gt 10) ("permissions=" + $j.data.permissions.Count)

$r = Call "POST" "/auth/login" $null '{"email":"admin@smartcampus.com","password":"wrong"}'
Check "bad password -> 401" ($r.code -eq 401) ("HTTP " + $r.code)

$r = Call "GET" "/auth/me" $admin $null
Check "/auth/me" ($r.code -eq 200 -and (Json $r).data.user.email -eq "admin@smartcampus.com") ((Json $r).data.user.email)

$r = Call "GET" "/auth/status" $null $null
Check "/auth/status anonymous" ($r.code -eq 200 -and (Json $r).data.authenticated -eq $false) ("HTTP " + $r.code)

$r = Call "GET" "/auth/status" $admin $null
Check "/auth/status signed in" ((Json $r).data.authenticated -eq $true) ((Json $r).data.user.role)

$r = Call "POST" "/auth/login" $null '{"email":"faculty@smartcampus.com","password":"Faculty@123"}'
$faculty = @{ Authorization = "Bearer " + (Json $r).data.token }
Check "login faculty" ($r.code -eq 200) ((Json $r).data.user.role)

$r = Call "POST" "/auth/login" $null '{"email":"student@smartcampus.com","password":"Student@123"}'
$sj = Json $r
$student = @{ Authorization = "Bearer " + $sj.data.token }
Check "login student" ($r.code -eq 200) ($sj.data.user.role)

# ---------------------------------------------------------------- AUTHZ
Section "AUTHORISATION (401 / 403)"
$r = Call "GET" "/students" $null $null
Check "no token -> 401" ($r.code -eq 401) ("HTTP " + $r.code)
$r = Call "GET" "/dashboard/statistics" $null $null
Check "no token dashboard -> 401" ($r.code -eq 401) ("HTTP " + $r.code)
$r = Call "GET" "/students" $student $null
Check "student reading register -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Call "POST" "/students" $student '{"name":"x"}'
Check "student creating -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Call "GET" "/users" $faculty $null
Check "faculty reading users -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Call "GET" "/reports/students" $student $null
Check "student exporting CSV -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Call "POST" "/concurrency/run" $faculty $null
Check "faculty running concurrency demo -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Call "GET" "/health" $null $null
Check "health is public" ($r.code -eq 200) ("HTTP " + $r.code)

# ---------------------------------------------------------------- READ
Section "LIST / SEARCH / FILTER / SORT / PAGE"
$r = Call "GET" "/students?page=0&size=3&sort=name&direction=asc" $admin $null
$j = Json $r
Check "students paged" ($r.code -eq 200 -and $j.data.content.Count -eq 3) ("rows=" + $j.data.content.Count + " total=" + $j.data.totalElements)
$studentTotal = $j.data.totalElements

$r = Call "GET" "/students?keyword=rahul" $admin $null
Check "students keyword search" ($r.code -eq 200) ("total=" + (Json $r).data.totalElements)

$r = Call "GET" "/students?department=Computer Science&status=ACTIVE" $admin $null
Check "students filter by department+status" ($r.code -eq 200) ("total=" + (Json $r).data.totalElements)

$r = Call "GET" "/students?sort=name&direction=desc&page=0&size=5" $admin $null
Check "students sorted desc" ($r.code -eq 200) ("sort=" + (Json $r).data.sort + " " + (Json $r).data.direction)

$r = Call "GET" "/students?sort=DROP%20TABLE&direction=x" $admin $null
Check "malicious sort whitelisted" ($r.code -eq 200) ("sort=" + (Json $r).data.sort)

$r = Call "GET" "/students/options" $admin $null
Check "students options" ($r.code -eq 200 -and (Json $r).data.departments.Count -gt 0) ("departments=" + (Json $r).data.departments.Count)

foreach ($m in @("faculty","courses","classrooms","laboratories","equipment","activities","bookings")) {
  $r = Call "GET" "/$m`?page=0&size=5" $admin $null
  $j = Json $r
  Check "$m list" ($r.code -eq 200 -and $j.data.content) ("total=" + $j.data.totalElements)
}

# ---------------------------------------------------------------- CRUD
Section "CRUD (create / read / update / delete)"
# remove leftovers from an earlier interrupted run
$leftover = @((Json (Call "GET" "/students?keyword=SMK-&size=100" $admin $null)).data.content)
foreach ($s in $leftover) { $null = Call "DELETE" "/students/$($s.id)" $admin $null }
if ($leftover.Count) { Write-Output ("  [setup] removed " + $leftover.Count + " student(s) from an earlier run") }

$roll = "SMK-" + (Get-Random -Maximum 9999)
$body = '{"usn":"' + $roll + '","name":"Smoke Tester","email":"' + $roll.ToLower() + '@smartcampus.com","gender":"MALE","department":"Information Technology","semester":6,"section":"A","phone":"9880001111","status":"ACTIVE","dateOfBirth":"2005-06-15","admissionYear":2023}'
$r = Call "POST" "/students" $admin $body
$j = Json $r
Check "create student" ($r.code -eq 201 -and $j.data.id) ("id=" + $j.data.id)
$sid = $j.data.id

$r = Call "GET" "/students/$sid" $admin $null
Check "read student" ($r.code -eq 200 -and (Json $r).data.usn -eq $roll) ((Json $r).data.name)

$body2 = '{"usn":"' + $roll + '","name":"Smoke Tester II","email":"' + $roll.ToLower() + '@smartcampus.com","gender":"MALE","department":"Information Technology","semester":7,"section":"A","phone":"9880001111","status":"ACTIVE","dateOfBirth":"2005-06-15","admissionYear":2023}'
$r = Call "PUT" "/students/$sid" $admin $body2
Check "update student" ($r.code -eq 200 -and (Json $r).data.name -eq "Smoke Tester II") ("sem=" + (Json $r).data.semester)

$r = Call "GET" "/students/$sid" $student $null
Check "student cannot read arbitrary profile -> 401/403" ($r.code -eq 401 -or $r.code -eq 403) ("HTTP " + $r.code)

$r = Call "POST" "/students" $admin $body
Check "duplicate USN rejected" ($r.code -eq 400 -or $r.code -eq 409) ("HTTP " + $r.code + " " + (Json $r).message)

$r = Call "POST" "/students" $admin '{"name":"","email":"not-an-email"}'
Check "invalid input -> 400 with field errors" ($r.code -eq 400) ((Json $r).message)

$r = Call "DELETE" "/students/$sid" $admin $null
Check "delete student" ($r.code -eq 200) ((Json $r).message)

$r = Call "GET" "/students/$sid" $admin $null
Check "deleted student gone -> 404" ($r.code -eq 404) ("HTTP " + $r.code)

# ---------------------------------------------------------------- BOOKINGS
Section "BOOKINGS + CONFLICT DETECTION"
# make the test repeatable: remove leftovers from a previous run
$existing = (Json (Call "GET" "/bookings?page=0&size=200" $admin $null)).data.content
$cleanupTargets = @($existing | Where-Object {
  $_.purpose -match "Conflict demonstration|Overlap|Adjacent|Thread race"
})
foreach ($b in $cleanupTargets) { $null = Call "DELETE" "/bookings/$($b.id)" $admin $null }
if ($cleanupTargets.Count) { Write-Output ("  [setup] removed " + $cleanupTargets.Count + " booking(s) from an earlier run") }

$r = Call "GET" "/bookings/resources" $admin $null
$resources = (Json $r).data
Check "booking resources dropdown" ($r.code -eq 200 -and $resources.Count -gt 0) ("resources=" + $resources.Count)

$lab = $resources | Where-Object { $_.type -eq "LABORATORY" } | Select-Object -First 1
$date = (Get-Date).AddDays(7).ToString("yyyy-MM-dd")
$b1 = '{"resourceType":"' + $lab.type + '","resourceId":' + $lab.id + ',"bookingDate":"' + $date + '","startTime":"10:00","endTime":"12:00","purpose":"Conflict demonstration"}'
$r = Call "POST" "/bookings" $admin $b1
$j = Json $r
Check "create booking" ($r.code -eq 201 -and $j.data.bookingRef) ($j.data.bookingRef + " for " + $j.data.resourceName)

$r = Call "POST" "/bookings" $admin $b1
$j2 = Json $r
Check "double booking -> 409" ($r.code -eq 409) ("HTTP " + $r.code)
Check "conflict message names the resource + slot" ($j2.message -match "already booked" -and $j2.message -match "10:00") $j2.message

$b3 = '{"resourceType":"' + $lab.type + '","resourceId":' + $lab.id + ',"bookingDate":"' + $date + '","startTime":"11:00","endTime":"13:00","purpose":"Overlap"}'
$r = Call "POST" "/bookings" $admin $b3
Check "partial overlap rejected -> 409" ($r.code -eq 409) ((Json $r).message)

$b4 = '{"resourceType":"' + $lab.type + '","resourceId":' + $lab.id + ',"bookingDate":"' + $date + '","startTime":"12:00","endTime":"14:00","purpose":"Adjacent"}'
$r = Call "POST" "/bookings" $admin $b4
Check "adjacent slot accepted" ($r.code -eq 201) ((Json $r).data.bookingRef)

$availBody = '{"resourceType":"' + $lab.type + '","resourceId":' + $lab.id + ',"date":"' + $date + '","startTime":"10:00","endTime":"11:00"}'
$r = Call "POST" "/bookings/availability" $admin $availBody
Check "availability check says booked" ($r.code -eq 200 -and (Json $r).data.available -eq $false) ((Json $r).data.message)

$availBody2 = '{"resourceType":"' + $lab.type + '","resourceId":' + $lab.id + ',"date":"' + $date + '","startTime":"16:00","endTime":"17:00"}'
$r = Call "POST" "/bookings/availability" $admin $availBody2
Check "availability check says free" ($r.code -eq 200 -and (Json $r).data.available -eq $true) ((Json $r).data.message)

# ---------------------------------------------------------------- CONCURRENCY
Section "MULTITHREADED CONCURRENCY DEMO"
$date2 = (Get-Date).AddDays(9).ToString("yyyy-MM-dd")
$ct = '{"resourceType":"' + $lab.type + '","resourceId":' + $lab.id + ',"date":"' + $date2 + '","startTime":"09:00","endTime":"11:00","purpose":"Thread race","attempts":5}'
$r = Call "POST" "/concurrency/run" $admin $ct
$j = Json $r
if ($r.code -eq 200) {
  Check "5 threads -> exactly 1 success" ($j.data.succeeded -eq 1) ("succeeded=" + $j.data.succeeded)
  Check "4 threads rejected" ($j.data.failed -eq 4) ("failed=" + $j.data.failed)
  Check "outcomes recorded per thread" ($j.data.outcomes.Count -eq 5) ("outcomes=" + $j.data.outcomes.Count)
  Write-Output ("       summary: " + $j.data.summary)
} else {
  Check "concurrency endpoint" $false ("HTTP " + $r.code + " " + $r.body)
}

# ---------------------------------------------------------------- REPORTS
Section "REPORTS / CSV EXPORT"
foreach ($type in @("students","faculty","courses","bookings","classrooms","laboratories","equipment","activities","resources")) {
  $r = Call "GET" "/reports/$type" $admin $null
  $isCsv = $r.body -match "(?m)^id,|^[A-Za-z]+," -or $r.body -match "usn|course_code|booking_ref"
  Check "export $type.csv" ($r.code -eq 200 -and $isCsv) ("bytes=" + $r.body.Length)
}
$r = Call "GET" "/reports/students?department=Computer%20Science" $admin $null
Check "filtered CSV export" ($r.code -eq 200) ("bytes=" + $r.body.Length)
$r = Call "GET" "/reports/audit" $admin $null
Check "audit trail endpoint" ($r.code -eq 200) ("entries=" + (Json $r).data.entries.Count)
$r = Call "GET" "/reports/types" $admin $null
Check "report types list" ($r.code -eq 200) ("types=" + (Json $r).data.Count)

# ---------------------------------------------------------------- DASHBOARD
Section "DASHBOARD"
$r = Call "GET" "/dashboard/statistics" $admin $null
$j = Json $r
Check "dashboard statistics" ($r.code -eq 200) ("students=" + $j.data.totalStudents + " faculty=" + $j.data.totalFaculty + " courses=" + $j.data.totalCourses)
Check "dashboard maps present" ($j.data.studentsByDepartment -and $j.data.bookingsByStatus -and $j.data.resourceUtilization) ""
Check "dashboard has events + recent bookings" ($j.data.upcomingEvents.Count -gt 0 -and $j.data.recentBookings.Count -gt 0) ("events=" + $j.data.upcomingEvents.Count + ", recentBookings=" + $j.data.recentBookings.Count)

# ---------------------------------------------------------------- USERS
Section "USERS (admin only)"
$r = Call "GET" "/users?page=0&size=10" $admin $null
$j = Json $r
Check "list users" ($r.code -eq 200) ("total=" + $j.data.totalElements)
$hasPassword = ($r.body -match '"password"')
Check "password hash never serialized" (-not $hasPassword) "no password field"

# ---------------------------------------------------------------- LOGOUT
Section "SESSION LIFECYCLE"
$r = Call "POST" "/auth/logout" $admin $null
Check "logout" ($r.code -eq 200) ((Json $r).message)
$r = Call "GET" "/auth/me" $admin $null
Check "token invalid after logout -> 401" ($r.code -eq 401) ("HTTP " + $r.code)

Write-Output ""
Write-Output ("========================================")
Write-Output ("TOTAL: " + ($script:pass + $script:fail) + "  PASS: " + $script:pass + "  FAIL: " + $script:fail)
Write-Output ("========================================")
