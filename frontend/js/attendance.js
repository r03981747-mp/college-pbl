/* ============================================================================
 * smart-campus :: attendance.js
 * ----------------------------------------------------------------------------
 * Student: read-only attendance history grouped by subject.
 * Faculty/Admin: daily register with Present/Absent/Late, corrections and audit.
 * ========================================================================== */

(function () {
  'use strict';

  const state = {
    courses: [],
    sheet: null
  };

  const $ = (id) => document.getElementById(id);

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('attendance.html', 'Attendance');

    if (!Auth.can('attendance.read')) {
      showAlert('You do not have permission to view attendance.', 'warning');
      return;
    }

    $('refreshAttendance').addEventListener('click', () => {
      if (Academic.isStudent()) loadStudent();
      else loadSheet();
    });

    if (Academic.isStudent()) {
      $('pageTitle').textContent = 'My Attendance';
      $('pageIntro').textContent = 'Your subject-wise attendance history, including late arrivals.';
      $('studentView').classList.remove('d-none');
      await loadStudent();
    } else if (Academic.isStaff()) {
      $('pageTitle').textContent = 'Attendance Register';
      $('pageIntro').textContent = 'Mark or correct daily attendance. Existing rows are updated, never duplicated.';
      $('staffView').classList.remove('d-none');
      bindStaff();
      await loadStaffCourses();
    } else {
      showAlert('This page is not available for your role.', 'warning');
    }
  }

  /* ============================================================ STUDENT */

  async function loadStudent() {
    $('studentAttendanceCourses').innerHTML = loadingBlock('Loading your attendance...');
    $('attendanceStats').innerHTML = '';
    $('attendanceHero').innerHTML = '';
    try {
      const data = await api.get('/attendance/my');
      renderStudent(data);
    } catch (error) {
      $('studentAttendanceCourses').innerHTML = errorBlock(errorMessage(error, 'Your attendance could not be loaded.'));
      const retry = $('studentAttendanceCourses').querySelector('[data-action="retry"]');
      if (retry) retry.addEventListener('click', loadStudent);
    }
  }

  function renderStudent(data) {
    const student = data.student || {};
    const overall = data.overall || {};
    $('attendanceHero').innerHTML = `
      <div class="card academic-hero">
        <div class="card-body">
          <div class="d-flex flex-wrap justify-content-between align-items-start gap-3">
            <div>
              <div class="eyebrow"><i class="bi bi-calendar2-check-fill"></i>Attendance record</div>
              <h2>${Academic.esc(student.name || Academic.user().fullName || 'Student')}</h2>
              <p>${Academic.esc(student.usn || '')}${student.department ? ` · ${Academic.esc(student.department)}` : ''}${student.semester ? ` · Semester ${Academic.esc(student.semester)}` : ''}</p>
            </div>
            <div class="text-end">
              <div class="hero-badge"><i class="bi bi-percent"></i>${Academic.pct(overall.percentage)} overall</div>
              <div class="small mt-2 opacity-75">${Academic.num(overall.total, 0)} recorded class day(s)</div>
            </div>
          </div>
        </div>
      </div>`;

    $('attendanceStats').innerHTML = [
      Academic.stat('Overall attendance', Academic.pct(overall.percentage), 'All subjects'),
      Academic.stat('Present', Academic.num(overall.present, 0), 'Classes attended'),
      Academic.stat('Absent', Academic.num(overall.absent, 0), 'Classes missed'),
      Academic.stat('Late', Academic.num(overall.late, 0), 'Marked by faculty'),
      Academic.stat('Total records', Academic.num(overall.total, 0), 'Recorded days')
    ].join('');

    const courses = Array.isArray(data.courses) ? data.courses : [];
    if (!courses.length) {
      $('studentAttendanceCourses').innerHTML = `<div class="card"><div class="card-body">
        ${Academic.empty('bi-calendar-x', 'No subjects found', 'Your department and semester do not have active subjects yet.')}
      </div></div>`;
      return;
    }

    $('studentAttendanceCourses').innerHTML = courses.map((entry) => {
      const course = entry.course || {};
      const summary = entry.summary || {};
      const records = Array.isArray(entry.records) ? entry.records : [];
      const rows = records.map((record) => `
        <tr>
          <td>${formatDate(record.attendanceDate)}</td>
          <td>${statusBadge(record.status)}</td>
          <td class="remarks">${Academic.esc(record.remarks || '—')}</td>
          <td class="audit-col small text-muted">${Academic.esc(record.markedByName || 'Faculty')}<br>${record.markedAt ? formatDateTime(record.markedAt) : '—'}</td>
        </tr>`).join('');

      return `
        <div class="card academic-card mb-3">
          <div class="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
            <div class="course-title">
              <span class="code">${Academic.esc(course.courseCode || '—')}</span>
              <h3>${Academic.esc(course.courseName || 'Subject')}</h3>
              <span class="course-sub">${Academic.esc(Academic.courseSubtitle(course))}</span>
            </div>
            <div class="text-end">
              <div class="fw-bold">${Academic.pct(summary.percentage)}</div>
              <div class="course-sub">${Academic.num(summary.present, 0)} present · ${Academic.num(summary.absent, 0)} absent · ${Academic.num(summary.late, 0)} late</div>
            </div>
          </div>
          <div class="card-body pt-2">
            ${records.length ? `
              <div class="table-responsive">
                <table class="table mark-table align-middle">
                  <thead><tr><th>Date</th><th>Status</th><th>Remarks</th><th class="audit-col">Marked by</th></tr></thead>
                  <tbody>${rows}</tbody>
                </table>
              </div>` : `
              <div class="empty-inline"><i class="bi bi-calendar2-x me-1"></i>No attendance has been recorded for this subject yet.</div>`}
          </div>
        </div>`;
    }).join('');
  }

  /* ============================================================== STAFF */

  function bindStaff() {
    $('attendanceCourse').addEventListener('change', loadSheet);
    $('attendanceDate').addEventListener('change', loadSheet);
    $('saveAttendanceBtn').addEventListener('click', saveAttendance);
    $('markAllPresentBtn').addEventListener('click', markAllPresent);
    $('attendanceSheetMount').addEventListener('change', (event) => {
      if (event.target.matches('input[type="radio"]')) updateStaffStatsFromDom();
    });
  }

  async function loadStaffCourses() {
    const dateInput = $('attendanceDate');
    dateInput.max = todayIso();
    dateInput.value = todayIso();
    try {
      state.courses = await Academic.courses(true);
      Academic.fillCourseSelect($('attendanceCourse'), state.courses, 'Select a subject', '');
      if (state.courses.length) {
        $('attendanceCourse').value = state.courses[0].id;
        await loadSheet();
      } else {
        $('attendanceSheetMount').innerHTML = `<div class="empty-inline m-3">
          <i class="bi bi-journal-x me-1"></i>No subjects are assigned to you yet.</div>`;
      }
    } catch (error) {
      $('attendanceSheetMount').innerHTML = errorBlock(errorMessage(error, 'Your subjects could not be loaded.'));
    }
  }

  async function loadSheet() {
    const courseId = $('attendanceCourse').value;
    const date = $('attendanceDate').value;
    if (!courseId || !date) {
      $('attendanceSheetMount').innerHTML = `<div class="empty-inline m-3">Select a subject and date to load the register.</div>`;
      $('saveAttendanceBtn').disabled = true;
      $('markAllPresentBtn').disabled = true;
      return;
    }

    $('attendanceSheetMount').innerHTML = loadingBlock('Loading the attendance register...');
    $('saveAttendanceBtn').disabled = true;
    $('markAllPresentBtn').disabled = true;
    try {
      const sheet = await api.get('/attendance/sheet', { courseId, date });
      state.sheet = sheet;
      renderSheet(sheet);
    } catch (error) {
      $('attendanceSheetMount').innerHTML = errorBlock(errorMessage(error, 'The attendance register could not be loaded.'));
    }
  }

  function renderSheet(sheet) {
    const course = sheet.course || {};
    const rows = Array.isArray(sheet.rows) ? sheet.rows : [];
    $('attendanceSheetTitle').textContent = `${course.courseCode || 'Subject'} attendance register`;
    $('attendanceSheetSubtitle').textContent = `${course.courseName || ''} · ${formatDate(sheet.date)} · ${rows.length} student(s)`;
    $('attendanceFilterSummary').textContent = rows.length
      ? `${rows.length} student(s) · ${formatDate(sheet.date)}`
      : `No students enrolled · ${formatDate(sheet.date)}`;

    if (!rows.length) {
      $('attendanceSheetMount').innerHTML = Academic.empty('bi-people', 'No enrolled students', 'No students match this subject’s department and semester.');
      $('staffAttendanceStats').innerHTML = '';
      $('saveAttendanceBtn').disabled = true;
      $('markAllPresentBtn').disabled = true;
      return;
    }

    $('attendanceSheetMount').innerHTML = `
      <div class="table-responsive">
        <table class="table attendance-grid align-middle mb-0">
          <thead>
            <tr><th>Student</th><th>Status</th><th>Remarks</th><th class="audit-col">Audit</th></tr>
          </thead>
          <tbody>
            ${rows.map((row) => {
              const student = row.student || {};
              const current = row.status || 'PRESENT';
              const id = student.id;
              return `
                <tr data-student-id="${id}">
                  <td>
                    <div class="student-name">${Academic.esc(student.name || 'Student')}</div>
                    <div class="student-usn">${Academic.esc(student.usn || '')}${student.section ? ` · Section ${Academic.esc(student.section)}` : ''}</div>
                  </td>
                  <td>
                    <div class="attendance-choice" role="radiogroup" aria-label="Attendance for ${Academic.esc(student.name || 'student')}">
                      <input type="radio" name="status-${id}" id="present-${id}" value="PRESENT" ${current === 'PRESENT' ? 'checked' : ''}>
                      <label class="present" for="present-${id}">Present</label>
                      <input type="radio" name="status-${id}" id="absent-${id}" value="ABSENT" ${current === 'ABSENT' ? 'checked' : ''}>
                      <label class="absent" for="absent-${id}">Absent</label>
                      <input type="radio" name="status-${id}" id="late-${id}" value="LATE" ${current === 'LATE' ? 'checked' : ''}>
                      <label class="late" for="late-${id}">Late</label>
                    </div>
                  </td>
                  <td>
                    <label class="visually-hidden" for="att-remarks-${id}">Remarks for ${Academic.esc(student.name || 'student')}</label>
                    <input type="text" class="form-control form-control-sm" id="att-remarks-${id}"
                           data-remarks-for="${id}" maxlength="255" value="${Academic.esc(row.remarks || '')}"
                           placeholder="Optional note">
                  </td>
                  <td class="audit-col small text-muted">
                    ${row.recordId
                      ? `${Academic.esc(row.markedByName || 'Faculty')}<br>${row.markedAt ? formatDateTime(row.markedAt) : '—'}${row.updatedByName ? `<br><span class="text-warning-emphasis">Corrected by ${Academic.esc(row.updatedByName)}</span>` : ''}`
                      : '<span class="badge rounded-pill border bg-info-subtle text-info border-info-emphasis">New</span>'}
                  </td>
                </tr>`;
            }).join('')}
          </tbody>
        </table>
      </div>`;

    $('saveAttendanceBtn').disabled = false;
    $('markAllPresentBtn').disabled = false;
    updateStaffStatsFromDom();
  }

  function updateStaffStatsFromDom() {
    const rows = [...$('attendanceSheetMount').querySelectorAll('tr[data-student-id]')];
    let present = 0;
    let absent = 0;
    let late = 0;
    let unmarked = 0;
    rows.forEach((row) => {
      const checked = row.querySelector('input[type="radio"]:checked');
      if (!checked) unmarked++;
      else if (checked.value === 'PRESENT') present++;
      else if (checked.value === 'ABSENT') absent++;
      else if (checked.value === 'LATE') late++;
    });
    $('staffAttendanceStats').innerHTML = [
      Academic.stat('Students', Academic.num(rows.length, 0), 'In this register'),
      Academic.stat('Present', Academic.num(present, 0), 'Selected now'),
      Academic.stat('Absent', Academic.num(absent, 0), 'Selected now'),
      Academic.stat('Late', Academic.num(late, 0), 'Selected now'),
      Academic.stat('Unmarked', Academic.num(unmarked, 0), 'Choose a status')
    ].join('');
  }

  function markAllPresent() {
    $('attendanceSheetMount').querySelectorAll('tr[data-student-id]').forEach((row) => {
      const present = row.querySelector('input[value="PRESENT"]');
      if (present) present.checked = true;
    });
    updateStaffStatsFromDom();
    toast('All students marked present. Save to apply.', 'info');
  }

  async function saveAttendance() {
    if (!state.sheet) return;
    const rows = [...$('attendanceSheetMount').querySelectorAll('tr[data-student-id]')];
    const records = rows.map((row) => {
      const studentId = Number(row.dataset.studentId);
      const checked = row.querySelector('input[type="radio"]:checked');
      const remarks = row.querySelector(`input[data-remarks-for="${studentId}"]`);
      return {
        studentId,
        status: checked ? checked.value : null,
        remarks: remarks && remarks.value.trim() ? remarks.value.trim() : null
      };
    });

    if (records.some((record) => !record.status)) {
      showAlert('Every student needs a Present, Absent or Late status.', 'warning');
      return;
    }

    const button = $('saveAttendanceBtn');
    try {
      await withBusy(button, () => api.put('/attendance/sheet', {
        courseId: Number($('attendanceCourse').value),
        date: $('attendanceDate').value,
        records
      }));
      toast('Attendance saved.', 'success');
      await loadSheet();
      showAlert('Attendance saved successfully. Existing records were corrected, not duplicated.', 'success');
    } catch (error) {
      showAlert(errorMessage(error, 'The attendance register could not be saved.'));
    }
  }

  function todayIso() {
    const now = new Date();
    const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 10);
  }

  function showAlert(message, type = 'danger') {
    const alert = $('attendanceAlert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.classList.remove('d-none');
  }
})();
