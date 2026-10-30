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
function TokenOf($headers) {
  if (-not $headers -or -not $headers.Authorization) { return $null }
  return ($headers.Authorization -replace '^Bearer\s+', '')
}
function UploadFile($path, $headers, $filePath, $contentType) {
  Add-Type -AssemblyName System.Net.Http -ErrorAction SilentlyContinue
  $client = New-Object System.Net.Http.HttpClient
  try {
    $token = TokenOf $headers
    if ($token) {
      $client.DefaultRequestHeaders.Authorization =
        New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $token)
    }
    $content = New-Object System.Net.Http.MultipartFormDataContent
    try {
      $bytes = [System.IO.File]::ReadAllBytes($filePath)
      $fileContent = New-Object System.Net.Http.ByteArrayContent(,$bytes)
      $fileContent.Headers.ContentType =
        [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($contentType)
      $content.Add($fileContent, "file", [System.IO.Path]::GetFileName($filePath))
      $response = $client.PostAsync("$base$path", $content).Result
      $text = $response.Content.ReadAsStringAsync().Result
      return @{ code = [int]$response.StatusCode; body = $text }
    } finally {
      $content.Dispose()
    }
  } catch {
    return @{ code = 0; body = $_.Exception.Message }
  } finally {
    $client.Dispose()
  }
}
function DownloadFile($path, $headers) {
  Add-Type -AssemblyName System.Net.Http -ErrorAction SilentlyContinue
  $client = New-Object System.Net.Http.HttpClient
  try {
    $token = TokenOf $headers
    if ($token) {
      $client.DefaultRequestHeaders.Authorization =
        New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $token)
    }
    $response = $client.GetAsync("$base$path").Result
    $bytes = $response.Content.ReadAsByteArrayAsync().Result
    return @{ code = [int]$response.StatusCode; length = $bytes.Length }
  } catch {
    return @{ code = 0; length = 0; body = $_.Exception.Message }
  } finally {
    $client.Dispose()
  }
}

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

# ---------------------------------------------------------------- ACADEMIC READS + AUTHORIZATION
Section "ACADEMIC READS / ROLE SCOPE"
$allCourses = (Json (Call "GET" "/academics/courses" $admin $null)).data
$facultyCourses = (Json (Call "GET" "/academics/courses" $faculty $null)).data
$studentCourses = (Json (Call "GET" "/academics/courses" $student $null)).data
Check "admin sees all subjects" ($allCourses.Count -gt 0) ("subjects=" + $allCourses.Count)
Check "faculty sees only managed subjects" ($facultyCourses.Count -gt 0 -and $facultyCourses.Count -le $allCourses.Count) ("subjects=" + $facultyCourses.Count)
Check "student sees only enrolled subjects" ($studentCourses.Count -gt 0 -and $studentCourses.Count -lt $allCourses.Count) ("subjects=" + $studentCourses.Count)

$studentProfile = (Json (Call "GET" "/academics/profile/me" $student $null)).data
$studentId = $studentProfile.student.id
Check "student own profile" ($studentProfile.student -and $studentId) ("studentId=" + $studentId)
$r = Call "GET" "/academics/eligibility/me" $student $null
$studentEligibility = (Json $r).data
Check "student own eligibility" ($r.code -eq 200 -and $studentEligibility.status) ($studentEligibility.status + " / " + $studentEligibility.summary)
Check "eligibility explains checks" ($studentEligibility.checks.Count -gt 0) ("checks=" + $studentEligibility.checks.Count)
$r = Call "GET" "/marks/my" $student $null
Check "student own marks" ($r.code -eq 200 -and (Json $r).data.courses) ("subjects=" + (Json $r).data.courses.Count)
$r = Call "GET" "/attendance/my" $student $null
Check "student own attendance" ($r.code -eq 200 -and (Json $r).data.courses) ("subjects=" + (Json $r).data.courses.Count)

