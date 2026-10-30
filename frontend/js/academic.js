/* ============================================================================
 * smart-campus :: academic.js
 * ----------------------------------------------------------------------------
 * Small shared helpers for the academic pages.  All real authorization is
 * enforced by the Spring Boot services; these helpers only make the UI
 * consistent (course pickers, badges, percentages, secure downloads).
 * ========================================================================== */

const Academic = (() => {
  'use strict';

  let coursesPromise = null;

  const ROLE_LABELS = { ADMIN: 'Administrator', FACULTY: 'Faculty', STUDENT: 'Student' };

  function user() {
    return Auth.currentUser() || {};
  }

  function role() {
    return String(Auth.role() || user().role || '').toUpperCase();
  }

  function isStudent() {
    return role() === 'STUDENT';
  }

  function isFaculty() {
    return role() === 'FACULTY';
  }

  function isAdmin() {
    return role() === 'ADMIN';
  }

  function isStaff() {
    return isAdmin() || isFaculty();
  }

  function roleLabel() {
    return ROLE_LABELS[role()] || role() || 'User';
  }

  function esc(value) {
    return escapeHtml(value);
  }

  function num(value, digits = 1) {
    if (value === null || value === undefined || value === '') return '—';
    const n = Number(value);
    if (!Number.isFinite(n)) return '—';
    return n.toFixed(digits).replace(/\.0+$/, '');
  }

  function pct(value, digits = 1) {
    if (value === null || value === undefined || value === '') return '—';
    return `${num(value, digits)}%`;
  }

  function score(value, max) {
    if (value === null || value === undefined || value === '') return 'Not entered';
    return `${num(value, 2)} / ${num(max, 2)}`;
  }

  function dateTime(value) {
    return value ? formatDateTime(value) : '—';
  }

  function courseLabel(course) {
    if (!course) return '—';
    return `${course.courseCode || ''} · ${course.courseName || ''}`.replace(/^ · /, '');
  }

  function courseSubtitle(course) {
    if (!course) return '';
    return [course.department, course.semester ? `Semester ${course.semester}` : null,
      course.facultyName].filter(Boolean).join(' · ');
  }

  function percentClass(value) {
    if (value === null || value === undefined || value === '') return '';
    const n = Number(value);
    if (n >= 75) return 'good';
    if (n >= 40) return 'warn';
    return 'bad';
  }

  function scoreBar(value) {
    if (value === null || value === undefined || value === '') {
      return '<div class="score-bar"><span style="width:0%"></span></div>';
    }
    const width = Math.max(0, Math.min(100, Number(value) || 0));
    return `<div class="score-bar ${percentClass(value)}"><span style="width:${width}%"></span></div>`;
  }

  function eligibilityBadge(status, extraClass = '') {
    const key = String(status || '').toUpperCase();
    if (key === 'ELIGIBLE') {
      return `<span class="eligibility-pill eligible ${extraClass}"><i class="bi bi-check-circle-fill"></i>Eligible</span>`;
    }
    if (key === 'NOT_ELIGIBLE' || key === 'NOTELIGIBLE') {
      return `<span class="eligibility-pill not-eligible ${extraClass}"><i class="bi bi-x-circle-fill"></i>Not eligible</span>`;
    }
    if (key === 'PENDING') {
      return `<span class="eligibility-pill pending ${extraClass}"><i class="bi bi-hourglass-split"></i>Pending / insufficient data</span>`;
    }
    return `<span class="eligibility-pill pending ${extraClass}"><i class="bi bi-question-circle-fill"></i>Not evaluated</span>`;
  }

  function checkIcon(result) {
    const key = String(result || '').toUpperCase();
    if (key === 'MET') return '<span class="icon met"><i class="bi bi-check-lg"></i></span>';
    if (key === 'UNMET') return '<span class="icon unmet"><i class="bi bi-x-lg"></i></span>';
    if (key === 'PENDING') return '<span class="icon pending"><i class="bi bi-hourglass"></i></span>';
    return '<span class="icon not-required"><i class="bi bi-dash-lg"></i></span>';
  }

  function eligibilityChecks(result) {
    if (!result) return '';
    if (!result.rulesConfigured) {
      return '<div class="empty-inline"><i class="bi bi-sliders me-1"></i>Eligibility rules have not been configured yet. An administrator can set them in Academic Setup.</div>';
    }
    const checks = Array.isArray(result.checks) ? result.checks : [];
    if (!checks.length) return '<div class="empty-inline">No eligibility checks were returned.</div>';
    return checks.map((check) => `
      <div class="eligibility-check">
        ${checkIcon(check.result)}
        <div>
          <div class="label">${esc(check.label)}</div>
          <div class="detail">${esc(check.detail)}</div>
        </div>
      </div>`).join('');
  }

  async function courses(force = false) {
    if (!coursesPromise || force) {
      coursesPromise = api.get('/academics/courses').then((list) => Array.isArray(list) ? list : []);
    }
    return coursesPromise;
  }

  function fillCourseSelect(select, list, placeholder = 'All subjects', selected = null) {
    if (!select) return;
    const current = selected !== null ? selected : select.value;
    const options = (list || []).map((course) => ({
      value: course.id,
      label: courseLabel(course)
    }));
    fillSelect(select, options, placeholder, current);
  }

  function fillTermSelect(select, list, placeholder = 'All terms', selected = null) {
    if (!select) return;
    const terms = [...new Set((list || []).map((item) => item.term).filter(Boolean))].sort();
    fillSelect(select, terms, placeholder, selected !== null ? selected : select.value);
  }

  function empty(icon, title, detail = '') {
    return stateBlock(icon, title, detail);
  }

  function stat(label, value, hint = '') {
    return `
      <div class="academic-stat">
        <div class="label">${esc(label)}</div>
        <div class="value">${value}</div>
        ${hint ? `<div class="hint">${esc(hint)}</div>` : ''}
      </div>`;
  }

  function fileSize(bytes) {
    const n = Number(bytes);
    if (!Number.isFinite(n) || n <= 0) return '—';
    if (n < 1024) return `${n} B`;
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
    return `${(n / (1024 * 1024)).toFixed(1)} MB`;
  }

  function allowedTypesLabel(value) {
    if (!value) return 'Any file type';
    return String(value).split(',').map((type) => type.trim()).filter(Boolean)
      .map((type) => type.toUpperCase()).join(', ');
  }

  function dueState(assignment) {
    if (!assignment) return { state: 'closed', label: 'Closed', className: 'secondary' };
    const due = assignment.dueAt ? new Date(assignment.dueAt) : null;
    const now = new Date();
    if (!due || Number.isNaN(due.getTime())) {
      return { state: 'open', label: 'Open', className: 'success' };
    }
    if (now <= due) {
      return { state: 'open', label: 'Open', className: 'success' };
    }
    if (assignment.allowLate) {
      return { state: 'late', label: 'Late submission allowed', className: 'warning' };
    }
    return { state: 'closed', label: 'Closed', className: 'danger' };
  }

  function canSubmit(assignment) {
    if (!assignment || !assignment.published) return false;
    const state = dueState(assignment);
    if (state.state === 'closed') return false;
    if (assignment.mySubmission && !assignment.allowResubmission) return false;
    return true;
  }

  function submissionStatus(submission) {
    if (!submission) return '<span class="badge rounded-pill border bg-secondary-subtle text-secondary border-secondary-emphasis">Not submitted</span>';
    return statusBadge(submission.status || 'SUBMITTED');
  }

  /** Authenticated file download; never exposes the stored path in the URL. */
  async function downloadSubmission(submissionId, fileName) {
    const response = await fetch(`${API_BASE}/assignments/submissions/${encodeURIComponent(submissionId)}/file`, {
      headers: {
        Accept: '*/*',
        Authorization: `Bearer ${getToken()}`
      }
    });
    if (response.status === 401) {
      clearSession();
      location.href = 'login.html?expired=1';
      throw new Error('Your session has expired.');
    }
    if (!response.ok) {
      let message = `Download failed (HTTP ${response.status}).`;
      try {
        const payload = await response.json();
        if (payload && payload.message) message = payload.message;
      } catch (ignored) { /* binary or empty error body */ }
      throw new Error(message);
    }
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = fileName || 'submission';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1500);
  }

  function modal(id) {
    const element = document.getElementById(id);
    if (!element) return null;
    return bootstrap.Modal.getOrCreateInstance(element);
  }

  function showModal(id) {
    const instance = modal(id);
    if (instance) instance.show();
  }

  function hideModal(id) {
    const element = document.getElementById(id);
    if (!element) return;
    const instance = bootstrap.Modal.getInstance(element);
    if (instance) instance.hide();
  }

  function formValue(form, name) {
    const field = form.elements.namedItem(name);
    return field ? field.value : '';
  }

  function setFormValue(form, name, value) {
    const field = form.elements.namedItem(name);
    if (field) field.value = value === null || value === undefined ? '' : value;
  }

  function setBusy(button, busy, label = 'Working...') {
    if (!button) return;
    if (busy) {
      button.dataset.originalHtml = button.innerHTML;
      button.disabled = true;
      button.innerHTML = `<span class="spinner-border spinner-border-sm me-1" role="status"></span>${escapeHtml(label)}`;
    } else {
      button.disabled = false;
      if (button.dataset.originalHtml) button.innerHTML = button.dataset.originalHtml;
    }
  }

  function refreshPermissions() {
    return Auth.me().catch(() => null);
  }

  return {
    user, role, roleLabel, isStudent, isFaculty, isAdmin, isStaff,
    esc, num, pct, score, dateTime, courseLabel, courseSubtitle,
    percentClass, scoreBar, eligibilityBadge, eligibilityChecks,
    courses, fillCourseSelect, fillTermSelect, empty, stat,
    fileSize, allowedTypesLabel, dueState, canSubmit, submissionStatus,
    downloadSubmission, modal, showModal, hideModal,
    formValue, setFormValue, setBusy, refreshPermissions
  };
})();
