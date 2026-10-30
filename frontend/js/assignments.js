/* ============================================================================
 * smart-campus :: assignments.js
 * ----------------------------------------------------------------------------
 * Student: view published assignments, upload/resubmit a file, see grades.
 * Faculty/Admin: create assignments, publish, inspect submissions, review.
 * ========================================================================== */

(function () {
  'use strict';

  const state = {
    courses: [],
    assignments: [],
    submissions: [],
    submissionsAssignment: null
  };

  const $ = (id) => document.getElementById(id);

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('assignments.html', 'Assignments');

    if (!Auth.can('assignments.read')) {
      showAlert('You do not have permission to view assignments.', 'warning');
      return;
    }

    $('refreshAssignments').addEventListener('click', loadAssignments);
    $('assignmentCourseFilter').addEventListener('change', renderAssignments);

    if (Academic.isStudent()) {
      $('pageTitle').textContent = 'My Assignments';
      $('pageIntro').textContent = 'Read instructions, upload your work securely and see faculty feedback.';
      $('assignmentsMount').addEventListener('click', onStudentAction);
      $('assignmentsMount').addEventListener('submit', onStudentSubmit);
      await loadAssignments();
    } else if (Academic.isStaff()) {
      $('pageTitle').textContent = 'Assignments Management';
      $('pageIntro').textContent = 'Create coursework, publish it to students and review secure submissions.';
      $('addAssignmentBtn').classList.remove('d-none');
      bindStaff();
      await loadAssignments();
    } else {
      showAlert('This page is not available for your role.', 'warning');
    }
  }

  function bindStaff() {
    $('addAssignmentBtn').addEventListener('click', () => openAssignmentForm(null));
    $('assignmentForm').addEventListener('submit', saveAssignment);
    $('assignmentsMount').addEventListener('click', onStaffAction);
    $('submissionsMount').addEventListener('click', onSubmissionsAction);
    $('reviewForm').addEventListener('submit', saveReview);
  }

  async function loadAssignments() {
    $('assignmentsMount').innerHTML = loadingBlock('Loading assignments...');
    $('assignmentStats').innerHTML = '';
    try {
      state.courses = await Academic.courses(true);
      const selected = $('assignmentCourseFilter').value;
      Academic.fillCourseSelect($('assignmentCourseFilter'), state.courses, 'All subjects', selected);
      state.assignments = await api.get('/assignments');
      renderAssignments();
    } catch (error) {
      $('assignmentsMount').innerHTML = errorBlock(errorMessage(error, 'Assignments could not be loaded.'));
      const retry = $('assignmentsMount').querySelector('[data-action="retry"]');
      if (retry) retry.addEventListener('click', loadAssignments);
    }
  }

  function visibleAssignments() {
    const courseId = $('assignmentCourseFilter').value;
    return state.assignments.filter((assignment) =>
      !courseId || String(assignment.courseId) === String(courseId));
  }

  function renderAssignments() {
    const visible = visibleAssignments();
    $('assignmentFilterSummary').textContent = visible.length
      ? `Showing ${visible.length} assignment(s).`
      : 'No assignments match this subject.';
    if (Academic.isStudent()) renderStudent(visible);
    else renderStaff(visible);
  }

  /* ============================================================ STUDENT */

  function renderStudent(assignments) {
    const submitted = assignments.filter((item) => item.mySubmission).length;
    const graded = assignments.filter((item) => item.mySubmission && item.mySubmission.status === 'GRADED').length;
    const open = assignments.filter((item) => Academic.canSubmit(item)).length;

    $('assignmentStats').innerHTML = [
      Academic.stat('Assignments', Academic.num(assignments.length, 0), 'Published to you'),
      Academic.stat('Open for submission', Academic.num(open, 0), 'Including late-allowed'),
      Academic.stat('Submitted', Academic.num(submitted, 0), 'Files received'),
      Academic.stat('Graded', Academic.num(graded, 0), 'Feedback available')
    ].join('');

    if (!assignments.length) {
      $('assignmentsMount').innerHTML = `<div class="card"><div class="card-body">
        ${Academic.empty('bi-journal-x', 'No assignments found', 'Your faculty have not published assignments for the selected subject.')}
      </div></div>`;
      return;
    }

    $('assignmentsMount').innerHTML = `<div class="row g-3">${assignments.map(renderStudentCard).join('')}</div>`;
  }

  function renderStudentCard(assignment) {
    const due = Academic.dueState(assignment);
    const submission = assignment.mySubmission;
    const types = assignment.allowedFileTypes || '';
    const accept = types ? types.split(',').map((type) => `.${type.trim()}`).join(',') : '';
    const closed = due.state === 'closed';
    const submitAllowed = Academic.canSubmit(assignment);

    const submissionHtml = submission ? `
      <div class="submission-row mb-3">
        <div class="d-flex flex-wrap justify-content-between align-items-start gap-2">
          <div>
            <div class="name"><i class="bi bi-file-earmark-check me-1 text-success"></i>${Academic.esc(submission.originalFileName || 'Submitted file')}</div>
            <div class="meta">Submitted ${submission.submittedAt ? formatDateTime(submission.submittedAt) : '—'} · attempt ${Academic.num(submission.attemptNo, 0)} · ${Academic.fileSize(submission.fileSize)}</div>
            <div class="mt-1 d-flex flex-wrap gap-1">
              ${statusBadge(submission.status || 'SUBMITTED')}
              ${submission.late ? statusBadge('LATE') : ''}
            </div>
          </div>
          <button type="button" class="btn btn-outline-secondary btn-sm" data-download-submission="${submission.id}" data-file-name="${Academic.esc(submission.originalFileName || 'submission')}">
            <i class="bi bi-download me-1"></i>Download
          </button>
        </div>
        ${submission.status === 'GRADED' || submission.grade !== null && submission.grade !== undefined ? `
          <div class="mt-2 pt-2 border-top">
            <div class="d-flex flex-wrap justify-content-between gap-2">
              <strong>Grade: ${Academic.num(submission.grade, 2)} / ${Academic.num(assignment.maxScore, 2)}</strong>
              <span class="small text-muted">${submission.reviewedByName ? `Reviewed by ${Academic.esc(submission.reviewedByName)}` : ''}</span>
            </div>
            ${submission.feedback ? `<div class="small text-secondary mt-1">${Academic.esc(submission.feedback)}</div>` : ''}
          </div>` : ''}
      </div>` : `
      <div class="empty-inline mb-3"><i class="bi bi-cloud-arrow-up me-1"></i>No file submitted yet.</div>`;

    const uploadHtml = submitAllowed ? `
      <form class="mt-2" data-submit-form data-assignment-id="${assignment.id}" data-max-size="${assignment.maxFileSizeMb}" data-allowed-types="${Academic.esc(types)}">
        <div class="file-drop">
          <label class="form-label small fw-semibold" for="file-${assignment.id}">
            ${submission ? 'Choose a replacement file' : 'Choose a file to submit'}
          </label>
          <input class="form-control form-control-sm" type="file" id="file-${assignment.id}" name="file"
                 ${accept ? `accept="${Academic.esc(accept)}"` : ''} required>
          <div class="form-text">
            Allowed: ${Academic.esc(Academic.allowedTypesLabel(types))} · Maximum ${Academic.num(assignment.maxFileSizeMb, 0)} MB
          </div>
        </div>
        <button type="submit" class="btn btn-primary btn-sm mt-2">
          <i class="bi bi-cloud-arrow-up me-1"></i>${submission ? 'Resubmit' : 'Submit assignment'}
        </button>
      </form>` : `
      <div class="empty-inline mt-2">
        <i class="bi bi-lock me-1"></i>${closed
          ? 'The due date has passed and late submissions are not allowed.'
          : 'This assignment has already been submitted and resubmission is not enabled.'}
      </div>`;

    return `
      <div class="col-12 col-xl-6">
        <div class="card academic-card assignment-card ${closed ? 'is-closed' : ''}">
          <div class="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
            <div class="course-title">
              <span class="code">${Academic.esc(assignment.courseCode || '—')}</span>
              <h3>${Academic.esc(assignment.title || 'Assignment')}</h3>
            </div>
            ${statusBadge(due.state === 'open' ? 'PUBLISHED' : due.state === 'late' ? 'LATE' : 'CLOSED', due.label)}
          </div>
          <div class="card-body">
            <div class="assignment-meta">
              <span><i class="bi bi-calendar-event"></i>Due ${formatDateTime(assignment.dueAt)}</span>
              <span><i class="bi bi-award"></i>${Academic.num(assignment.maxScore, 2)} marks</span>
              <span><i class="bi bi-paperclip"></i>${Academic.esc(Academic.allowedTypesLabel(assignment.allowedFileTypes))}</span>
            </div>
            <p class="instructions">${Academic.esc(assignment.instructions || 'No instructions provided.')}</p>
            ${submissionHtml}
            ${uploadHtml}
          </div>
          <div class="card-footer small text-muted">
            <i class="bi bi-person-badge me-1"></i>${Academic.esc(assignment.createdByName || 'Faculty')}
          </div>
        </div>
      </div>`;
  }

  async function onStudentAction(event) {
    const button = event.target.closest('[data-download-submission]');
    if (!button) return;
    try {
      await withBusy(button, () => Academic.downloadSubmission(
        Number(button.dataset.downloadSubmission), button.dataset.fileName));
    } catch (error) {
      showAlert(errorMessage(error, 'The submission could not be downloaded.'));
    }
  }

  async function onStudentSubmit(event) {
    const form = event.target.closest('form[data-submit-form]');
    if (!form) return;
    event.preventDefault();

    const assignmentId = Number(form.dataset.assignmentId);
    const input = form.querySelector('input[type="file"]');
    const file = input && input.files && input.files[0];
    if (!file) {
      showAlert('Choose a file before submitting.', 'warning');
      return;
    }

    const allowed = String(form.dataset.allowedTypes || '')
      .split(',').map((type) => type.trim().toLowerCase()).filter(Boolean);
    const extension = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : '';
    if (allowed.length && !allowed.includes(extension)) {
      showAlert(`This assignment accepts ${Academic.allowedTypesLabel(form.dataset.allowedTypes)} files.`, 'warning');
      return;
    }
    const maxMb = Number(form.dataset.maxSize) || 0;
    if (maxMb && file.size > maxMb * 1024 * 1024) {
      showAlert(`The file is larger than the ${Academic.num(maxMb, 0)} MB limit for this assignment.`, 'warning');
      return;
    }

    const formData = new FormData();
    formData.append('file', file);
    const button = form.querySelector('button[type="submit"]');
    try {
      await withBusy(button, () => api.raw(`/assignments/${assignmentId}/submit`, {
        method: 'POST',
        body: formData
      }));
      toast('Assignment submitted securely.', 'success');
      await loadAssignments();
    } catch (error) {
      showAlert(errorMessage(error, 'The assignment could not be submitted.'));
    }
  }

  /* ============================================================== STAFF */

  function renderStaff(assignments) {
    const published = assignments.filter((item) => item.published).length;
    const drafts = assignments.length - published;
    const submissions = assignments.reduce((sum, item) => sum + (Number(item.submissionCount) || 0), 0);

    $('assignmentStats').innerHTML = [
      Academic.stat('Assignments', Academic.num(assignments.length, 0), 'Matching the filter'),
      Academic.stat('Published', Academic.num(published, 0), 'Visible to students'),
      Academic.stat('Drafts', Academic.num(drafts, 0), 'Not visible yet'),
      Academic.stat('Submissions', Academic.num(submissions, 0), 'Files received')
    ].join('');

    if (!state.courses.length) {
      $('assignmentsMount').innerHTML = `<div class="card"><div class="card-body">
        ${Academic.empty('bi-journal-x', 'No assigned subjects', 'An administrator must assign subjects to you before you can create assignments.')}
      </div></div>`;
      return;
    }

    if (!assignments.length) {
      $('assignmentsMount').innerHTML = `<div class="card"><div class="card-body">
        ${Academic.empty('bi-journal-plus', 'No assignments found', 'Create an assignment for one of your subjects.')}
      </div></div>`;
      return;
    }

    const rows = assignments.map((assignment) => `
      <tr>
        <td>
          <div class="fw-semibold">${Academic.esc(assignment.title)}</div>
          <div class="small text-muted">${Academic.esc(assignment.courseCode || '')} · ${Academic.esc(assignment.courseName || '')}</div>
        </td>
        <td>${formatDateTime(assignment.dueAt)}</td>
        <td class="score">${Academic.num(assignment.maxScore, 2)}</td>
        <td>
          <div class="small">${Academic.num(assignment.submissionCount, 0)} received</div>
          <div class="small text-muted">${Academic.num(assignment.gradedCount, 0)} graded</div>
        </td>
        <td>${statusBadge(assignment.published ? 'PUBLISHED' : 'DRAFT')}</td>
        <td class="text-end" style="min-width:13rem">
          <div class="btn-group btn-group-sm" role="group" aria-label="Assignment actions">
            <button type="button" class="btn btn-outline-primary" data-action="submissions" data-id="${assignment.id}" title="View submissions" aria-label="View submissions for ${Academic.esc(assignment.title)}">
              <i class="bi bi-inbox"></i>
            </button>
            <button type="button" class="btn btn-outline-${assignment.published ? 'warning' : 'success'}" data-action="publish" data-id="${assignment.id}" data-published="${assignment.published ? 'true' : 'false'}" title="${assignment.published ? 'Move to draft' : 'Publish'}" aria-label="${assignment.published ? 'Unpublish' : 'Publish'} ${Academic.esc(assignment.title)}">
              <i class="bi ${assignment.published ? 'bi-eye-slash' : 'bi-send-check'}"></i>
            </button>
            <button type="button" class="btn btn-outline-secondary" data-action="edit" data-id="${assignment.id}" title="Edit assignment" aria-label="Edit ${Academic.esc(assignment.title)}">
              <i class="bi bi-pencil"></i>
            </button>
            <button type="button" class="btn btn-outline-danger" data-action="delete" data-id="${assignment.id}" title="Delete assignment" aria-label="Delete ${Academic.esc(assignment.title)}">
              <i class="bi bi-trash3"></i>
            </button>
          </div>
        </td>
      </tr>`).join('');

    $('assignmentsMount').innerHTML = `
      <div class="card">
        <div class="card-header">
          <h6 class="mb-0">Assignment catalogue</h6>
          <small class="text-secondary">Publish only when instructions and deadlines are final.</small>
        </div>
        <div class="table-responsive">
          <table class="table mark-table align-middle mb-0">
            <thead><tr><th>Assignment</th><th>Due</th><th>Max</th><th>Submissions</th><th>Status</th><th class="text-end">Actions</th></tr></thead>
            <tbody>${rows}</tbody>
          </table>
        </div>
      </div>`;
  }

  function onStaffAction(event) {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const id = Number(button.dataset.id);
    const assignment = state.assignments.find((item) => Number(item.id) === id);
    if (!assignment) return;

    if (button.dataset.action === 'edit') openAssignmentForm(assignment);
    if (button.dataset.action === 'publish') togglePublished(assignment, button);
    if (button.dataset.action === 'delete') deleteAssignment(assignment, button);
    if (button.dataset.action === 'submissions') openSubmissions(assignment);
  }

  function openAssignmentForm(assignment) {
    const form = $('assignmentForm');
    form.reset();
    form.classList.remove('was-validated');
    form.querySelectorAll('.is-invalid').forEach((field) => field.classList.remove('is-invalid'));
    $('assignmentAlert').classList.add('d-none');
    $('assignmentModalTitle').textContent = assignment ? 'Edit assignment' : 'New assignment';
    $('assignmentId').value = assignment ? assignment.id : '';
    Academic.fillCourseSelect($('assignmentCourse'), state.courses, null,
      assignment ? assignment.courseId : ($('assignmentCourseFilter').value || (state.courses[0] && state.courses[0].id) || ''));
    $('assignmentTitle').value = assignment ? assignment.title : '';
    $('assignmentInstructions').value = assignment ? assignment.instructions : '';
    $('assignmentDueAt').value = assignment && assignment.dueAt
      ? String(assignment.dueAt).slice(0, 16)
      : defaultDueAt();
    $('assignmentFileTypes').value = assignment && assignment.allowedFileTypes ? assignment.allowedFileTypes : 'pdf,doc,docx';
    $('assignmentMaxSize').value = assignment ? assignment.maxFileSizeMb : 10;
    $('assignmentMaxScore').value = assignment && assignment.maxScore !== null ? assignment.maxScore : '';
    $('assignmentAllowLate').checked = assignment ? Boolean(assignment.allowLate) : false;
    $('assignmentAllowResubmission').checked = assignment ? Boolean(assignment.allowResubmission) : false;
    $('assignmentPublished').checked = assignment ? Boolean(assignment.published) : false;
    Academic.showModal('assignmentModal');
  }

  async function saveAssignment(event) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.checkValidity()) {
      form.classList.add('was-validated');
      return;
    }

    const id = $('assignmentId').value;
    const payload = {
      courseId: Number($('assignmentCourse').value),
      title: $('assignmentTitle').value.trim(),
      instructions: $('assignmentInstructions').value.trim(),
      dueAt: $('assignmentDueAt').value,
      allowedFileTypes: $('assignmentFileTypes').value.trim() || null,
      maxFileSizeMb: Number($('assignmentMaxSize').value),
      maxScore: Number($('assignmentMaxScore').value),
      allowLate: $('assignmentAllowLate').checked,
      allowResubmission: $('assignmentAllowResubmission').checked,
      published: $('assignmentPublished').checked
    };

    const button = $('saveAssignmentBtn');
    try {
      await withBusy(button, () => id
        ? api.put(`/assignments/${id}`, payload)
        : api.post('/assignments', payload));
      Academic.hideModal('assignmentModal');
      toast(id ? 'Assignment updated.' : 'Assignment created.', 'success');
      await loadAssignments();
    } catch (error) {
      showAssignmentAlert(errorMessage(error, 'The assignment could not be saved.'));
      showFieldErrors(form, error.errors);
    }
  }

  async function togglePublished(assignment, button) {
    const next = !assignment.published;
    try {
      await withBusy(button, () => api.put(`/assignments/${assignment.id}/publish?published=${next}`));
      toast(next ? 'Assignment published to students.' : 'Assignment moved back to draft.', 'success');
      await loadAssignments();
    } catch (error) {
      showAlert(errorMessage(error, 'The assignment status could not be changed.'));
    }
  }

  async function deleteAssignment(assignment, button) {
    const confirmed = await confirmDialog({
      title: 'Delete assignment?',
      message: `Delete “${assignment.title}”? Its submissions and stored files will also be removed.`,
      confirmLabel: 'Delete assignment',
      danger: true
    });
    if (!confirmed) return;
    try {
      await withBusy(button, () => api.del(`/assignments/${assignment.id}`));
      toast('Assignment deleted.', 'success');
      await loadAssignments();
    } catch (error) {
      showAlert(errorMessage(error, 'The assignment could not be deleted.'));
    }
  }

  async function openSubmissions(assignment) {
    state.submissionsAssignment = assignment;
    $('submissionsModalTitle').textContent = `Submissions · ${assignment.title}`;
    $('submissionsModalSubtitle').textContent = `${assignment.courseCode} · ${Academic.num(assignment.submissionCount, 0)} received · ${Academic.num(assignment.gradedCount, 0)} graded`;
    $('submissionsMount').innerHTML = loadingBlock('Loading submissions...');
    $('submissionsAlert').classList.add('d-none');
    Academic.showModal('submissionsModal');

    try {
      state.submissions = await api.get(`/assignments/${assignment.id}/submissions`);
      renderSubmissions();
    } catch (error) {
      $('submissionsMount').innerHTML = errorBlock(errorMessage(error, 'Submissions could not be loaded.'));
    }
  }

  function renderSubmissions() {
    const submissions = state.submissions || [];
    if (!submissions.length) {
      $('submissionsMount').innerHTML = Academic.empty('bi-inbox', 'No submissions yet', 'Students will appear here after they upload a file.');
      return;
    }

    $('submissionsMount').innerHTML = `
      <div class="table-responsive">
        <table class="table mark-table align-middle">
          <thead><tr><th>Student</th><th>File</th><th>Submitted</th><th>Status</th><th>Grade</th><th class="text-end">Review</th></tr></thead>
          <tbody>
            ${submissions.map((submission) => `
              <tr>
                <td>
                  <div class="fw-semibold">${Academic.esc(submission.studentName || 'Student')}</div>
                  <div class="small text-muted">${Academic.esc(submission.studentUsn || '')}</div>
                </td>
                <td>
                  <button type="button" class="btn btn-link btn-sm p-0 text-start" data-download-submission="${submission.id}" data-file-name="${Academic.esc(submission.originalFileName || 'submission')}">
                    <i class="bi bi-paperclip me-1"></i>${Academic.esc(submission.originalFileName || 'Download')}
                  </button>
                  <div class="small text-muted">${Academic.fileSize(submission.fileSize)}</div>
                </td>
                <td class="small">${formatDateTime(submission.submittedAt)}${submission.attemptNo > 1 ? `<div class="text-muted">Attempt ${submission.attemptNo}</div>` : ''}</td>
                <td>${statusBadge(submission.status || 'SUBMITTED')}${submission.late ? ` ${statusBadge('LATE')}` : ''}</td>
                <td>${submission.grade !== null && submission.grade !== undefined ? `${Academic.num(submission.grade, 2)} / ${Academic.num(submission.assignmentMaxScore, 2)}` : '<span class="text-muted small">Not graded</span>'}</td>
                <td class="text-end">
                  <button type="button" class="btn btn-outline-primary btn-sm" data-action="review" data-id="${submission.id}">
                    <i class="bi bi-pencil-square me-1"></i>Review
                  </button>
                </td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;
  }

  async function onSubmissionsAction(event) {
    const download = event.target.closest('[data-download-submission]');
    if (download) {
      try {
        await withBusy(download, () => Academic.downloadSubmission(
          Number(download.dataset.downloadSubmission), download.dataset.fileName));
      } catch (error) {
        showSubmissionsAlert(errorMessage(error, 'The file could not be downloaded.'));
      }
      return;
    }

    const review = event.target.closest('button[data-action="review"]');
    if (!review) return;
    const submission = state.submissions.find((item) => Number(item.id) === Number(review.dataset.id));
    if (!submission) return;
    openReview(submission);
  }

  function openReview(submission) {
    const assignment = state.submissionsAssignment;
    if (!assignment) return;
    $('reviewAssignmentId').value = assignment.id;
    $('reviewSubmissionId').value = submission.id;
    $('reviewAlert').classList.add('d-none');
    $('reviewModalTitle').textContent = 'Review submission';
    $('reviewModalSubtitle').textContent = `${submission.studentName || 'Student'} · ${submission.originalFileName || 'File'}`;
    $('reviewGrade').max = assignment.maxScore;
    $('reviewGrade').value = submission.grade === null || submission.grade === undefined ? '' : submission.grade;
    $('reviewFeedback').value = submission.feedback || '';
    $('reviewMaxHint').textContent = `Maximum score: ${Academic.num(assignment.maxScore, 2)}`;
    $('reviewForm').classList.remove('was-validated');
    Academic.showModal('reviewModal');
  }

  async function saveReview(event) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.checkValidity()) {
      form.classList.add('was-validated');
      return;
    }
    const assignmentId = $('reviewAssignmentId').value;
    const submissionId = $('reviewSubmissionId').value;
    const grade = Number($('reviewGrade').value);
    const feedback = $('reviewFeedback').value.trim();
    const max = Number($('reviewGrade').max) || 0;
    if (grade < 0 || grade > max) {
      showReviewAlert(`Grade must be between 0 and ${Academic.num(max, 2)}.`, 'warning');
      return;
    }

    const button = $('saveReviewBtn');
    try {
      await withBusy(button, () => api.put(`/assignments/${assignmentId}/submissions/${submissionId}/review`, {
        grade,
        feedback: feedback || null
      }));
      Academic.hideModal('reviewModal');
      toast('Submission review saved.', 'success');
      const assignment = state.submissionsAssignment;
      await loadAssignments();
      if (assignment) {
        const fresh = state.assignments.find((item) => Number(item.id) === Number(assignment.id));
        if (fresh) await openSubmissions(fresh);
      }
    } catch (error) {
      showReviewAlert(errorMessage(error, 'The review could not be saved.'));
    }
  }

  function defaultDueAt() {
    const date = new Date();
    date.setDate(date.getDate() + 7);
    date.setHours(23, 59, 0, 0);
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  }

  function showAlert(message, type = 'danger') {
    const alert = $('assignmentsAlert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.classList.remove('d-none');
  }

  function showSubmissionsAlert(message, type = 'danger') {
    const alert = $('submissionsAlert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.classList.remove('d-none');
  }

  function showAssignmentAlert(message, type = 'danger') {
    const alert = $('assignmentAlert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.classList.remove('d-none');
  }

  function showReviewAlert(message, type = 'danger') {
    const alert = $('reviewAlert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.classList.remove('d-none');
  }
})();
