/* ============================================================================
 * smart-campus :: bookings.js
 * ----------------------------------------------------------------------------
 * The Resource Bookings module - the core page of the system.
 *
 *   1. guard the page  -> Auth.requireAuth()
 *   2. paint the shell -> AppShell.init('bookings.html', 'Bookings')
 *   3. build a CrudTable over GET /api/bookings (keyword + filters + sort +
 *      paging + per-row actions honouring the permission keys)
 *   4. wire the create form to POST /api/bookings and POST
 *      /api/bookings/availability - HTTP 409 is displayed verbatim
 *   5. administrators additionally get the multithreading race test that
 *      POSTs to /api/concurrency/run and renders one outcome per Java thread
 *
 * UI gating (this file) is convenience only: the Spring Boot controllers
 * re-check every permission, the role and the booking ownership.
 * ========================================================================== */

(function () {
  'use strict';

  const MODULE = 'bookings';
  const API = '/bookings';
  const CONCURRENCY_API = '/concurrency';

  /** Java enum com.smartcampus.model.enums.BookingStatus */
  const STATUSES = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED'];

  /** Java enum com.smartcampus.model.enums.ResourceType + drop-down groups */
  const RESOURCE_GROUPS = [
    { type: 'CLASSROOM', label: 'Classrooms' },
    { type: 'LABORATORY', label: 'Laboratories' },
    { type: 'EQUIPMENT', label: 'Equipment' }
  ];

  /** Java enum com.smartcampus.model.enums.ResourceStatus */
  const RESOURCE_STATUS_LABELS = {
    ACTIVE: 'Active',
    MAINTENANCE: 'Under maintenance',
    INACTIVE: 'Inactive'
  };

  let table = null;

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('bookings.html', 'Bookings');

    const canRead = Auth.can('bookings.read');
    const canWrite = Auth.can('bookings.write');
    const canReadAll = Auth.can('bookings.readAll');
    const canExport = Auth.can('reports.read');

    const roleChip = document.querySelector('.page-actions [data-user-role]');
    if (roleChip) roleChip.textContent = Auth.role() || '—';

    /* scope chip: the server always filters the list, this only explains it */
    const scopeChip = document.getElementById('scopeChip');
    if (scopeChip) {
      scopeChip.innerHTML = canReadAll
        ? '<i class="bi bi-globe2 text-success"></i> Scope: <strong>All bookings</strong>'
        : '<i class="bi bi-person text-primary"></i> Scope: <strong>My bookings only</strong>';
    }

    if (canExport) {
      const reportsLink = document.getElementById('reportsLink');
      if (reportsLink) reportsLink.classList.remove('d-none');
    }

    table = new CrudTable({
      mount: '#tableMount',
      apiPath: API,
      searchPlaceholder: 'Search by booking ref, resource, person or purpose…',
      emptyTitle: canRead ? 'No bookings match your search' : 'No bookings to display',
      emptyDetail: 'Clear the filters, or create a new booking request.',
      createLabel: 'New booking',
      canCreate: canWrite,
      exportPath: canExport ? '/reports/bookings' : null,
      exportFilename: 'bookings.csv',
      pageSizes: [5, 10, 25, 50],

      filters: [
        { name: 'status', label: 'Status' },
        { name: 'resourceType', label: 'Resource type' }
      ],

      /* only sort keys whitelisted by BookingRepository.SORT_COLUMNS */
      sortOptions: [
        { value: 'createdAt:desc', label: 'Newest first' },
        { value: 'bookingDate:asc', label: 'Booking date (soonest)' },
        { value: 'bookingDate:desc', label: 'Booking date (latest)' },
        { value: 'startTime:asc', label: 'Start time (earliest)' },
        { value: 'status:asc', label: 'Status' },
        { value: 'bookingRef:asc', label: 'Booking reference' },
        { value: 'resourceName:asc', label: 'Resource (A → Z)' },
        { value: 'requestedByName:asc', label: 'Requested by (A → Z)' }
      ],
      defaultSort: 'createdAt:desc',

      columns: [
        {
          key: 'bookingRef',
          label: 'Booking',
          render: (v) => codeBadge(v, 'bg-dark')
        },
        {
          key: 'resourceName',
          label: 'Resource',
          render: (v, row) => `
            <span class="cell-strong">${escapeHtml(v || '—')}</span>
            <span class="cell-sub">${labelify(row.resourceType)}</span>`
        },
        {
          key: 'requestedByName',
          label: 'Requested by',
          className: 'hide-mobile',
          cellClass: 'hide-mobile',
          render: (v, row) => `
            <span class="cell-strong">${escapeHtml(v || '—')}</span>
            <span class="cell-sub">${row.requestedBy !== null && row.requestedBy !== undefined
              ? 'User #' + escapeHtml(row.requestedBy) : '—'}</span>`
        },
        { key: 'bookingDate', label: 'Date', render: (v) => formatDate(v) },
        {
          key: 'startTime',
          label: 'Time',
          render: (v, row) => `${formatTime(v)} – ${formatTime(row.endTime)}`
        },
        { key: 'status', label: 'Status', render: (v) => statusBadge(v) },
        {
          key: 'id',
          label: 'Actions',
          className: 'text-end',
          cellClass: 'text-end',
          render: (v, row, index) => renderRowActions(row, index)
        }
      ],

      /* row actions are rendered per row by renderRowActions() above and
         dispatched by the delegated listener bound below */
      onCreate: canWrite ? () => openForm() : null
    });

    table.mountEl.addEventListener('click', onTableAction);

    /* ---- date filter strip (filter key "date" is read by the repository) */
    const dateFilter = document.getElementById('dateFilter');
    if (dateFilter) {
      dateFilter.addEventListener('change', () => table.setFilter('date', dateFilter.value.trim()));
    }
    const dateClear = document.getElementById('dateFilterClear');
    if (dateClear) {
      dateClear.addEventListener('click', () => {
        if (dateFilter) dateFilter.value = '';
        table.setFilter('date', '');
      });
    }

    bindForm();

    if (Auth.isAdmin()) initRacePanel();

    /* restore ?q= coming from the global search box */
    const q = new URLSearchParams(location.search).get('q');
    if (q) {
      const input = table.mountEl.querySelector('[data-toolbar="search"]');
      if (input) input.value = q;
      table.query.keyword = q;
    }

    await Promise.all([table.refresh(), loadFilterOptions(), loadBookableResources()]);
  }

  /* --------------------------------------------------------- filter lists */

  async function loadFilterOptions() {
    table.setFilterOptions('status',
      STATUSES.map((s) => ({ value: s, label: labelify(s) })), 'Status: all');
    table.setFilterOptions('resourceType',
      RESOURCE_GROUPS.map((g) => ({ value: g.type, label: g.label })), 'Resource type: all');
  }

  /** GET /api/bookings/resources -> [{type,id,label,status}] grouped by type. */
  async function loadBookableResources() {
    const select = document.getElementById('resource');
    try {
      const options = await apiGet(API + '/resources');
      fillResourceSelect(select, options, 'Select a classroom, laboratory or equipment', true);
    } catch (error) {
      toastError(errorMessage(error, 'The list of resources could not be loaded.'));
      if (select) select.innerHTML = '<option value="">Resources could not be loaded</option>';
    }
  }

  /** Builds one <select> with Classrooms / Laboratories / Equipment groups. */
  function fillResourceSelect(select, options, placeholder, rejectNonActive) {
    if (!select) return;
    const items = Array.isArray(options) ? options : [];
    let html = `<option value="">${escapeHtml(placeholder)}</option>`;

    RESOURCE_GROUPS.forEach((group) => {
      const inGroup = items.filter((item) => item && item.type === group.type);
      if (!inGroup.length) return;
      html += `<optgroup label="${escapeHtml(group.label)}">`;
      inGroup.forEach((item) => {
        const value = `${item.type}:${item.id}`;
        const status = item.status || '';
        const blocked = rejectNonActive && !!status && status !== 'ACTIVE';
        const suffix = status
          ? ` (${escapeHtml(RESOURCE_STATUS_LABELS[status] || labelify(status))})` : '';
        html += `<option value="${escapeHtml(value)}"${blocked ? ' disabled' : ''}>` +
                `${escapeHtml(String(item.label || '') + suffix)}</option>`;
      });
      html += '</optgroup>';
    });

    select.innerHTML = html;
  }

  /* --------------------------------------------------------- row actions */

  function canCancel(status) {
    /* BookingServiceImpl.cancel(): refused only for CANCELLED / COMPLETED,
       ownership is enforced server side by getVisible(). */
    return status === 'PENDING' || status === 'APPROVED' || status === 'REJECTED';
  }

  function renderRowActions(row, index) {
    const canApprove = Auth.can('bookings.approve');
    const status = row.status;
    const button = (action, icon, title, color) => `
      <button type="button" class="btn btn-sm btn-outline-${color} border-0"
              data-bk-action="${action}" data-bk-index="${index}" title="${escapeHtml(title)}">
        <i class="bi ${icon}"></i></button>`;

    let html = button('view', 'bi-eye', 'View booking', 'secondary');

    if (canApprove && status === 'PENDING') {
      html += button('approve', 'bi-check2-circle', 'Approve', 'success');
      html += button('reject', 'bi-x-circle', 'Reject', 'danger');
    }
    if (canApprove && status === 'APPROVED') {
      html += button('complete', 'bi-check2-all', 'Mark completed', 'success');
    }
    if (canCancel(status)) {
      html += button('cancel', 'bi-slash-circle', 'Cancel booking', 'warning');
    }
    if (Auth.isAdmin()) {
      html += button('delete', 'bi-trash3', 'Delete booking', 'danger');
    }

    return `<div class="btn-group btn-group-sm">${html}</div>`;
  }

  function onTableAction(event) {
    const button = event.target.closest('[data-bk-action]');
    if (!button || !table || !table.mountEl.contains(button)) return;

    const row = table.data[parseInt(button.dataset.bkIndex, 10)];
    if (!row) return;

    switch (button.dataset.bkAction) {
      case 'view': showView(row); break;
      case 'approve': changeStatus(row, 'APPROVED', button); break;
      case 'reject': changeStatus(row, 'REJECTED', button); break;
      case 'complete': changeStatus(row, 'COMPLETED', button); break;
      case 'cancel': cancelBooking(row, button); break;
      case 'delete': removeBooking(row); break;
      default: break;
    }
  }

  /* ---------------------------------------------------------------- VIEW */

  function showView(row) {
    document.getElementById('viewName').textContent = row.resourceName || 'Booking';
    document.getElementById('viewSub').textContent =
      (row.bookingRef || '') + ' · ' + labelify(row.resourceType);

    const pairs = [
      ['Booking reference', codeBadge(row.bookingRef, 'bg-dark')],
      ['Status', statusBadge(row.status)],
      ['Resource type', labelify(row.resourceType)],
      ['Resource name', escapeHtml(row.resourceName || '—')],
      ['Resource id', row.resourceId !== null && row.resourceId !== undefined
        ? escapeHtml(row.resourceId) : '—'],
      ['Requested by', escapeHtml(row.requestedByName || '—') +
        (row.requestedBy !== null && row.requestedBy !== undefined
          ? ` <span class="cell-sub">User #${escapeHtml(row.requestedBy)}</span>` : '')],
      ['Booking date', formatDate(row.bookingDate)],
      ['Start time', formatTime(row.startTime)],
      ['End time', formatTime(row.endTime)],
      ['Purpose', escapeHtml(row.purpose || '—')],
      ['Booking id', row.id !== null && row.id !== undefined ? escapeHtml(row.id) : '—'],
      ['Created at', formatDateTime(row.createdAt)]
    ];

    document.getElementById('viewBody').innerHTML = `
      <div class="kv-list">
        ${pairs.map(([k, v]) =>
          `<div class="kv-row"><span class="k">${escapeHtml(k)}</span><span class="v">${v}</span></div>`).join('')}
      </div>
      <div class="info-banner mt-3">
        <i class="bi bi-info-circle"></i>
        <div>Bookings cannot be edited after they are created - the booking service
          answers <code>bookings cannot be edited - cancel the booking and create a new one</code>.</div>
      </div>`;

    openModal('viewModal');
  }

  /* ------------------------------------------------------------ CREATE */

  function openForm() {
    const form = document.getElementById('bookingForm');
    resetForm(form);
    form.classList.remove('was-validated');

    document.getElementById('formTitle').textContent = 'New booking';
    document.getElementById('saveLabel').textContent = 'Create booking';
    document.getElementById('purposeCount').textContent = '0 / 255';

    const today = todayIso();
    const dateInput = document.getElementById('bookingDate');
    dateInput.min = today;
    dateInput.value = today;

    hideFormAlert();
    document.getElementById('availabilityResult').classList.add('d-none');

    openModal('formModal');
    setTimeout(() => document.getElementById('resource').focus(), 400);
  }

  function bindForm() {
    const form = document.getElementById('bookingForm');
    if (!form) return;

    /* stale answers are dropped as soon as the request changes */
    form.addEventListener('input', () => {
      document.getElementById('availabilityResult').classList.add('d-none');
      hideFormAlert();
      const purpose = document.getElementById('purpose');
      document.getElementById('purposeCount').textContent = purpose.value.length + ' / 255';
    });

    document.getElementById('checkAvailabilityBtn')
      .addEventListener('click', checkAvailability);

    form.addEventListener('submit', submitBooking);
  }

  /**
   * Slot rules shared by the availability check and the submission:
   * resource chosen, date today-or-later, end time after start time.
   */
  function validateSlot(form) {
    clearFieldErrors(form);
    let ok = true;

    if (!val('resource')) {
      markInvalid('resource', 'Please choose a resource.');
      ok = false;
    }

    const date = val('bookingDate');
    if (!date) {
      markInvalid('bookingDate', 'A booking date is required.');
      ok = false;
    } else if (date < todayIso()) {
      markInvalid('bookingDate', 'The booking date cannot be in the past.');
      ok = false;
    }

    const start = val('startTime');
    const end = val('endTime');
    if (!start) { markInvalid('startTime', 'A start time is required.'); ok = false; }
    if (!end) { markInvalid('endTime', 'An end time is required.'); ok = false; }
    if (start && end && end <= start) {
      markInvalid('endTime', 'The end time must be later than the start time.');
      ok = false;
    }

    if (!ok) form.classList.add('was-validated');
    else form.classList.remove('was-validated');
    return ok;
  }

  /** Client side rules of a booking - the server repeats all of them. */
  function validateForm(form) {
    let ok = validateSlot(form);

    const purpose = val('purpose');
    if (!purpose) {
      markInvalid('purpose', 'Tell others why you need this resource.');
      ok = false;
    } else if (purpose.length > 255) {
      markInvalid('purpose', 'The purpose must be at most 255 characters.');
      ok = false;
    }

    if (!ok) form.classList.add('was-validated');
    else form.classList.remove('was-validated');
    return ok;
  }

  async function checkAvailability() {
    const form = document.getElementById('bookingForm');
    if (!validateSlot(form)) {
      toastWarning('Complete the highlighted fields before checking availability.',
        'Check availability');
      return;
    }

    const [resourceType, resourceId] = splitResource(val('resource'));
    const payload = {
      resourceType,
      resourceId,
      date: val('bookingDate'),
      startTime: val('startTime'),
      endTime: val('endTime')
    };

    await withBusy(document.getElementById('checkAvailabilityBtn'), async () => {
      try {
        const answer = await api.post(API + '/availability', payload);
        renderAvailability(!!answer.available, answer.message || '');
      } catch (error) {
        renderAvailability(false, errorMessage(error, 'The availability could not be checked.'));
        toastError(errorMessage(error, 'The availability could not be checked.'));
      }
    }, 'Checking…');
  }

  /** Draws the answer of POST /api/bookings/availability (message verbatim). */
  function renderAvailability(available, message) {
    const box = document.getElementById('availabilityResult');
    box.classList.remove('d-none');
    box.innerHTML = `
      <div class="${available ? 'info-banner' : 'warning-banner'}">
        <i class="bi ${available ? 'bi-check2-circle' : 'bi-exclamation-triangle'}"></i>
        <div>
          <div class="fw-semibold mb-1">${available ? 'Slot free' : 'Slot already taken'}</div>
          <div>${escapeHtml(message)}</div>
        </div>
      </div>`;
  }

  async function submitBooking(event) {
    event.preventDefault();
    const form = event.currentTarget;
    hideFormAlert();

    if (!validateForm(form)) {
      toastWarning('Complete the highlighted fields - the booking was not created.',
        'Missing details');
      return;
    }

    const [resourceType, resourceId] = splitResource(val('resource'));
    const payload = {
      resourceType,
      resourceId,
      bookingDate: val('bookingDate'),
      startTime: val('startTime'),
      endTime: val('endTime'),
      purpose: val('purpose')
    };

    await withBusy(document.getElementById('saveBtn'), async () => {
      try {
        const created = await api.post(API, payload);
        toastSuccess(`Booking ${created.bookingRef} for ${created.resourceName} was created.`,
          'Booking created');
        closeModal('formModal');
        await table.refresh();
      } catch (error) {
        if (error.status === 409) {
          /* the server message names the conflicting booking - shown as-is */
          showFormAlert(error.message);
          toastError(error.message, 'Booking conflict');
        } else {
          if (error.errors) showFieldErrors(form, error.errors);
          if (error.message) showFormAlert(error.message);
          toastError(errorMessage(error, 'The booking could not be created.'));
        }
      }
    }, 'Saving…');
  }

  /* -------------------------------------------------------- STATUS MOVES */

  async function changeStatus(row, status, button) {
    const verbs = { APPROVED: 'Approve', REJECTED: 'Reject', COMPLETED: 'Complete' };
    const verb = verbs[status] || 'Update';

    const confirmed = await confirmDialog({
      title: `${verb} booking`,
      message: `${verb} ${row.bookingRef} for ${row.resourceName} on ${formatDate(row.bookingDate)}?`,
      confirmText: `Yes, ${verb.toLowerCase()}`,
      confirmClass: status === 'REJECTED' ? 'btn-danger' : 'btn-primary'
    });
    if (!confirmed) return;

    await withBusy(button, async () => {
      try {
        const updated = await api.put(`${API}/${row.id}/status`, { status });
        toastSuccess(`${updated.bookingRef} is now ${labelify(updated.status)}.`, 'Status updated');
        await table.refresh();
      } catch (error) {
        toastError(errorMessage(error, 'The status could not be changed.'));
      }
    }, 'Saving…');
  }

  async function cancelBooking(row, button) {
    const confirmed = await confirmDialog({
      title: 'Cancel booking',
      message: `Cancel ${row.bookingRef} for ${row.resourceName}? The time slot is released for everybody else.`,
      confirmText: 'Yes, cancel it',
      confirmClass: 'btn-danger'
    });
    if (!confirmed) return;

    await withBusy(button, async () => {
      try {
        const cancelled = await api.post(`${API}/${row.id}/cancel`);
        toastSuccess(`Booking ${cancelled.bookingRef} was cancelled.`, 'Booking cancelled');
        await table.refresh();
      } catch (error) {
        toastError(errorMessage(error, 'The booking could not be cancelled.'));
      }
    }, 'Cancelling…');
  }

  async function removeBooking(row) {
    const confirmed = await confirmDialog({
      title: 'Delete booking',
      message: `Delete ${row.bookingRef} (${row.resourceName})? This removes the row from the database and cannot be undone.`,
      confirmText: 'Yes, delete',
      confirmClass: 'btn-danger'
    });
    if (!confirmed) return;

    try {
      await api.del(`${API}/${row.id}`);
      toastSuccess(`Booking ${row.bookingRef} was deleted.`, 'Booking deleted');
      await table.refresh();
    } catch (error) {
      toastError(errorMessage(error, 'The booking could not be deleted.'));
    }
  }

  /* ============================================== CONCURRENCY DEMO (ADMIN) */

  async function initRacePanel() {
    const card = document.getElementById('raceCard');
    card.classList.remove('d-none');

    const today = todayIso();
    const dateInput = document.getElementById('raceDate');
    dateInput.min = today;
    dateInput.value = today;

    document.getElementById('raceAttempts').addEventListener('input', (event) => {
      document.getElementById('raceAttemptsValue').textContent = event.target.value;
    });
    document.getElementById('raceRun').addEventListener('click', runRaceTest);

    await Promise.all([loadRaceResources(), refreshRaceCount('raceCountBefore')]);
  }

  /** GET /api/concurrency/resources -> [{type,id,label}] */
  async function loadRaceResources() {
    const select = document.getElementById('raceResource');
    try {
      const options = await apiGet(CONCURRENCY_API + '/resources');
      fillResourceSelect(select, options, 'Select a resource', false);
      if (!select.options.length || select.options.length === 1) {
        select.innerHTML = '<option value="">No resources found</option>';
      }
    } catch (error) {
      select.innerHTML = '<option value="">Resources could not be loaded</option>';
      toastError(errorMessage(error, 'The race-test resources could not be loaded.'));
    }
  }

  /** GET /api/concurrency/count -> total number of bookings. */
  async function refreshRaceCount(targetId) {
    try {
      const count = await apiGet(CONCURRENCY_API + '/count');
      const el = document.getElementById(targetId);
      if (el) el.textContent = String(count);
      return count;
    } catch (error) {
      const el = document.getElementById(targetId);
      if (el) el.textContent = '—';
      toastError(errorMessage(error, 'The booking count could not be read.'));
      return null;
    }
  }

  async function runRaceTest() {
    const resourceValue = document.getElementById('raceResource').value;
    const date = document.getElementById('raceDate').value;
    const startTime = document.getElementById('raceStart').value;
    const endTime = document.getElementById('raceEnd').value;
    const attempts = parseInt(document.getElementById('raceAttempts').value, 10);

    if (!resourceValue) {
      toastWarning('Choose the resource every thread will compete for.', 'Missing resource');
      return;
    }
    if (!date || date < todayIso()) {
      toastWarning('Choose today or a future date.', 'Invalid date');
      return;
    }
    if (!startTime || !endTime) {
      toastWarning('Enter a start time and an end time.', 'Missing time');
      return;
    }
    if (endTime <= startTime) {
      toastWarning('The end time must be later than the start time.', 'Invalid time range');
      return;
    }
    if (!(attempts >= 2 && attempts <= 10)) {
      toastWarning('Choose between 2 and 10 threads.', 'Invalid thread count');
      return;
    }

    const [resourceType, resourceId] = splitResource(resourceValue);
    const payload = {
      resourceType,
      resourceId,
      date,
      startTime,
      endTime,
      purpose: 'Concurrency demonstration',
      attempts
    };

    await withBusy(document.getElementById('raceRun'), async () => {
      const resultEl = document.getElementById('raceResult');
      resultEl.innerHTML = loadingBlock('Starting the Java threads…');
      document.getElementById('raceCountAfter').textContent = '—';
      await refreshRaceCount('raceCountBefore');

      try {
        const result = await api.post(CONCURRENCY_API + '/run', payload);
        renderRaceResult(result);
        toastSuccess(result.summary || 'The race test finished.', 'Race test finished');
        await refreshRaceCount('raceCountAfter');
        if (table) await table.refresh();
      } catch (error) {
        const message = errorMessage(error, 'The concurrency test could not be run.');
        resultEl.innerHTML = `
          <div class="warning-banner">
            <i class="bi bi-exclamation-triangle"></i>
            <div>${escapeHtml(message)}</div>
          </div>`;
        toastError(message);
      }
    }, 'Running threads…');
  }

  /** Renders ConcurrencyTestResult: summary + one .thread-result per outcome. */
  function renderRaceResult(result) {
    const outcomes = Array.isArray(result.outcomes) ? result.outcomes : [];
    const rows = outcomes.map((line) => {
      const ok = String(line).indexOf('SUCCESS') !== -1;
      return `
        <div class="thread-result ${ok ? 'ok' : 'bad'}">
          <i class="bi ${ok ? 'bi-check2-circle' : 'bi-x-circle'}"></i>
          <div>${escapeHtml(line)}</div>
        </div>`;
    }).join('');

    document.getElementById('raceResult').innerHTML = `
      <div class="info-banner">
        <i class="bi bi-people-fill"></i>
        <div>
          <div class="fw-semibold mb-1">${escapeHtml(result.summary || '')}</div>
          <div class="small">
            Resource: ${escapeHtml(result.resourceLabel || '—')} ·
            Threads: ${escapeHtml(result.attempts)} ·
            Succeeded: ${escapeHtml(result.succeeded)} ·
            Rejected: ${escapeHtml(result.failed)} ·
            Elapsed: ${escapeHtml(result.elapsedMillis)} ms
          </div>
        </div>
      </div>
      <div class="small text-secondary fw-semibold mt-3 mb-1">
        Thread outcomes (${outcomes.length})
      </div>
      ${rows || '<div class="small text-muted">The server did not report any thread outcome.</div>'}`;
  }

  /* --------------------------------------------------------------- utils */

  /** "2026-10-03" in the browser's local time zone. */
  function todayIso() {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
  }

  /** "LABORATORY:2" -> ["LABORATORY", 2] */
  function splitResource(value) {
    const parts = String(value || '').split(':');
    const id = parseInt(parts[1], 10);
    return [parts[0] || '', isNaN(id) ? null : id];
  }

  function val(name) {
    const el = document.querySelector(`#bookingForm [name="${name}"]`);
    return el ? String(el.value).trim() : '';
  }

  function markInvalid(name, message) {
    const input = document.querySelector(`#bookingForm [name="${name}"]`);
    if (!input) return;
    input.classList.add('is-invalid');
    const feedback = input.parentElement.querySelector('.invalid-feedback');
    if (feedback) feedback.textContent = message;
  }

  function clearFieldErrors(form) {
    form.classList.remove('was-validated');
    form.querySelectorAll('.is-invalid, .is-valid').forEach((el) =>
      el.classList.remove('is-invalid', 'is-valid'));
    form.querySelectorAll('.invalid-feedback').forEach((el) => { el.textContent = ''; });
  }

  function showFormAlert(message) {
    const alert = document.getElementById('formAlert');
    alert.textContent = message;
    alert.classList.remove('d-none');
  }

  function hideFormAlert() {
    document.getElementById('formAlert').classList.add('d-none');
  }
})();