$r = Call "GET" "/academics/students" $student $null
$studentList = (Json $r).data
Check "student student-list scoped to self" ($r.code -eq 200 -and $studentList.Count -eq 1 -and $studentList[0].student.id -eq $studentId) ("rows=" + $studentList.Count)
$adminStudents = (Json (Call "GET" "/academics/students" $admin $null)).data
$otherStudent = $adminStudents | Where-Object { $_.student.id -ne $studentId } | Select-Object -First 1
if ($otherStudent) {
  $r = Call "GET" ("/academics/students/" + $otherStudent.student.id + "/profile") $student $null
  Check "student cannot read another profile -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
}
$r = Call "GET" "/academics/students" $faculty $null
Check "faculty student-list scoped" ($r.code -eq 200 -and (Json $r).data.Count -gt 0) ("rows=" + (Json $r).data.Count)
$r = Call "GET" "/academics/eligibility/students" $faculty $null
Check "faculty eligibility list" ($r.code -eq 200) ("rows=" + (Json $r).data.Count)

$unassignedCourse = $allCourses | Where-Object { $_.id -notin @($facultyCourses | ForEach-Object { $_.id }) } | Select-Object -First 1
if ($unassignedCourse) {
  $r = Call "GET" ("/attendance/sheet?courseId=" + $unassignedCourse.id + "&date=2026-09-21") $faculty $null
  Check "faculty cannot read unassigned attendance -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
  $badAssessment = '{"courseId":' + $unassignedCourse.id + ',"title":"Unauthorized smoke","assessmentType":"QUIZ","term":"2026-SMOKE","maxScore":10,"published":false}'
  $r = Call "POST" "/marks/assessments" $faculty $badAssessment
  Check "faculty cannot create assessment in unassigned subject -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
  $badAssignment = '{"courseId":' + $unassignedCourse.id + ',"title":"Unauthorized smoke","instructions":"x","dueAt":"2030-01-01T23:59:00","allowedFileTypes":"txt","maxFileSizeMb":1,"maxScore":10,"allowLate":false,"allowResubmission":false,"published":false}'
  $r = Call "POST" "/assignments" $faculty $badAssignment
  Check "faculty cannot create assignment in unassigned subject -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
}

# ---------------------------------------------------------------- SETTINGS
Section "ELIGIBILITY SETTINGS (admin write / role denial)"
$r = Call "GET" "/academics/settings" $admin $null
$originalSettings = (Json $r).data
Check "read eligibility settings" ($r.code -eq 200 -and $originalSettings) ("rulesConfigured=" + $originalSettings.rulesConfigured)
$modifiedSettings = @{
  minAttendancePercentage = 80
  minMarksPercentage = 45
  minAssessmentsRequired = 2
  minAttendanceRecords = 2
  requireMarks = $true
  requireAttendance = $true
  lateCountsAsPresent = $true
} | ConvertTo-Json -Compress
$r = Call "PUT" "/academics/settings" $admin $modifiedSettings
$savedSettings = (Json $r).data
Check "admin updates settings" ($r.code -eq 200 -and $savedSettings.rulesConfigured -eq $true) ("attendance=" + $savedSettings.minAttendancePercentage)
$r = Call "GET" "/academics/settings" $student $null
Check "student can read rules" ($r.code -eq 200) ("minMarks=" + (Json $r).data.minMarksPercentage)
$r = Call "PUT" "/academics/settings" $student $modifiedSettings
Check "student cannot update rules -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Call "PUT" "/academics/settings" $admin '{"minAttendancePercentage":-1,"minMarksPercentage":45,"minAssessmentsRequired":2,"minAttendanceRecords":2,"requireMarks":true,"requireAttendance":true,"lateCountsAsPresent":true}'
Check "invalid attendance threshold -> 400" ($r.code -eq 400) ((Json $r).message)
$restoreSettings = @{
  minAttendancePercentage = $originalSettings.minAttendancePercentage
  minMarksPercentage = $originalSettings.minMarksPercentage
  minAssessmentsRequired = $originalSettings.minAssessmentsRequired
  minAttendanceRecords = $originalSettings.minAttendanceRecords
  requireMarks = $originalSettings.requireMarks
  requireAttendance = $originalSettings.requireAttendance
  lateCountsAsPresent = $originalSettings.lateCountsAsPresent
} | ConvertTo-Json -Compress
$r = Call "PUT" "/academics/settings" $admin $restoreSettings
Check "restore original settings" ($r.code -eq 200) ("attendance=" + (Json $r).data.minAttendancePercentage)

