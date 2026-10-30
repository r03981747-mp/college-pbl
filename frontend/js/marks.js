/* ============================================================================
 * smart-campus :: marks.js
 * ----------------------------------------------------------------------------
 * Student: read-only published marks grouped by subject.
 * Faculty/Admin: assessment catalogue, marks entry, publication workflow.
 * ========================================================================== */

(function () {
  'use strict';

  const state = {
    courses: [],
    assessments: [],
    sheet: null
  };

  const $ = (id) => document.getElementById(id);

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('marks.html', 'Marks');

    if (!Auth.can('marks.read')) {
      showAlert('You do not have permission to view marks.', 'warning');
      return;
    }

    bindCommon();
    if (Academic.isStudent()) {
      $('pageTitle').textContent = 'My Marks';
      $('pageIntro').textContent = 'Your published assessment marks, grouped by subject and term.';
      $('studentView').classList.remove('d-none');
      await loadStudent();
    } else if (Academic.isStaff()) {
      $('pageTitle').textContent = 'Marks Management';
      $('pageIntro').textContent = 'Record assessment marks, correct entries and release results to students.';
      $('staffView').classList.remove('d-none');
      $('addAssessmentBtn').classList.remove('d-none');
      bindStaff();
      await loadStaff();
    } else {
      showAlert('This page is not available for your role.', 'warning');
    }
  }

  function bindCommon() {
    $('refreshMarks').addEventListener('click', () => {
      if (Academic.isStudent()) loadStudent();
      else loadStaff();
    });
  }

  function bindStaff() {
    $('marksCourseFilter').addEventListener('change', renderStaff);
    $('marksTermFilter').addEventListener('change', renderStaff);
    $('addAssessmentBtn').addEventListener('click', () => openAssessmentForm(null));
    $('assessmentForm').addEventListener('submit', saveAssessment);
    $('saveMarksBtn').addEventListener('click', saveMarks);
    $('staffAssessments').addEventListener('click', onAssessmentAction);
  }

  /* ============================================================ STUDENT */

  async function loadStudent() {
    $('studentCourses').innerHTML = loadingBlock('Loading your published marks...');
    $('marksStats').innerHTML = '';
    $('studentHero').innerHTML = '';

    try {
      const [marks, eligibility] = await Promise.all([
        api.get('/marks/my'),
        api.get('/academics/eligibility/me').catch(() => null)
      ]);
      renderStudent(marks, eligibility);
    } catch (error) {
      $('studentCourses').innerHTML = errorBlock(errorMessage(error, 'Your marks could not be loaded.'));
      const retry = $('studentCourses').querySelector('[data-action="retry"]');
      if (retry) retry.addEventListener('click', loadStudent);
    }
  }

  function renderStudent(data, eligibility) {
    const user = Academic.user();
    const overall = Academic.pct(data.overallPercentage);

    $('studentHero').innerHTML = `
      <div class="card academic-hero">
        <div class="card-body">
          <div class="d-flex flex-wrap justify-content-between align-items-start gap-3">
            <div>
              <div class="eyebrow"><i class="bi bi-mortarboard-fill"></i>Academic record</div>
              <h2>${Academic.esc(user.fullName || user.username || 'Student')}</h2>
              <p>Only assessments published by your faculty are shown. Draft marks stay private until released.</p>
            </div>
            <div class="text-end">
              ${eligibility ? Academic.eligibilityBadge(eligibility.status) : ''}
              <div class="small mt-2 opacity-75">Eligibility status</div>
            </div>
          </div>
          <div class="hero-meta">
            <span><i class="bi bi-journal-check"></i>${Academic.num(data.assessmentCount, 0)} published assessment(s)</span>
            <span><i class="bi bi-trophy"></i>${Academic.num(data.totalEarned, 2)} / ${Academic.num(data.totalMax, 2)} marks</span>
            <span><i class="bi bi-percent"></i>${overall} overall</span>
          </div>
        </div>
      </div>`;

    $('marksStats').innerHTML = [
      Academic.stat('Overall result', overall, 'Published marks only'),
      Academic.stat('Assessments', Academic.num(data.assessmentCount, 0), 'Counted with a score'),
      Academic.stat('Marks earned', Academic.num(data.totalEarned, 2), `out of ${Academic.num(data.totalMax, 2)}`),
      Academic.stat('Subjects', Academic.num((data.courses || []).length, 0), 'Current semester')
    ].join('');

    const courses = Array.isArray(data.courses) ? data.courses : [];
    if (!courses.length) {
      $('studentCourses').innerHTML = `<div class="card"><div class="card-body">
        ${Academic.empty('bi-journal-x', 'No subjects found', 'Your department and semester do not have active subjects yet.')}
      </div></div>`;
      return;
    }

    $('studentCourses').innerHTML = courses.map((summary) => renderStudentCourse(summary)).join('');
  }

  function renderStudentCourse(summary) {
    const course = summary.course || {};
    const marks = Array.isArray(summary.marks) ? summary.marks : [];
    const percentage = Academic.pct(summary.percentage);

    const rows = marks.map((mark) => {
      const value = mark.score === null || mark.score === undefined
        ? null
        : (Number(mark.maxScore) > 0 ? Number(mark.score) * 100 / Number(mark.maxScore) : null);
      return `
        <tr>
          <td>
            <div class="fw-semibold">${Academic.esc(mark.title || 'Assessment')}</div>
            <div class="small text-muted">${Academic.esc(labelify(mark.assessmentType || 'OTHER'))}</div>
          </td>
          <td>${Academic.esc(mark.term || '—')}</td>
          <td>${formatDate(mark.assessmentDate)}</td>
          <td class="score">${Academic.score(mark.score, mark.maxScore)}</td>
          <td style="min-width:7rem">
            <div class="d-flex align-items-center gap-2">
              <span class="small fw-semibold">${value === null ? '—' : Academic.pct(value)}</span>
              ${Academic.scoreBar(value)}
            </div>
          </td>
          <td class="remarks">${Academic.esc(mark.remarks || '—')}</td>
        </tr>`;
    }).join('');

    return `
      <div class="card academic-card mb-3">
        <div class="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
          <div class="course-title">
            <span class="code">${Academic.esc(course.courseCode || '—')}</span>
            <h3>${Academic.esc(course.courseName || 'Subject')}</h3>
            <span class="course-sub">${Academic.esc(Academic.courseSubtitle(course))}</span>
          </div>
          <div class="text-end">
            <div class="fw-bold">${percentage}</div>
            <div class="course-sub">${Academic.num(summary.earned, 2)} / ${Academic.num(summary.max, 2)}</div>
          </div>
        </div>
        <div class="card-body pt-2">
          ${marks.length ? `
            <div class="table-responsive">
              <table class="table mark-table align-middle">
                <thead>
                  <tr><th>Assessment</th><th>Term</th><th>Date</th><th>Score</th><th>Result</th><th>Remarks</th></tr>
                </thead>
                <tbody>${rows}</tbody>
              </table>
            </div>` : `
            <div class="empty-inline"><i class="bi bi-hourglass-split me-1"></i>No published assessments for this subject yet.</div>`}
        </div>
      </div>`;
  }

  /* ============================================================== STAFF */

  async function loadStaff() {
    $('staffAssessments').innerHTML = loadingBlock('Loading assessments...');
    $('staffStats').innerHTML = '';
    try {
      state.courses = await Academic.courses(true);
      Academic.fillCourseSelect($('marksCourseFilter'), state.courses, 'All assigned subjects', '');
      state.assessments = await api.get('/marks/assessments');
      Academic.fillTermSelect($('marksTermFilter'), state.assessments, 'All terms', '');
      renderStaff();
    } catch (error) {
      $('staffAssessments').innerHTML = errorBlock(errorMessage(error, 'Assessments could not be loaded.'));
      const retry = $('staffAssessments').querySelector('[data-action="retry"]');
      if (retry) retry.addEventListener('click', loadStaff);
    }
  }

  function visibleAssessments() {
    const courseId = $('marksCourseFilter').value;
    const term = $('marksTermFilter').value;
    return state.assessments.filter((assessment) => {
      if (courseId && String(assessment.courseId) !== String(courseId)) return false;
      if (term && String(assessment.term || '').toLowerCase() !== term.toLowerCase()) return false;
      return true;
    });
  }

  function renderStaff() {
    const visible = visibleAssessments();
    const published = visible.filter((item) => item.published).length;
    const draft = visible.length - published;
    const subjects = new Set(visible.map((item) => item.courseId)).size;

    $('staffStats').innerHTML = [
      Academic.stat('Assessments', Academic.num(visible.length, 0), 'Matching the filters'),
      Academic.stat('Published', Academic.num(published, 0), 'Visible to students'),
      Academic.stat('Drafts', Academic.num(draft, 0), 'Not visible yet'),
      Academic.stat('Subjects', Academic.num(subjects, 0), 'With assessments')
    ].join('');

    $('marksFilterSummary').textContent = visible.length
      ? `Showing ${visible.length} assessment(s).`
      : 'No assessments match the selected filters.';

    if (!state.courses.length) {
      $('staffAssessments').innerHTML = `<div class="card"><div class="card-body">
        ${Academic.empty('bi-journal-x', 'No assigned subjects', 'An administrator must assign subjects to you before you can manage marks.')}
      </div></div>`;
      return;
    }

    if (!visible.length) {
      $('staffAssessments').innerHTML = `<div class="card"><div class="card-body">
        ${Academic.empty('bi-clipboard-x', 'No assessments found', 'Create an assessment for one of your subjects to begin entering marks.')}
      </div></div>`;
      return;
    }

    const rows = visible.map((assessment) => `
      <tr>
        <td>
          <div class="fw-semibold">${Academic.esc(assessment.title)}</div>
          <div class="small text-muted">${Academic.esc(assessment.courseCode || '')} · ${Academic.esc(assessment.courseName || '')}</div>
        </td>
        <td>${Academic.esc(labelify(assessment.assessmentType || 'OTHER'))}</td>
        <td>${Academic.esc(assessment.term || '—')}</td>
        <td>${formatDate(assessment.assessmentDate)}</td>
        <td class="score">${Academic.num(assessment.maxScore, 2)}${assessment.weight !== null && assessment.weight !== undefined ? `<div class="small text-muted">${Academic.num(assessment.weight, 1)}% weight</div>` : ''}</td>
        <td>${statusBadge(assessment.published ? 'PUBLISHED' : 'DRAFT')}</td>
        <td class="text-end" style="min-width:13rem">
          <div class="btn-group btn-group-sm" role="group" aria-label="Assessment actions">
            <button type="button" class="btn btn-outline-primary" data-action="sheet" data-id="${assessment.id}" title="Enter marks" aria-label="Enter marks for ${Academic.esc(assessment.title)}">
              <i class="bi bi-pencil-square"></i>
            </button>
            <button type="button" class="btn btn-outline-${assessment.published ? 'warning' : 'success'}" data-action="publish" data-id="${assessment.id}" data-published="${assessment.published ? 'true' : 'false'}" title="${assessment.published ? 'Move to draft' : 'Publish'}" aria-label="${assessment.published ? 'Unpublish' : 'Publish'} ${Academic.esc(assessment.title)}">
              <i class="bi ${assessment.published ? 'bi-eye-slash' : 'bi-send-check'}"></i>
            </button>
            <button type="button" class="btn btn-outline-secondary" data-action="edit" data-id="${assessment.id}" title="Edit assessment" aria-label="Edit ${Academic.esc(assessment.title)}">
              <i class="bi bi-pencil"></i>
            </button>
            <button type="button" class="btn btn-outline-danger" data-action="delete" data-id="${assessment.id}" title="Delete assessment" aria-label="Delete ${Academic.esc(assessment.title)}">
              <i class="bi bi-trash3"></i>
            </button>
          </div>
        </td>
      </tr>`).join('');

    $('staffAssessments').innerHTML = `
      <div class="card">
        <div class="card-header d-flex justify-content-between align-items-center">
          <div>
            <h6 class="mb-0">Assessment catalogue</h6>
            <small class="text-secondary">Publish an assessment only after its marks are ready for students.</small>
          </div>
        </div>
        <div class="table-responsive">
          <table class="table mark-table align-middle mb-0">
            <thead><tr><th>Assessment</th><th>Type</th><th>Term</th><th>Date</th><th>Max</th><th>Status</th><th class="text-end">Actions</th></tr></thead>
            <tbody>${rows}</tbody>
          </table>
        </div>
      </div>`;
  }

  function onAssessmentAction(event) {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const id = Number(button.dataset.id);
    const assessment = state.assessments.find((item) => Number(item.id) === id);
    if (!assessment) return;

    if (button.dataset.action === 'sheet') openMarksSheet(assessment);
    if (button.dataset.action === 'edit') openAssessmentForm(assessment);
    if (button.dataset.action === 'publish') togglePublished(assessment, button);
    if (button.dataset.action === 'delete') deleteAssessment(assessment, button);
  }

  function openAssessmentForm(assessment) {
    const form = $('assessmentForm');
    form.reset();
    form.classList.remove('was-validated');
    form.querySelectorAll('.is-invalid').forEach((field) => field.classList.remove('is-invalid'));
    $('assessmentAlert').classList.add('d-none');
    $('assessmentModalTitle').textContent = assessment ? 'Edit assessment' : 'New assessment';
    $('assessmentId').value = assessment ? assessment.id : '';
    Academic.fillCourseSelect($('assessmentCourse'), state.courses, null,
      assessment ? assessment.courseId : ($('marksCourseFilter').value || (state.courses[0] && state.courses[0].id) || ''));
    $('assessmentTitle').value = assessment ? assessment.title : '';
    $('assessmentType').value = assessment && assessment.assessmentType ? assessment.assessmentType : 'QUIZ';
    $('assessmentTerm').value = assessment && assessment.term
      ? assessment.term
      : ($('marksTermFilter').value || `${new Date().getFullYear()}-ODD`);
    $('assessmentMaxScore').value = assessment && assessment.maxScore !== null ? assessment.maxScore : '';
    $('assessmentWeight').value = assessment && assessment.weight !== null && assessment.weight !== undefined ? assessment.weight : '';
    $('assessmentDate').value = assessment && assessment.assessmentDate ? assessment.assessmentDate : '';
    $('assessmentPublished').checked = assessment ? Boolean(assessment.published) : false;
    Academic.showModal('assessmentModal');
  }

  async function saveAssessment(event) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.checkValidity()) {
      form.classList.add('was-validated');
      return;
    }

    const id = $('assessmentId').value;
    const payload = {
      courseId: Number($('assessmentCourse').value),
      title: $('assessmentTitle').value.trim(),
      assessmentType: $('assessmentType').value,
      term: $('assessmentTerm').value.trim(),
      maxScore: Number($('assessmentMaxScore').value),
      weight: $('assessmentWeight').value === '' ? null : Number($('assessmentWeight').value),
      assessmentDate: $('assessmentDate').value || null,
      published: $('assessmentPublished').checked
    };

    const button = $('saveAssessmentBtn');
    try {
      await withBusy(button, () => id
        ? api.put(`/marks/assessments/${id}`, payload)
        : api.post('/marks/assessments', payload));
      Academic.hideModal('assessmentModal');
      toast(id ? 'Assessment updated.' : 'Assessment created.', 'success');
      await loadStaff();
    } catch (error) {
      showAssessmentAlert(errorMessage(error, 'The assessment could not be saved.'));
      showFieldErrors(form, error.errors);
    }
  }

  async function togglePublished(assessment, button) {
    const next = !assessment.published;
    try {
      await withBusy(button, () => api.put(`/marks/assessments/${assessment.id}/publish?published=${next}`));
      toast(next ? 'Assessment published to students.' : 'Assessment moved back to draft.', 'success');
      await loadStaff();
    } catch (error) {
      showAlert(errorMessage(error, 'The assessment status could not be changed.'));
    }
  }

  async function deleteAssessment(assessment, button) {
    const confirmed = await confirmDialog({
      title: 'Delete assessment?',
      message: `Delete “${assessment.title}”? All marks entered for it will also be removed.`,
      confirmLabel: 'Delete assessment',
      danger: true
    });
    if (!confirmed) return;
    try {
      await withBusy(button, () => api.del(`/marks/assessments/${assessment.id}`));
      toast('Assessment deleted.', 'success');
      await loadStaff();
    } catch (error) {
      showAlert(errorMessage(error, 'The assessment could not be deleted.'));
    }
  }

  async function openMarksSheet(assessment) {
    $('marksSheetAlert').classList.add('d-none');
    $('marksSheetTitle').textContent = `Marks entry · ${assessment.title}`;
    $('marksSheetSubtitle').textContent = `${assessment.courseCode} · ${assessment.courseName} · Maximum ${Academic.num(assessment.maxScore, 2)}`;
    $('marksSheetMount').innerHTML = loadingBlock('Loading enrolled students...');
    Academic.showModal('marksSheetModal');

    try {
      const sheet = await api.get(`/marks/assessments/${assessment.id}/sheet`);
      state.sheet = sheet;
      renderMarksSheet(sheet);
    } catch (error) {
      $('marksSheetMount').innerHTML = errorBlock(errorMessage(error, 'The marks sheet could not be loaded.'));
    }
  }

  function renderMarksSheet(sheet) {
    const assessment = sheet.assessment || {};
    const rows = Array.isArray(sheet.marks) ? sheet.marks : [];
    const max = Number(assessment.maxScore) || 0;
    $('marksSheetHint').textContent = `Scores must be between 0 and ${Academic.num(max, 2)}. Leave a row blank if the student has not been assessed.`;

    if (!rows.length) {
      $('marksSheetMount').innerHTML = Academic.empty('bi-people', 'No enrolled students', 'No students match this subject’s department and semester.');
      return;
    }

    $('marksSheetMount').innerHTML = `
      <table class="table mark-table align-middle">
        <thead>
          <tr><th>Student</th><th style="width:9rem">Score / ${Academic.num(max, 2)}</th><th>Remarks</th><th>Last entered</th></tr>
        </thead>
        <tbody>
          ${rows.map((row) => `
            <tr>
              <td>
                <div class="fw-semibold">${Academic.esc(row.studentName || 'Student')}</div>
                <div class="small text-muted">${Academic.esc(row.studentUsn || '')}${row.section ? ` · Section ${Academic.esc(row.section)}` : ''}</div>
              </td>
              <td>
                <label class="visually-hidden" for="score-${row.studentId}">Score for ${Academic.esc(row.studentName || 'student')}</label>
                <input type="number" class="form-control form-control-sm" id="score-${row.studentId}"
                       data-student-id="${row.studentId}" min="0" max="${max}" step="0.01"
                       value="${row.score === null || row.score === undefined ? '' : row.score}"
                       aria-label="Score for ${Academic.esc(row.studentName || 'student')}">
              </td>
              <td>
                <label class="visually-hidden" for="remarks-${row.studentId}">Remarks for ${Academic.esc(row.studentName || 'student')}</label>
                <input type="text" class="form-control form-control-sm" id="remarks-${row.studentId}"
                       data-remarks-for="${row.studentId}" maxlength="255"
                       value="${Academic.esc(row.remarks || '')}"
                       placeholder="Optional feedback"
                       aria-label="Remarks for ${Academic.esc(row.studentName || 'student')}">
              </td>
              <td class="small text-muted">${row.enteredAt ? `${Academic.esc(row.enteredByName || 'Faculty')}<br>${formatDateTime(row.enteredAt)}` : 'Not entered'}</td>
            </tr>`).join('')}
        </tbody>
      </table>`;
  }

  async function saveMarks() {
    const sheet = state.sheet;
    if (!sheet) return;
    const assessment = sheet.assessment || {};
    const max = Number(assessment.maxScore) || 0;
    const inputs = [...$('marksSheetMount').querySelectorAll('input[data-student-id]')];
    const entries = [];
    let invalid = null;

    inputs.forEach((input) => {
      const value = input.value.trim();
      if (value === '') return;
      const score = Number(value);
      if (!Number.isFinite(score) || score < 0 || score > max) {
        invalid = input;
        return;
      }
      const remarks = $('marksSheetMount').querySelector(`input[data-remarks-for="${input.dataset.studentId}"]`);
      entries.push({
        studentId: Number(input.dataset.studentId),
        score,
        remarks: remarks ? remarks.value.trim() || null : null
      });
    });

    if (invalid) {
      showSheetAlert(`Enter a score between 0 and ${Academic.num(max, 2)} for every student.`, 'danger');
      invalid.focus();
      return;
    }
    if (!entries.length) {
      showSheetAlert('Enter at least one score before saving.', 'warning');
      return;
    }

    const button = $('saveMarksBtn');
    try {
      await withBusy(button, () => api.put(`/marks/assessments/${assessment.id}/marks`, { marks: entries }));
      toast('Marks saved.', 'success');
      const refreshed = await api.get(`/marks/assessments/${assessment.id}/sheet`);
      state.sheet = refreshed;
      renderMarksSheet(refreshed);
      showSheetAlert('Marks saved successfully.', 'success');
    } catch (error) {
      showSheetAlert(errorMessage(error, 'The marks could not be saved.'), 'danger');
    }
  }

  function showSheetAlert(message, type = 'danger') {
    const alert = $('marksSheetAlert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.classList.remove('d-none');
  }

  function showAssessmentAlert(message, type = 'danger') {
    const alert = $('assessmentAlert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.classList.remove('d-none');
  }

  function showAlert(message, type = 'danger') {
    const alert = $('marksAlert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.classList.remove('d-none');
  }
})();
