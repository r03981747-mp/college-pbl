/* ============================================================================
 * smart-campus :: academic-settings.js
 * ----------------------------------------------------------------------------
 * Administrator-only eligibility rule configuration.
 * ========================================================================== */

(function () {
  'use strict';

  const $ = (id) => document.getElementById(id);

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('academic-settings.html', 'Academic Setup');

    if (!Academic.isAdmin() || !Auth.can('academicConfig.write')) {
      $('settingsDenied').classList.remove('d-none');
      $('settingsContent').classList.add('d-none');
      return;
    }

    $('settingsContent').classList.remove('d-none');
    $('settingsForm').addEventListener('submit', saveSettings);
    await loadSettings();
  }

  async function loadSettings() {
    $('settingsStatus').innerHTML = loadingBlock('Loading eligibility rules...');
    try {
      const settings = await api.get('/academics/settings');
      fillForm(settings);
      renderStatus(settings);
    } catch (error) {
      $('settingsStatus').innerHTML = errorBlock(errorMessage(error, 'Eligibility rules could not be loaded.'));
      const retry = $('settingsStatus').querySelector('[data-action="retry"]');
      if (retry) retry.addEventListener('click', loadSettings);
    }
  }

  function fillForm(settings) {
    $('minAttendancePercentage').value = settings.minAttendancePercentage ?? 75;
    $('minMarksPercentage').value = settings.minMarksPercentage ?? 40;
    $('minAssessmentsRequired').value = settings.minAssessmentsRequired ?? 1;
    $('minAttendanceRecords').value = settings.minAttendanceRecords ?? 1;
    $('requireMarks').checked = Boolean(settings.requireMarks);
    $('requireAttendance').checked = Boolean(settings.requireAttendance);
    $('lateCountsAsPresent').checked = Boolean(settings.lateCountsAsPresent);
    $('settingsUpdatedText').textContent = settings.updatedAt
      ? `Last saved ${formatDateTime(settings.updatedAt)}${settings.updatedByName ? ` by ${settings.updatedByName}` : ''}.`
      : 'These rules have not been saved yet.';
  }

  function renderStatus(settings) {
    if (!settings.rulesConfigured) {
      $('settingsStatus').innerHTML = `
        <div class="alert alert-warning mb-0" role="status">
          <i class="bi bi-exclamation-triangle me-1"></i>
          Eligibility rules are not configured yet. Students will see a pending / insufficient data result until you save these rules.
        </div>`;
      return;
    }
    $('settingsStatus').innerHTML = `
      <div class="alert alert-success mb-0" role="status">
        <i class="bi bi-check-circle me-1"></i>
        Eligibility rules are active: minimum ${Academic.pct(settings.minAttendancePercentage)} attendance and
        ${Academic.pct(settings.minMarksPercentage)} marks, with at least
        ${Academic.num(settings.minAssessmentsRequired, 0)} assessment(s) and
        ${Academic.num(settings.minAttendanceRecords, 0)} attendance record(s) required.
      </div>`;
  }

  async function saveSettings(event) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.checkValidity()) {
      form.classList.add('was-validated');
      return;
    }

    const requireMarks = $('requireMarks').checked;
    const requireAttendance = $('requireAttendance').checked;
    if (!requireMarks && !requireAttendance) {
      showAlert('Enable at least one requirement: marks or attendance.', 'warning');
      return;
    }

    const payload = {
      minAttendancePercentage: Number($('minAttendancePercentage').value),
      minMarksPercentage: Number($('minMarksPercentage').value),
      minAssessmentsRequired: Number($('minAssessmentsRequired').value),
      minAttendanceRecords: Number($('minAttendanceRecords').value),
      requireMarks,
      requireAttendance,
      lateCountsAsPresent: $('lateCountsAsPresent').checked
    };

    const button = $('saveSettingsBtn');
    try {
      const saved = await withBusy(button, () => api.put('/academics/settings', payload));
      fillForm(saved);
      renderStatus(saved);
      showAlert('Eligibility rules saved. Student results will use the new thresholds immediately.', 'success');
      toast('Eligibility rules saved.', 'success');
    } catch (error) {
      showAlert(errorMessage(error, 'The eligibility rules could not be saved.'));
      showFieldErrors(form, error.errors);
    }
  }

  function showAlert(message, type = 'danger') {
    const alert = $('settingsAlert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.classList.remove('d-none');
  }
})();