# ---------------------------------------------------------------- MENTOR ASSIGNMENT
Section "CLASS TEACHER / MENTOR ASSIGNMENT"
$r = Call "GET" "/academics/faculty-options" $admin $null
$facultyOptions = (Json $r).data
Check "admin faculty options" ($r.code -eq 200 -and $facultyOptions.Count -gt 0) ("faculty=" + $facultyOptions.Count)
$r = Call "GET" "/academics/faculty-options" $faculty $null
Check "faculty cannot list admin faculty options -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$target = $adminStudents | Where-Object { $_.student.id -eq $studentId } | Select-Object -First 1
if ($target -and $facultyOptions.Count) {
  $newTeacher = $facultyOptions | Where-Object { $_.id -ne $target.student.classTeacherId } | Select-Object -First 1
  if (-not $newTeacher) { $newTeacher = $facultyOptions[0] }
  $newMentor = $facultyOptions | Where-Object { $_.id -ne $target.student.mentorId -and $_.id -ne $newTeacher.id } | Select-Object -First 1
  if (-not $newMentor) { $newMentor = $newTeacher }
  $assignBody = @{ classTeacherId = $newTeacher.id; mentorId = $newMentor.id } | ConvertTo-Json -Compress
  $r = Call "PUT" ("/academics/students/" + $studentId + "/mentors") $admin $assignBody
  $assigned = (Json $r).data
  Check "admin assigns class teacher + mentor" ($r.code -eq 200 -and $assigned.classTeacherId -eq $newTeacher.id -and $assigned.mentorId -eq $newMentor.id) ("teacher=" + $assigned.classTeacherName + ", mentor=" + $assigned.mentorName)
  $r = Call "PUT" ("/academics/students/" + $studentId + "/mentors") $faculty $assignBody
  Check "faculty cannot assign mentors -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
  $restoreMentors = @{ classTeacherId = $target.student.classTeacherId; mentorId = $target.student.mentorId } | ConvertTo-Json -Compress
  $r = Call "PUT" ("/academics/students/" + $studentId + "/mentors") $admin $restoreMentors
  Check "restore original mentors" ($r.code -eq 200) ("teacher=" + (Json $r).data.classTeacherName)
}

