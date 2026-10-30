/* ============================================================================
 * smart-campus :: eligibility.js
 * ----------------------------------------------------------------------------
 * Student: own calculated eligibility with every rule explained.
 * Faculty/Admin: class eligibility list, reasons and links to profiles.
 * ========================================================================== */

(function () {
  'use strict';

  const state = {
    courses: [],
    students: [],
    settings: null
  };

  const $ = (id) => document.getElementById(id);

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('eligibility.html', 'Eligibility');

    if (!Auth.can('eligibility.read')) {
      showAlert('You do not have permission to view eligibility.', 'warning');
      return;
    }

    $('refreshEligibility').addEventListener('click', refresh);
    if (Academic.isAdmin()) $('configureRulesBtn').classList.remove('d-none');

    if (Academic.isStudent()) {
      $('pageTitle').textContent = 'My Eligibility';
      $('pageIntro').textContent = 'See whether you meet the configured attendance and marks requirements, and why.';
      $('studentEligibilityView').classList.remove('d-none');
      await loadStudent();
    } else if (Academic.isStaff()) {
      $('pageTitle').textContent = 'Student Eligibility';
      $('pageIntro').textContent = 'Calculated status for students you are authorised to support, with reasons.';
      $('staffEligibilityView').classList.remove('d-none');
      $('eligibilityCourseFilter').addEventListener('change', loadStudents);
      await loadStaff();
    } else {
      showAlert('This page is not available for your role.', 'warning');
    }
  }

  async function refresh() {
    if (Academic.isStudent()) await loadStudent();
    else await loadStaff();
  }

  /* ============================================================ STUDENT */

  async function loadStudent() {
    $('studentEligibilityMount').innerHTML = loadingBlock('Calculating your eligibility...');
    try {
      const [result, settings] = await Promise.all([
        api.get('/academics/eligibility/me'),
        api.get('/academics/settings').catch(() => null)
      ]);
      renderStudent(result, settings);
    } catch (error) {
      $('studentEligibilityMount').innerHTML = errorBlock(errorMessage(error, 'Your eligibility could not be calculated.'));
      const retry = $('studentEligibilityMount').querySelector('[data-action="retry"]');
      if (retry) retry.addEventListener('click', loadStudent);
    }
  }

  function renderStudent(result, settings) {
    const attendance = result.attendancePercentage;
    const marks = result.marksPercentage;
    $('studentEligibilityMount').innerHTML = `
      <div class="card academic-hero mb-4">
        <div class="card-body">
          <div class="d-flex flex-wrap justify-content-between align-items-start gap-3">
            <div>
              <div class="eyebrow"><i class="bi bi-patch-check-fill"></i>Eligibility status</div>
              <h2>${Academic.esc(result.summary || 'Eligibility result')}</h2>
              <p>This status is calculated live from your published marks and recorded attendance. It is not stored permanently.</p>
            </div>
            <div class="text-end">${Academic.eligibilityBadge(result.status)}</div>
          </div>
          <div class="hero-meta">
            <span><i class="bi bi-calendar2-check"></i>Attendance ${Academic.pct(attendance)}</span>
            <span><i class="bi bi-clipboard-data"></i>Marks ${Academic.pct(marks)}</span>
            <span><i class="bi bi-journal-check"></i>${Academic.num(result.assessmentsCounted, 0)} assessment(s) counted</span>
          </div>
        </div>
      </div>

      <div class="academic-stat-grid mb-4">
        ${Academic.stat('Attendance', Academic.pct(attendance), `${Academic.num(result.attendanceRecords, 0)} record(s) · minimum ${Academic.pct(result.minAttendancePercentage)}`)}
        ${Academic.stat('Marks', Academic.pct(marks), `${Academic.num(result.assessmentsCounted, 0)} assessment(s) · minimum ${Academic.pct(result.minMarksPercentage)}`)}
        ${Academic.stat('Required assessments', Academic.num(result.minAssessmentsRequired, 0), 'For a complete evaluation')}
        ${Academic.stat('Required attendance rows', Academic.num(result.minAttendanceRecords, 0), 'For a complete evaluation')}
      </div>

      <div class="row g-3">
        <div class="col-lg-7">
          <div class="card academic-card">
            <div class="card-header d-flex justify-content-between align-items-center">
              <h6 class="mb-0">Why this result?</h6>
              ${Academic.eligibilityBadge(result.status)}
            </div>
            <div class="card-body">
              ${Academic.eligibilityChecks(result)}
            </div>
          </div>
        </div>
        <div class="col-lg-5">
          ${renderRulesCard(settings, result)}
          <div class="card academic-card mt-3">
            <div class="card-body">
              <div class="small text-uppercase text-secondary fw-bold mb-2">Where to improve</div>
              <div class="d-grid gap-2">
                <a href="marks.html" class="btn btn-outline-primary btn-sm text-start">
                  <i class="bi bi-clipboard-data me-1"></i>Review my marks
                </a>
                <a href="attendance.html" class="btn btn-outline-primary btn-sm text-start">
                  <i class="bi bi-calendar2-check me-1"></i>Review my attendance
                </a>
                <a href="profile.html" class="btn btn-outline-secondary btn-sm text-start">
                  <i class="bi bi-person-vcard me-1"></i>Open my academic profile
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>`;
  }

  function renderRulesCard(settings, result) {
    const configured = settings ? settings.rulesConfigured : result.rulesConfigured;
    if (!configured) {
      return `<div class="card academic-card">
        <div class="card-body">
          <div class="small text-uppercase text-secondary fw-bold mb-2">Configured rules</div>
          <div class="empty-inline"><i class="bi bi-exclamation-triangle me-1"></i>An administrator has not configured eligibility rules yet, so this result is pending.</div>
        </div>
      </div>`;
    }
    const source = settings || result;
    return `
      <div class="card academic-card">
        <div class="card-header"><h6 class="mb-0">Configured rules</h6></div>
        <div class="card-body">
          <div class="mini-stat"><span>Minimum attendance</span><strong>${Academic.pct(source.minAttendancePercentage)}</strong></div>
          <div class="mini-stat"><span>Minimum marks</span><strong>${Academic.pct(source.minMarksPercentage)}</strong></div>
          <div class="mini-stat"><span>Minimum assessments</span><strong>${Academic.num(source.minAssessmentsRequired, 0)}</strong></div>
          <div class="mini-stat"><span>Minimum attendance records</span><strong>${Academic.num(source.minAttendanceRecords, 0)}</strong></div>
          <div class="mini-stat"><span>Late arrivals</span><strong>${source.lateCountsAsPresent ? 'Count as present' : 'Do not count as present'}</strong></div>
        </div>
      </div>`;
  }

  /* ============================================================== STAFF */

  async function loadStaff() {
    $('eligibilityListMount').innerHTML = loadingBlock('Loading eligibility results...');
    try {
      state.courses = await Academic.courses(true);
      const selected = $('eligibilityCourseFilter').value;
      Academic.fillCourseSelect($('eligibilityCourseFilter'), state.courses, 'All accessible students', selected);
      const [students, settings] = await Promise.all([
        api.get('/academics/eligibility/students', selected ? { courseId: selected } : null),
        api.get('/academics/settings').catch(() => null)
      ]);
      state.students = Array.isArray(students) ? students : [];
      state.settings = settings;
      renderStaff();
    } catch (error) {
      $('eligibilityListMount').innerHTML = errorBlock(errorMessage(error, 'Eligibility results could not be loaded.'));
      const retry = $('eligibilityListMount').querySelector('[data-action="retry"]');
      if (retry) retry.addEventListener('click', loadStaff);
    }
  }

  async function loadStudents() {
    const courseId = $('eligibilityCourseFilter').value;
    $('eligibilityListMount').innerHTML = loadingBlock('Loading eligibility results...');
    try {
      state.students = await api.get('/academics/eligibility/students', courseId ? { courseId } : null);
      renderStaff();
    } catch (error) {
      $('eligibilityListMount').innerHTML = errorBlock(errorMessage(error, 'Eligibility results could not be loaded.'));
    }
  }

  function renderStaff() {
    const students = state.students || [];
    const eligible = students.filter((entry) => entry.eligibility && entry.eligibility.status === 'ELIGIBLE').length;
    const notEligible = students.filter((entry) => entry.eligibility && entry.eligibility.status === 'NOT_ELIGIBLE').length;
    const pending = students.length - eligible - notEligible;

    $('eligibilityStats').innerHTML = [
      Academic.stat('Students', Academic.num(students.length, 0), 'Matching the filter'),
      Academic.stat('Eligible', Academic.num(eligible, 0), 'All required checks met'),
      Academic.stat('Not eligible', Academic.num(notEligible, 0), 'At least one check unmet'),
      Academic.stat('Pending', Academic.num(pending, 0), 'Insufficient data or no rules')
    ].join('');

    $('eligibilitySummaryText').textContent = students.length
      ? `${students.length} student(s) · ${eligible} eligible · ${notEligible} not eligible · ${pending} pending`
      : 'No students match the selected subject.';

    if (!students.length) {
      $('eligibilityListMount').innerHTML = `<div class="card"><div class="card-body">
        ${Academic.empty('bi-patch-question', 'No eligibility results', 'Students appear here when you teach their subject or are assigned as their class teacher or mentor.')}
      </div></div>`;
      return;
    }

    const rows = students.map((entry) => {
      const student = entry.student || {};
      const result = entry.eligibility || {};
      const unmet = (result.checks || []).filter((check) => check.result === 'UNMET' || check.result === 'PENDING');
      const reason = unmet.length
        ? unmet.map((check) => check.detail).join(' ')
        : (result.summary || 'All configured checks are met.');
      return `
        <tr>
          <td>
            <div class="fw-semibold">${Academic.esc(student.name || 'Student')}</div>
            <div class="small text-muted">${Academic.esc(student.usn || '')} · ${Academic.esc(student.department || '')} · Sem ${Academic.esc(student.semester || '—')}</div>
          </td>
          <td>${Academic.pct(result.attendancePercentage)}<div class="small text-muted">${Academic.num(result.attendanceRecords, 0)} record(s)</div></td>
          <td>${Academic.pct(result.marksPercentage)}<div class="small text-muted">${Academic.num(result.assessmentsCounted, 0)} assessment(s)</div></td>
          <td>${Academic.eligibilityBadge(result.status)}</td>
          <td class="small text-muted" style="max-width:24rem">${Academic.esc(reason)}</td>
          <td class="text-end">
            <a class="btn btn-outline-primary btn-sm" href="profile.html?studentId=${encodeURIComponent(student.id)}">
              <i class="bi bi-person-vcard me-1"></i>Profile
            </a>
          </td>
        </tr>`;
    }).join('');

    $('eligibilityListMount').innerHTML = `
      ${renderRulesCard(state.settings, students[0] ? students[0].eligibility : null)}
      <div class="card mt-3">
        <div class="card-header">
          <h6 class="mb-0">Eligibility list</h6>
          <small class="text-secondary">Status is recalculated each time this page loads.</small>
        </div>
        <div class="table-responsive">
          <table class="table mark-table align-middle mb-0">
            <thead><tr><th>Student</th><th>Attendance</th><th>Marks</th><th>Status</th><th>Reason</th><th class="text-end">Profile</th></tr></thead>
            <tbody>${rows}</tbody>
          </table>
        </div>
      </div>`;
  }

  function showAlert(message, type = 'danger') {
    const alert = $('eligibilityAlert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.classList.remove('d-none');
  }
})();
