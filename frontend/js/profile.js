/* ============================================================================
 * smart-campus :: profile.js
 * ----------------------------------------------------------------------------
 * Student: own read-only academic profile.
 * Faculty/Admin: list only accessible students and open an academic overview.
 * Admin: assign a class teacher and mentor from the profile.
 * ========================================================================== */

(function () {
  'use strict';

  const state = {
    courses: [],
    students: [],
    facultyOptions: [],
    profile: null,
    currentStudentId: null
  };

  const $ = (id) => document.getElementById(id);

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('profile.html', 'Academic Profile');

    if (!Auth.can('profile.read')) {
      showAlert('You do not have permission to view academic profiles.', 'warning');
      return;
    }

    $('refreshProfile').addEventListener('click', refresh);
    if (Academic.isStudent()) {
      $('pageTitle').textContent = 'My Academic Profile';
      $('pageIntro').textContent = 'Your subject results, attendance, assignments and eligibility at a glance.';
      $('studentProfileView').classList.remove('d-none');
      await loadStudentProfile();
    } else if (Academic.isStaff()) {
      $('pageTitle').textContent = 'Student Academic Profiles';
      $('pageIntro').textContent = 'Academic overview for students you are authorised to support.';
      $('staffProfileView').classList.remove('d-none');
      bindStaff();
      await loadStaff();
      const requestedStudent = Number(new URLSearchParams(location.search).get('studentId'));
      if (Number.isFinite(requestedStudent) && requestedStudent > 0) {
        await viewProfile(requestedStudent);
      }
    } else {
      showAlert('This page is not available for your role.', 'warning');
    }
  }

  function bindStaff() {
    $('profileCourseFilter').addEventListener('change', loadStudents);
    $('studentListMount').addEventListener('click', (event) => {
      const button = event.target.closest('[data-view-student]');
      if (button) viewProfile(Number(button.dataset.viewStudent));
    });
  }

  async function refresh() {
    if (Academic.isStudent()) {
      await loadStudentProfile();
      return;
    }
    const current = state.currentStudentId;
    await loadStaff();
    if (current) await viewProfile(current);
  }

  /* ============================================================ STUDENT */

  async function loadStudentProfile() {
    $('studentProfileMount').innerHTML = loadingBlock('Loading your academic profile...');
    try {
      const profile = await api.get('/academics/profile/me');
      state.profile = profile;
      renderProfile(profile, false);
    } catch (error) {
      $('studentProfileMount').innerHTML = errorBlock(errorMessage(error, 'Your academic profile could not be loaded.'));
      const retry = $('studentProfileMount').querySelector('[data-action="retry"]');
      if (retry) retry.addEventListener('click', loadStudentProfile);
    }
  }

  /* ============================================================== STAFF */

  async function loadStaff() {
    $('studentListMount').innerHTML = loadingBlock('Loading accessible students...');
    try {
      state.courses = await Academic.courses(true);
      const selected = $('profileCourseFilter').value;
      Academic.fillCourseSelect($('profileCourseFilter'), state.courses, 'All accessible students', selected);
      if (Academic.isAdmin() && !state.facultyOptions.length) {
        state.facultyOptions = await api.get('/academics/faculty-options').catch(() => []);
      }
      await loadStudents();
    } catch (error) {
      $('studentListMount').innerHTML = errorBlock(errorMessage(error, 'Students could not be loaded.'));
      const retry = $('studentListMount').querySelector('[data-action="retry"]');
      if (retry) retry.addEventListener('click', loadStaff);
    }
  }

  async function loadStudents() {
    const courseId = $('profileCourseFilter').value;
    $('studentListMount').innerHTML = loadingBlock('Loading students...');
    try {
      state.students = await api.get('/academics/students', courseId ? { courseId } : null);
      renderStudentList();
    } catch (error) {
      $('studentListMount').innerHTML = errorBlock(errorMessage(error, 'Students could not be loaded.'));
      const retry = $('studentListMount').querySelector('[data-action="retry"]');
      if (retry) retry.addEventListener('click', loadStudents);
    }
  }

  function renderStudentList() {
    const students = Array.isArray(state.students) ? state.students : [];
    $('profileListSummary').textContent = students.length
      ? `Showing ${students.length} accessible student(s).`
      : 'No students match the selected subject.';

    if (!students.length) {
      $('studentListMount').innerHTML = `<div class="card"><div class="card-body">
        ${Academic.empty('bi-people', 'No accessible students', 'Students appear here when you teach their subject or are assigned as their class teacher or mentor.')}
      </div></div>`;
      return;
    }

    $('studentListMount').innerHTML = `
      <div class="card">
        <div class="card-header">
          <h6 class="mb-0">Students</h6>
          <small class="text-secondary">Only students you are authorised to view are listed.</small>
        </div>
        <div class="table-responsive">
          <table class="table mark-table align-middle mb-0">
            <thead><tr><th>Student</th><th>Department</th><th>Class teacher</th><th>Mentor</th><th>Eligibility</th><th class="text-end">Profile</th></tr></thead>
            <tbody>
              ${students.map((entry) => {
                const student = entry.student || {};
                return `
                  <tr>
                    <td>
                      <div class="fw-semibold">${Academic.esc(student.name || 'Student')}</div>
                      <div class="small text-muted">${Academic.esc(student.usn || '')} · Section ${Academic.esc(student.section || '—')}</div>
                    </td>
                    <td>
                      <div class="small">${Academic.esc(student.department || '—')}</div>
                      <div class="small text-muted">Semester ${Academic.esc(student.semester || '—')}</div>
                    </td>
                    <td class="small">${Academic.esc(student.classTeacherName || 'Not assigned')}</td>
                    <td class="small">${Academic.esc(student.mentorName || 'Not assigned')}</td>
                    <td>${Academic.eligibilityBadge(entry.eligibility && entry.eligibility.status)}</td>
                    <td class="text-end">
                      <button type="button" class="btn btn-outline-primary btn-sm" data-view-student="${student.id}">
                        <i class="bi bi-person-vcard me-1"></i>${Academic.isAdmin() ? 'View / assign' : 'View profile'}
                      </button>
                    </td>
                  </tr>`;
              }).join('')}
            </tbody>
          </table>
        </div>
      </div>`;
  }

  async function viewProfile(studentId) {
    state.currentStudentId = studentId;
    $('staffProfileDetail').innerHTML = loadingBlock('Loading student profile...');
    try {
      const profile = await api.get(`/academics/students/${studentId}/profile`);
      state.profile = profile;
      renderProfile(profile, true);
      $('staffProfileDetail').scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (error) {
      $('staffProfileDetail').innerHTML = errorBlock(errorMessage(error, 'The student profile could not be loaded.'));
    }
  }

  /* =========================================================== PROFILE */

  function renderProfile(profile, staffView) {
    const student = profile.student || {};
    const courses = Array.isArray(profile.courses) ? profile.courses : [];
    const eligibility = profile.eligibility || {};
    const allMarks = courses.flatMap((course) => Array.isArray(course.marks) ? course.marks : [])
      .filter((mark) => mark.score !== null && mark.score !== undefined);
    const totalEarned = allMarks.reduce((sum, mark) => sum + Number(mark.score || 0), 0);
    const totalMax = allMarks.reduce((sum, mark) => sum + Number(mark.maxScore || 0), 0);
    const marksPercentage = totalMax > 0 ? totalEarned * 100 / totalMax : null;

    const attendanceSummaries = courses.map((course) => course.attendanceSummary).filter(Boolean);
    const attendanceTotal = attendanceSummaries.reduce((sum, item) => sum + Number(item.total || 0), 0);
    const attendanceWeighted = attendanceSummaries.reduce((sum, item) =>
      sum + (item.percentage === null || item.percentage === undefined ? 0 : Number(item.percentage) * Number(item.total || 0)), 0);
    const attendancePercentage = attendanceTotal > 0 ? attendanceWeighted / attendanceTotal : null;
    const assignmentCount = courses.reduce((sum, course) =>
      sum + (Array.isArray(course.assignments) ? course.assignments.length : 0), 0);

    const initials = String(student.name || 'Student').split(/\s+/).map((part) => part[0])
      .join('').slice(0, 2).toUpperCase();
    const teacher = profile.classTeacher;
    const mentor = profile.mentor;

    const html = `
      <div class="card academic-hero mb-4">
        <div class="card-body">
          <div class="d-flex flex-wrap justify-content-between align-items-start gap-3">
            <div class="d-flex align-items-center gap-3">
              <div class="profile-hero-avatar">${Academic.esc(initials)}</div>
              <div>
                <div class="eyebrow"><i class="bi bi-person-vcard-fill"></i>${staffView ? 'Student academic profile' : 'Your academic profile'}</div>
                <h2>${Academic.esc(student.name || 'Student')}</h2>
                <p>${Academic.esc(student.usn || '')}${student.department ? ` · ${Academic.esc(student.department)}` : ''}${student.semester ? ` · Semester ${Academic.esc(student.semester)}` : ''}${student.section ? ` · Section ${Academic.esc(student.section)}` : ''}</p>
              </div>
            </div>
            <div class="text-end">
              ${Academic.eligibilityBadge(eligibility.status)}
              <div class="mt-2">${statusBadge(student.status || 'ACTIVE')}</div>
            </div>
          </div>
          <div class="hero-meta">
            <span><i class="bi bi-person-badge"></i>Class teacher: ${Academic.esc(teacher ? teacher.name : 'Not assigned')}</span>
            <span><i class="bi bi-person-heart"></i>Mentor: ${Academic.esc(mentor ? mentor.name : 'Not assigned')}</span>
            <span><i class="bi bi-calendar3"></i>Admitted ${Academic.esc(student.admissionYear || '—')}</span>
            ${staffView ? '<span><i class="bi bi-eye"></i>Staff view includes draft assessments</span>' : ''}
          </div>
        </div>
      </div>

      <div class="academic-stat-grid mb-4">
        ${Academic.stat('Marks', Academic.pct(marksPercentage), `${Academic.num(totalEarned, 2)} / ${Academic.num(totalMax, 2)}`)}
        ${Academic.stat('Attendance', Academic.pct(attendancePercentage), `${Academic.num(attendanceTotal, 0)} recorded day(s)`)}
        ${Academic.stat('Assessments', Academic.num(allMarks.length, 0), staffView ? 'All marks, including drafts' : 'Published marks with a score')}
        ${Academic.stat('Assignments', Academic.num(assignmentCount, 0), 'Across current subjects')}
      </div>

      <div class="row g-3 mb-4">
        <div class="col-lg-6">
          <div class="card academic-card">
            <div class="card-header d-flex justify-content-between align-items-center">
              <h6 class="mb-0">Eligibility</h6>
              ${Academic.eligibilityBadge(eligibility.status)}
            </div>
            <div class="card-body">
              <p class="small text-secondary mb-2">${Academic.esc(eligibility.summary || 'Eligibility could not be calculated.')}</p>
              ${Academic.eligibilityChecks(eligibility)}
            </div>
          </div>
        </div>
        <div class="col-lg-3 col-md-6">
          ${personCard('Class teacher', teacher, 'bi-person-badge-fill')}
        </div>
        <div class="col-lg-3 col-md-6">
          ${personCard('Mentor', mentor, 'bi-person-heart')}
        </div>
      </div>

      ${staffView && Academic.isAdmin() ? renderMentorAssignment(student) : ''}

      <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
          <h2 class="h5 mb-1">Subject overview</h2>
          <p class="small text-secondary mb-0">Current semester subjects, results, attendance and coursework.</p>
        </div>
      </div>
      ${courses.length ? `<div class="row g-3">${courses.map((course) => renderCourseCard(course, staffView)).join('')}</div>` : `<div class="card"><div class="card-body">
        ${Academic.empty('bi-journal-x', 'No subjects found', 'This student’s department and semester do not have active subjects.')}
      </div></div>`}`;

    if (staffView) $('staffProfileDetail').innerHTML = html;
    else $('studentProfileMount').innerHTML = html;

    if (staffView && Academic.isAdmin()) bindMentorAssignment();
  }

  function personCard(label, person, icon) {
    return `
      <div class="card academic-card">
        <div class="card-body">
          <div class="small text-uppercase text-secondary fw-bold mb-2"><i class="bi ${icon} me-1"></i>${Academic.esc(label)}</div>
          ${person ? `
            <div class="fw-semibold">${Academic.esc(person.name)}</div>
            <div class="small text-muted">${Academic.esc(person.designation || 'Faculty')}${person.employeeId ? ` · ${Academic.esc(person.employeeId)}` : ''}</div>
            <div class="small text-muted mt-1"><i class="bi bi-envelope me-1"></i>${Academic.esc(person.email || '—')}</div>` : `
            <div class="text-muted small">Not assigned yet.</div>`}
        </div>
      </div>`;
  }

  function renderCourseCard(courseView, staffView) {
    const course = courseView.course || {};
    const marks = (Array.isArray(courseView.marks) ? courseView.marks : [])
      .filter((mark) => mark.score !== null && mark.score !== undefined);
    const earned = marks.reduce((sum, mark) => sum + Number(mark.score || 0), 0);
    const max = marks.reduce((sum, mark) => sum + Number(mark.maxScore || 0), 0);
    const percentage = max > 0 ? earned * 100 / max : null;
    const attendance = courseView.attendanceSummary || {};
    const assignments = Array.isArray(courseView.assignments) ? courseView.assignments : [];

    const assignmentRows = assignments.slice(0, 5).map((assignment) => {
      let stateHtml;
      if (staffView) {
        stateHtml = `<span class="small text-muted">${Academic.num(assignment.submissionCount, 0)} submitted · ${Academic.num(assignment.gradedCount, 0)} graded</span>`;
      } else if (assignment.mySubmission) {
        stateHtml = `${statusBadge(assignment.mySubmission.status || 'SUBMITTED')}${assignment.mySubmission.grade !== null && assignment.mySubmission.grade !== undefined ? `<span class="small text-muted ms-1">${Academic.num(assignment.mySubmission.grade, 2)} / ${Academic.num(assignment.maxScore, 2)}</span>` : ''}`;
      } else {
        stateHtml = '<span class="small text-muted">Not submitted</span>';
      }
      return `<div class="d-flex justify-content-between align-items-center gap-2 py-1 border-bottom">
        <div class="small text-truncate" title="${Academic.esc(assignment.title)}">${Academic.esc(assignment.title)}</div>
        <div class="flex-shrink-0">${stateHtml}</div>
      </div>`;
    }).join('');

    return `
      <div class="col-lg-6">
        <div class="profile-course">
          <div class="course-head">
            <div>
              <h3>${Academic.esc(course.courseName || 'Subject')}</h3>
              <div class="course-sub">${Academic.esc(course.courseCode || '')} · ${Academic.esc(course.facultyName || 'Faculty not assigned')}</div>
            </div>
            <span class="badge rounded-pill border bg-primary-subtle text-primary border-primary-emphasis">${Academic.esc(course.credits || 0)} credits</span>
          </div>
          <div class="mt-3">
            <div class="mini-stat"><span>Marks</span><strong>${Academic.pct(percentage)} <span class="fw-normal text-muted">(${Academic.num(earned, 2)} / ${Academic.num(max, 2)})</span></strong></div>
            <div class="mini-stat"><span>Attendance</span><strong>${Academic.pct(attendance.percentage)} <span class="fw-normal text-muted">(${Academic.num(attendance.present, 0)} present, ${Academic.num(attendance.absent, 0)} absent, ${Academic.num(attendance.late, 0)} late)</span></strong></div>
            <div class="mini-stat"><span>Assignments</span><strong>${Academic.num(assignments.length, 0)}</strong></div>
          </div>
          ${assignments.length ? `<div class="mt-3"><div class="small text-uppercase text-secondary fw-bold mb-1">Coursework</div>${assignmentRows}</div>` : '<div class="empty-inline mt-3">No published assignments for this subject.</div>'}
        </div>
      </div>`;
  }

  function renderMentorAssignment(student) {
    const options = state.facultyOptions || [];
    return `
      <div class="card mb-4" id="mentorAssignmentCard">
        <div class="card-header">
          <h6 class="mb-0"><i class="bi bi-diagram-3 me-1"></i>Class teacher & mentor assignment</h6>
          <small class="text-secondary">Administrators can assign or clear both roles for ${Academic.esc(student.name || 'this student')}.</small>
        </div>
        <div class="card-body">
          <form id="mentorAssignmentForm" data-student-id="${student.id}" class="row g-3 align-items-end">
            <div class="col-md-5">
              <label class="form-label" for="classTeacherSelect">Class teacher</label>
              <select class="form-select" id="classTeacherSelect" name="classTeacherId">
                <option value="">Not assigned</option>
                ${options.map((faculty) => `<option value="${faculty.id}" ${String(faculty.id) === String(student.classTeacherId) ? 'selected' : ''}>${Academic.esc(faculty.name)} — ${Academic.esc(faculty.department || '')}</option>`).join('')}
              </select>
            </div>
            <div class="col-md-5">
              <label class="form-label" for="mentorSelect">Mentor</label>
              <select class="form-select" id="mentorSelect" name="mentorId">
                <option value="">Not assigned</option>
                ${options.map((faculty) => `<option value="${faculty.id}" ${String(faculty.id) === String(student.mentorId) ? 'selected' : ''}>${Academic.esc(faculty.name)} — ${Academic.esc(faculty.department || '')}</option>`).join('')}
              </select>
            </div>
            <div class="col-md-2">
              <button type="submit" class="btn btn-primary w-100">
                <i class="bi bi-check2 me-1"></i>Save
              </button>
            </div>
            ${options.length ? '' : '<div class="col-12"><div class="empty-inline">No active faculty members are available to assign.</div></div>'}
          </form>
        </div>
      </div>`;
  }

  function bindMentorAssignment() {
    const form = $('mentorAssignmentForm');
    if (!form) return;
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const studentId = form.dataset.studentId;
      const classTeacherId = $('classTeacherSelect').value ? Number($('classTeacherSelect').value) : null;
      const mentorId = $('mentorSelect').value ? Number($('mentorSelect').value) : null;
      const button = form.querySelector('button[type="submit"]');
      try {
        await withBusy(button, () => api.put(`/academics/students/${studentId}/mentors`, {
          classTeacherId,
          mentorId
        }));
        toast('Class teacher and mentor updated.', 'success');
        await loadStudents();
        await viewProfile(Number(studentId));
      } catch (error) {
        showAlert(errorMessage(error, 'The class teacher and mentor could not be updated.'));
      }
    });
  }

  function showAlert(message, type = 'danger') {
    const alert = $('profileAlert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.classList.remove('d-none');
  }
})();