# ---------------------------------------------------------------- MARKS
Section "MARKS + PUBLICATION WORKFLOW"
$smokeTitle = "SMOKE-MARKS-" + (Get-Random -Maximum 99999)
$assessmentBody = @{
  courseId = 1
  title = $smokeTitle
  assessmentType = "QUIZ"
  term = "2026-SMOKE"
  maxScore = 10
  weight = 5
  assessmentDate = "2026-10-29"
  published = $false
} | ConvertTo-Json -Compress
$r = Call "POST" "/marks/assessments" $faculty $assessmentBody
$assessment = (Json $r).data
Check "faculty creates assessment" ($r.code -eq 201 -and $assessment.id) ("id=" + $assessment.id)
$assessmentId = $assessment.id
$r = Call "GET" "/marks/assessments" $student $null
$studentAssessments = (Json $r).data
Check "draft assessment hidden from student" (-not ($studentAssessments | Where-Object { $_.id -eq $assessmentId })) ("student assessments=" + $studentAssessments.Count)
$r = Call "GET" ("/marks/assessments/" + $assessmentId + "/sheet") $faculty $null
$sheet = (Json $r).data
Check "faculty marks sheet" ($r.code -eq 200 -and $sheet.marks.Count -gt 0) ("rows=" + $sheet.marks.Count)
$markRow = $sheet.marks | Where-Object { $_.studentId -eq $studentId } | Select-Object -First 1
if (-not $markRow) { $markRow = $sheet.marks[0] }
$markBody = @{ marks = @(@{ studentId = $markRow.studentId; score = 9; remarks = "Smoke mark" }) } | ConvertTo-Json -Compress
$r = Call "PUT" ("/marks/assessments/" + $assessmentId + "/marks") $faculty $markBody
$savedSheet = (Json $r).data
$savedMark = $savedSheet.marks | Where-Object { $_.studentId -eq $markRow.studentId } | Select-Object -First 1
Check "faculty saves marks" ($r.code -eq 200 -and $savedMark.score -eq 9) ("score=" + $savedMark.score)
$r = Call "PUT" ("/marks/assessments/" + $assessmentId + "/marks") $faculty ('{"marks":[{"studentId":' + $markRow.studentId + ',"score":11}]}')
Check "score above maximum -> 400" ($r.code -eq 400) ((Json $r).message)
$r = Call "GET" "/marks/my" $student $null
Check "draft marks hidden from student" (-not ((Json $r).data.marks | Where-Object { $_.assessmentId -eq $assessmentId })) "no draft mark"
$r = Call "PUT" ("/marks/assessments/" + $assessmentId + "/publish?published=true") $faculty $null
Check "faculty publishes assessment" ($r.code -eq 200 -and (Json $r).data.published -eq $true) ("HTTP " + $r.code)
$r = Call "GET" "/marks/my" $student $null
$publishedMark = (Json $r).data.marks | Where-Object { $_.assessmentId -eq $assessmentId } | Select-Object -First 1
Check "student sees published mark" ($r.code -eq 200 -and $publishedMark -and $publishedMark.score -eq 9) ("score=" + $publishedMark.score)
$r = Call "POST" "/marks/assessments" $student $assessmentBody
Check "student cannot create assessment -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Call "GET" ("/marks/assessments/" + $assessmentId + "/sheet") $student $null
Check "student cannot read marks sheet -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Call "DELETE" ("/marks/assessments/" + $assessmentId) $faculty $null
Check "faculty deletes assessment" ($r.code -eq 200) ((Json $r).message)
$r = Call "GET" "/marks/my" $student $null
Check "deleted assessment gone from student" (-not ((Json $r).data.marks | Where-Object { $_.assessmentId -eq $assessmentId })) "no deleted mark"

# ---------------------------------------------------------------- ATTENDANCE
Section "ATTENDANCE + CORRECTION AUDIT"
$attendanceDate = "2026-09-21"
$r = Call "GET" ("/attendance/sheet?courseId=1&date=" + $attendanceDate) $faculty $null
$attendanceSheet = (Json $r).data
Check "faculty attendance sheet" ($r.code -eq 200 -and $attendanceSheet.rows.Count -gt 0) ("rows=" + $attendanceSheet.rows.Count)
$originalAttendance = @($attendanceSheet.rows)
$originalRecords = @($originalAttendance | ForEach-Object { @{ studentId = $_.student.id; status = $_.status; remarks = $_.remarks } })
$originalAttendanceBody = @{ courseId = 1; date = $attendanceDate; records = $originalRecords } | ConvertTo-Json -Compress
$r = Call "PUT" "/attendance/sheet" $faculty $originalAttendanceBody
Check "faculty saves attendance" ($r.code -eq 200 -and (Json $r).data.rows.Count -eq $originalAttendance.Count) ("rows=" + (Json $r).data.rows.Count)
$studentAttendanceRow = $originalAttendance | Where-Object { $_.student.id -eq $studentId } | Select-Object -First 1
if (-not $studentAttendanceRow) { $studentAttendanceRow = $originalAttendance[0] }
$firstStudent = $studentAttendanceRow.student
$firstRecordId = $studentAttendanceRow.recordId
$correctionRecords = @($originalRecords | ForEach-Object {
  if ($_.studentId -eq $firstStudent.id) { @{ studentId = $_.studentId; status = "ABSENT"; remarks = "Smoke correction" } }
  else { $_ }
})
$correctionBody = @{ courseId = 1; date = $attendanceDate; records = $correctionRecords } | ConvertTo-Json -Compress
$r = Call "PUT" "/attendance/sheet" $faculty $correctionBody
$correctedSheet = (Json $r).data
$correctedRow = $correctedSheet.rows | Where-Object { $_.student.id -eq $firstStudent.id } | Select-Object -First 1
Check "attendance correction updates existing row" ($r.code -eq 200 -and $correctedRow.status -eq "ABSENT" -and $correctedRow.recordId -eq $firstRecordId) ("recordId=" + $correctedRow.recordId)
Check "correction records audit name" ($correctedRow.updatedByName) ("updatedBy=" + $correctedRow.updatedByName)
$r = Call "GET" "/attendance/my" $student $null
$studentAttendance = (Json $r).data
$course1 = $studentAttendance.courses | Where-Object { $_.course.id -eq 1 } | Select-Object -First 1
$attendanceRecord = $course1.records | Where-Object { $_.attendanceDate -eq $attendanceDate } | Select-Object -First 1
Check "student sees corrected attendance" ($attendanceRecord -and $attendanceRecord.status -eq "ABSENT") ("status=" + $attendanceRecord.status)
$r = Call "PUT" "/attendance/sheet" $student $correctionBody
Check "student cannot save attendance -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$badAttendance = @{ courseId = 1; date = $attendanceDate; records = @(@{ studentId = $firstStudent.id; status = "MAYBE" }) } | ConvertTo-Json -Compress
$r = Call "PUT" "/attendance/sheet" $faculty $badAttendance
Check "invalid attendance status -> 400" ($r.code -eq 400) ((Json $r).message)
$futureAttendance = @{ courseId = 1; date = (Get-Date).AddDays(1).ToString("yyyy-MM-dd"); records = @(@{ studentId = $firstStudent.id; status = "PRESENT" }) } | ConvertTo-Json -Compress
$r = Call "PUT" "/attendance/sheet" $faculty $futureAttendance
Check "future attendance -> 400" ($r.code -eq 400) ((Json $r).message)
$r = Call "PUT" "/attendance/sheet" $faculty $originalAttendanceBody
Check "restore original attendance" ($r.code -eq 200) ("rows=" + (Json $r).data.rows.Count)

