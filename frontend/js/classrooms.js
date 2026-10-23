/* ============================================================================
 * smart-campus :: classrooms.js
 * ----------------------------------------------------------------------------
 * The Classrooms module (rooms, seminar halls, auditoriums, conference rooms).
 *
 *   1. guard the page  -> Auth.requireAuth()
 *   2. paint the shell -> AppShell.init('classrooms.html', 'Classrooms')
 *   3. build a CrudTable (search + filters + sort + paging + row actions)
 *   4. wire the view / add / edit / delete modals to the REST API
 *   5. "Check availability" row action -> POST /api/classrooms/{id}/availability
 *
 * Endpoints used (ClassroomController):
 *   GET    /api/classrooms                    list (SearchCriteria)
 *   GET    /api/classrooms/options            buildings for the filter
 *   GET    /api/classrooms/{id}               one record
 *   POST   /api/classrooms                    create   (ADMIN)
 *   PUT    /api/classrooms/{id}               update   (ADMIN)
 *   DELETE /api/classrooms/{id}               delete   (ADMIN)
 *   POST   /api/classrooms/{id}/availability  "is it free?" check
 *   GET    /api/reports/classrooms            CSV export
 * ========================================================================== */

(function () {
  'use strict';

  const MODULE = 'classrooms';
  const API = '/classrooms';

  /** RoomType constants (Java enum names) + their getLabel() text. */
  const ROOM_TYPES = [
    { value: 'CLASSROOM', label: 'Classroom' },
    { value: 'SEMINAR_HALL', label: 'Seminar Hall' },
    { value: 'AUDITORIUM', label: 'Auditorium' },
    { value: 'CONFERENCE_ROOM', label: 'Conference Room' }
  ];

  /** ResourceStatus constants (Java enum names) + their getLabel() text. */
  const RESOURCE_STATUSES = [
    { value: 'ACTIVE', label: 'Active' },
    { value: 'MAINTENANCE', label: 'Under Maintenance' },
    { value: 'INACTIVE', label: 'Inactive' }
  ];

  let table = null;
  let editingId = null;
  let viewingRow = null;
  let availabilityRow = null;

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('classrooms.html', 'Classrooms');

    const canRead = Auth.can(MODULE + '.read');
    const canWrite = Auth.can(MODULE + '.write');
    const canExport = Auth.can('reports.read');

    const roleChip = document.querySelector('.page-actions [data-user-role]');
    if (roleChip) roleChip.textContent = Auth.role() || '—';
    if (!canExport) document.getElementById('reportsLinkBtn')?.classList.add('d-none');

    table = new CrudTable({
      mount: '#tableMount',
      apiPath: API,
      searchPlaceholder: 'Search by room number, building or facilities…',
      emptyTitle: 'No classrooms match your search',
      emptyDetail: 'Clear the filters, or add a new room.',
      createLabel: 'Add room',
      canCreate: canWrite,
      exportPath: canExport ? '/reports/classrooms' : null,
      exportFilename: 'classrooms.csv',
      pageSizes: [5, 10, 25, 50],

      filters: [
        { name: 'building', label: 'Building' },
        { name: 'type', label: 'Room type' },
        { name: 'status', label: 'Status' }
      ],

      /* only keys whitelisted in ClassroomRepository.SORT_COLUMNS */
      sortOptions: [
        { value: 'roomNumber:asc', label: 'Room number (A → Z)' },
        { value: 'roomNumber:desc', label: 'Room number (Z → A)' },
        { value: 'building:asc', label: 'Building (A → Z)' },
        { value: 'floor:asc', label: 'Floor (low → high)' },
        { value: 'floor:desc', label: 'Floor (high → low)' },
        { value: 'capacity:asc', label: 'Capacity (low → high)' },
        { value: 'capacity:desc', label: 'Capacity (high → low)' },
        { value: 'type:asc', label: 'Room type' },
        { value: 'status:asc', label: 'Status' }
      ],
      defaultSort: 'roomNumber:asc',

      columns: [
        {
          key: 'roomNumber',
          label: 'Room',
          render: (v, row) => `${codeBadge(v, 'bg-dark')}
            <span class="cell-sub">${escapeHtml(row.building || '—')}</span>`
        },
        { key: 'type', label: 'Type', render: (v) => escapeHtml(labelFor(ROOM_TYPES, v)) },
        { key: 'floor', label: 'Floor', className: 'hide-mobile', cellClass: 'hide-mobile' },
        {
          key: 'capacity',
          label: 'Capacity',
          className: 'hide-mobile',
          cellClass: 'hide-mobile',
          render: (v) => `${escapeHtml(v)} seat(s)`
        },
        {
          key: 'facilities',
          label: 'Facilities',
          className: 'hide-mobile',
          cellClass: 'hide-mobile',
          render: (v) => escapeHtml(v || '—')
        },
        { key: 'status', label: 'Status', render: (v) => statusFor(v) }
      ],

      customActions: [
        { key: 'availability', icon: 'bi-calendar2-check', title: 'Check availability', color: 'info' }
      ],
      onCustom: canRead
        ? (key, row) => { if (key === 'availability') openAvailability(row); }
        : null,

      onView: canRead ? showView : null,
      onEdit: canWrite ? (row) => openForm(row) : null,
      onDelete: canWrite ? remove : null,
      onCreate: canWrite ? () => openForm(null) : null
    });

    /* restore ?q= coming from the global search box */
    const q = new URLSearchParams(location.search).get('q');
    if (q) {
      const input = table.mountEl.querySelector('[data-toolbar="search"]');
      if (input) input.value = q;
      table.query.keyword = q;
    }

    await Promise.all([table.refresh(), loadOptions()]);
  }

  /* --------------------------------------------------------- filter lists */

  async function loadOptions() {
    try {
      const options = await apiGet(API + '/options');
      const buildings = options.buildings || [];
      table.setFilterOptions('building', buildings.map((b) => ({ value: b, label: b })),
        'Building: All');
      fillDatalist('buildingOptions', buildings);
    } catch (error) {
      /* filters are optional - never block the page because of them */
      console.warn('Filter options could not be loaded:', error);
    }

    fillSelect(
      table.mountEl.querySelector('[data-filter="type"]'),
      ROOM_TYPES.map((t) => ({ value: t.value, label: t.label })),
      'Room type: All'
    );
    fillSelect(
      table.mountEl.querySelector('[data-filter="status"]'),
      RESOURCE_STATUSES.map((s) => ({ value: s.value, label: s.label })),
      'Status: All'
    );
  }

  /* ---------------------------------------------------------------- VIEW */

  function showView(row) {
    viewingRow = row;
    document.getElementById('viewTitle').textContent = row.roomNumber || 'Room';
    document.getElementById('viewSubtitle').textContent =
      `${row.building || '—'} · Floor ${row.floor ?? '—'}`;
    document.getElementById('viewInitials').textContent = initialsOf(row.roomNumber);

    const pairs = [
      ['Room number', codeBadge(row.roomNumber, 'bg-dark')],
      ['Building', escapeHtml(row.building || '—')],
      ['Floor', escapeHtml(row.floor ?? '—')],
      ['Capacity', row.capacity ? `${escapeHtml(row.capacity)} seat(s)` : '—'],
      ['Room type', escapeHtml(labelFor(ROOM_TYPES, row.type))],
      ['Facilities', escapeHtml(row.facilities || '—')],
      ['Status', statusFor(row.status)],
      ['Created', formatDateTime(row.createdAt)]
    ];

    document.getElementById('viewBody').innerHTML = `
      <div class="kv-list">
        ${pairs.map(([k, v]) => `<div class="kv-row"><span class="k">${k}</span><span class="v">${v}</span></div>`).join('')}
      </div>`;

    document.getElementById('viewEditBtn').style.display =
      Auth.can(MODULE + '.write') ? '' : 'none';

    openModal('viewModal');
  }

  document.getElementById('viewEditBtn').addEventListener('click', () => {
    if (!viewingRow) return;
    closeModal('viewModal');
    setTimeout(() => openForm(viewingRow), 350);
  });

  /* ------------------------------------------------------------ ADD/EDIT */

  function openForm(row) {
    editingId = row ? row.id : null;
    const form = document.getElementById('classroomForm');
    resetForm(form);

    document.getElementById('formTitle').textContent = row ? 'Edit room' : 'Add room';
    document.getElementById('saveLabel').textContent = row ? 'Update room' : 'Save room';
    document.getElementById('floor').value = 1;
    document.getElementById('type').value = 'CLASSROOM';
    document.getElementById('status').value = 'ACTIVE';

    if (row) {
      ['roomNumber', 'building', 'floor', 'capacity', 'type', 'facilities', 'status']
        .forEach((field) => {
          const input = form.querySelector(`[name="${field}"]`);
          if (input && row[field] !== undefined && row[field] !== null) {
            input.value = row[field];
          }
        });
    }

    openModal('formModal');
    setTimeout(() => document.getElementById('roomNumber').focus(), 400);
  }

  document.getElementById('classroomForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;

    if (!applyInvalid(form)) {
      toastError('Complete the highlighted fields - the room was not saved.', 'Missing details');
      return;
    }

    const payload = {
      roomNumber: val('roomNumber'),
      building: val('building'),
      floor: parseInt(val('floor') || '0', 10),
      capacity: parseInt(val('capacity') || '0', 10),
      type: val('type'),
      facilities: val('facilities') || null,
      status: val('status') || 'ACTIVE'
    };

    await withBusy(document.getElementById('saveBtn'), async () => {
      try {
        if (editingId) {
          const updated = await api.put(`${API}/${editingId}`, payload);
          toastSuccess(`${updated.roomNumber} was updated.`, 'Room updated');
        } else {
          const created = await api.post(API, payload);
          toastSuccess(`${created.roomNumber} was added to the room register.`, 'Room added');
        }
        closeModal('formModal');
        await table.refresh();
        loadOptions();
      } catch (error) {
        if (error.errors) showFieldErrors(form, error.errors);
        toastError(errorMessage(error, 'The room could not be saved.'));
      }
    }, 'Saving…');
  });

  /* ------------------------------------------------------------- DELETE */

  async function remove(row) {
    const confirmed = await confirmDialog({
      title: 'Delete room',
      message: `Delete ${row.roomNumber} (${row.building || 'building not set'})? This removes the record from the database and cannot be undone.`,
      confirmText: 'Yes, delete',
      confirmClass: 'btn-danger'
    });
    if (!confirmed) return;

    try {
      await api.del(`${API}/${row.id}`);
      toastSuccess(`${row.roomNumber} was deleted.`, 'Room deleted');
      await table.refresh();
      loadOptions();
    } catch (error) {
      toastError(errorMessage(error, 'The room could not be deleted.'));
    }
  }

  /* ------------------------------------------------------- AVAILABILITY */

  function openAvailability(row) {
    availabilityRow = row;
    const form = document.getElementById('availabilityForm');
    resetForm(form);
    document.getElementById('availabilityResult').innerHTML = '';
    document.getElementById('availabilitySubtitle').textContent =
      `${row.roomNumber || ''} · ${row.building || ''}`.replace(/ · $/, '');

    openModal('availabilityModal');
    setTimeout(() => document.getElementById('availabilityDate').focus(), 400);
  }

  document.getElementById('availabilityForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;

    if (!applyInvalid(form)) {
      toastError('Enter a date, a start time and an end time - the availability was not checked.',
        'Missing details');
      return;
    }

    const date = valAv('date');
    const startTime = valAv('startTime');
    const endTime = valAv('endTime');

    if (endTime <= startTime) {
      markInvalid('availabilityEnd', 'The end time must be after the start time.');
      document.getElementById('availabilityEnd').focus();
      toastError('The end time must be after the start time.', 'Check the time range');
      return;
    }

    if (!availabilityRow) return;

    await withBusy(document.getElementById('availabilityBtn'), async () => {
      try {
        const result = await api.post(`${API}/${availabilityRow.id}/availability`, {
          date,
          startTime,
          endTime
        });
        renderAvailability(result);
      } catch (error) {
        document.getElementById('availabilityResult').innerHTML = '';
        toastError(errorMessage(error, 'The availability could not be checked.'));
      }
    }, 'Checking…');
  });

  /** Green / red banner carrying the server message verbatim. */
  function renderAvailability(result) {
    const box = document.getElementById('availabilityResult');
    if (!box) return;

    const available = !!(result && result.available);
    const message = (result && result.message) || 'The server did not return a message.';

    let conflict = '';
    if (result && result.conflictingBookingRef) {
      const timeWindow = result.conflictStart && result.conflictEnd
        ? ` · ${formatTime(result.conflictStart)} to ${formatTime(result.conflictEnd)}`
        : '';
      conflict = `<div class="small mt-1">Conflicting booking:
        <strong>${escapeHtml(result.conflictingBookingRef)}</strong>${escapeHtml(timeWindow)}</div>`;
    }

    box.innerHTML = `
      <div class="alert ${available ? 'alert-success' : 'alert-danger'} mb-0" role="status">
        <div class="d-flex align-items-start gap-2">
          <i class="bi ${available ? 'bi-check-circle-fill' : 'bi-x-octagon-fill'} fs-5"></i>
          <div>
            <div class="fw-semibold">${available ? 'Slot available' : 'Slot not available'}</div>
            <div>${escapeHtml(message)}</div>
            ${conflict}
          </div>
        </div>
      </div>`;
  }

  /* --------------------------------------------------------------- util */

  function val(name) {
    const el = document.querySelector(`#classroomForm [name="${name}"]`);
    return el ? String(el.value).trim() : '';
  }

  function valAv(name) {
    const el = document.querySelector(`#availabilityForm [name="${name}"]`);
    return el ? String(el.value).trim() : '';
  }

  function labelFor(list, value) {
    const found = list.find((item) => item.value === value);
    return found ? found.label : labelify(value);
  }

  function statusFor(value) {
    return statusBadge(value, labelFor(RESOURCE_STATUSES, value));
  }

  function initialsOf(value) {
    const parts = String(value || '?')
      .replace(/[^A-Za-z0-9 ]/g, ' ')
      .split(/\s+/).filter(Boolean);
    return (parts.length ? parts : ['?'])
      .map((p) => p.charAt(0)).slice(0, 2).join('').toUpperCase() || '?';
  }

  function fillDatalist(id, values) {
    const list = document.getElementById(id);
    if (!list) return;
    list.innerHTML = (values || []).filter(Boolean)
      .map((v) => `<option value="${escapeHtml(v)}"></option>`).join('');
  }

  /** Marks every invalid control of a form and shows a clear message. */
  function applyInvalid(form) {
    let firstInvalid = null;
    form.querySelectorAll('input, select, textarea').forEach((field) => {
      const feedback = field.parentElement
        ? field.parentElement.querySelector('.invalid-feedback')
        : null;

      if (field.validity.valid) {
        field.classList.remove('is-invalid');
        if (feedback) feedback.textContent = '';
        return;
      }

      field.classList.add('is-invalid');
      if (feedback && !feedback.textContent) {
        feedback.textContent = field.dataset.msg || 'This field is required.';
      }
      if (!firstInvalid) firstInvalid = field;
    });

    if (firstInvalid) firstInvalid.focus();
    return !firstInvalid;
  }

  function markInvalid(id, message) {
    const field = document.getElementById(id);
    if (!field) return;
    field.classList.add('is-invalid');
    const feedback = field.parentElement
      ? field.parentElement.querySelector('.invalid-feedback')
      : null;
    if (feedback) feedback.textContent = message;
  }
})();