# ---------------------------------------------------------------- ASSIGNMENTS + UPLOADS
Section "ASSIGNMENTS / SECURE UPLOAD / REVIEW"
$tmpDir = Join-Path $env:TEMP ("sc-api-smoke-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
New-Item -ItemType Directory -Path $tmpDir | Out-Null
$validFile = Join-Path $tmpDir "smoke.txt"
$validPdf = Join-Path $tmpDir "smoke.pdf"
$badFile = Join-Path $tmpDir "smoke.exe"
$largeFile = Join-Path $tmpDir "large.txt"
[System.IO.File]::WriteAllText($validFile, "Smart Campus smoke submission.")
[System.IO.File]::WriteAllText($validPdf, "%PDF-1.4`nSmoke PDF")
[System.IO.File]::WriteAllText($badFile, "not allowed")
$largeBytes = New-Object byte[] (1572864)
[System.IO.File]::WriteAllBytes($largeFile, $largeBytes)
$assignmentTitle = "SMOKE-ASSIGNMENT-" + (Get-Random -Maximum 99999)
$assignmentBody = @{
  courseId = 1
  title = $assignmentTitle
  instructions = "Upload a short text file for the smoke test."
  dueAt = "2030-01-01T23:59:00"
  allowedFileTypes = "txt,pdf"
  maxFileSizeMb = 1
  maxScore = 10
  allowLate = $false
  allowResubmission = $true
  published = $true
} | ConvertTo-Json -Compress
$r = Call "POST" "/assignments" $faculty $assignmentBody
$assignment = (Json $r).data
Check "faculty creates published assignment" ($r.code -eq 201 -and $assignment.id) ("id=" + $assignment.id)
$assignmentId = $assignment.id
$r = Call "GET" "/assignments" $student $null
$studentAssignments = (Json $r).data
$visibleAssignment = $studentAssignments | Where-Object { $_.id -eq $assignmentId } | Select-Object -First 1
Check "student sees published assignment" ($r.code -eq 200 -and $visibleAssignment -and -not $visibleAssignment.mySubmission) ("assignments=" + $studentAssignments.Count)
$r = UploadFile ("/assignments/" + $assignmentId + "/submit") $student $validFile "text/plain"
$submission = (Json $r).data
Check "student uploads valid file" ($r.code -eq 201 -and $submission.id -and $submission.status -eq "SUBMITTED") ("file=" + $submission.originalFileName)
$submissionId = $submission.id
$r = UploadFile ("/assignments/" + $assignmentId + "/submit") $student $badFile "application/octet-stream"
Check "disallowed file type -> 400" ($r.code -eq 400) ((Json $r).message)
$r = UploadFile ("/assignments/" + $assignmentId + "/submit") $student $largeFile "text/plain"
Check "oversized file -> 400" ($r.code -eq 400) ((Json $r).message)
$r = UploadFile ("/assignments/" + $assignmentId + "/submit") $student $validPdf "application/pdf"
$resubmission = (Json $r).data
Check "allowed resubmission replaces file" ($r.code -eq 201 -and $resubmission.attemptNo -eq 2) ("attempt=" + $resubmission.attemptNo)
$r = UploadFile ("/assignments/" + $assignmentId + "/submit") $faculty $validFile "text/plain"
Check "faculty cannot upload as student -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Call "PUT" ("/assignments/" + $assignmentId + "/submissions/" + $submissionId + "/review") $student '{"grade":8,"feedback":"x"}'
Check "student cannot review -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Call "PUT" ("/assignments/" + $assignmentId + "/submissions/" + $submissionId + "/review") $faculty '{"grade":8,"feedback":"Smoke feedback"}'
$reviewed = (Json $r).data
Check "faculty reviews submission" ($r.code -eq 200 -and $reviewed.status -eq "GRADED" -and $reviewed.grade -eq 8) ("grade=" + $reviewed.grade)
$r = Call "GET" "/assignments" $student $null
$gradedAssignment = (Json $r).data | Where-Object { $_.id -eq $assignmentId } | Select-Object -First 1
Check "student sees grade + feedback" ($gradedAssignment.mySubmission.status -eq "GRADED" -and $gradedAssignment.mySubmission.feedback -eq "Smoke feedback") ("grade=" + $gradedAssignment.mySubmission.grade)
$r = DownloadFile ("/assignments/submissions/" + $submissionId + "/file") $student
Check "student downloads own file" ($r.code -eq 200 -and $r.length -gt 0) ("bytes=" + $r.length)
$r = DownloadFile ("/assignments/submissions/" + $submissionId + "/file") $faculty
Check "faculty downloads managed submission" ($r.code -eq 200 -and $r.length -gt 0) ("bytes=" + $r.length)
$r = DownloadFile ("/assignments/submissions/" + $submissionId + "/file") $null
Check "anonymous download -> 401" ($r.code -eq 401) ("HTTP " + $r.code)
$lateTitle = "SMOKE-LATE-" + (Get-Random -Maximum 99999)
$lateBody = @{
  courseId = 1
  title = $lateTitle
  instructions = "Late submissions are disabled."
  dueAt = "2020-01-01T23:59:00"
  allowedFileTypes = "txt"
  maxFileSizeMb = 1
  maxScore = 10
  allowLate = $false
  allowResubmission = $false
  published = $true
} | ConvertTo-Json -Compress
$r = Call "POST" "/assignments" $faculty $lateBody
$lateAssignment = (Json $r).data
Check "create closed assignment" ($r.code -eq 201) ("id=" + $lateAssignment.id)
$r = UploadFile ("/assignments/" + $lateAssignment.id + "/submit") $student $validFile "text/plain"
Check "late upload rejected -> 400" ($r.code -eq 400) ((Json $r).message)
$r = Call "DELETE" ("/assignments/" + $lateAssignment.id) $faculty $null
Check "delete closed assignment" ($r.code -eq 200) ((Json $r).message)
$r = Call "POST" "/assignments" $student $assignmentBody
Check "student cannot create assignment -> 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Call "DELETE" ("/assignments/" + $assignmentId) $faculty $null
Check "delete assignment + stored files" ($r.code -eq 200) ((Json $r).message)
$r = DownloadFile ("/assignments/submissions/" + $submissionId + "/file") $student
Check "deleted submission file -> 404" ($r.code -eq 404) ("HTTP " + $r.code)
Remove-Item $tmpDir -Recurse -Force -ErrorAction SilentlyContinue

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
