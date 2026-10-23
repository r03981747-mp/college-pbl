/* ============================================================================
 * smart-campus :: equipment.js
 * ----------------------------------------------------------------------------
 * The Equipment module (same shape as students.js):
 *
 *   1. guard the page  -> Auth.requireAuth()
 *   2. paint the shell -> AppShell.init('equipment.html', 'Equipment')
 *   3. build a CrudTable (search + filters + sort + paging + row actions)
 *   4. CRUD against /api/equipment plus three real quick actions:
 *        PUT  /api/equipment/{id}/condition    {"condition": "..."}
 *        PUT  /api/equipment/{id}/quantity     {"quantity": 3}
 *        POST /api/equipment/{id}/availability {"date","startTime","endTime"}
 * ========================================================================== */

(function () {
  'use strict';

  const MODULE = 'equipment';
  const API = '/equipment';

  /** EquipmentCondition constants - exactly the Java enum names. */
  const CONDITIONS = [
    { value: 'NEW', label: 'New' },
    { value: 'GOOD', label: 'Good' },
    { value: 'MAINTENANCE_REQUIRED', label: 'Maintenance required' },
    { value: 'DAMAGED', label: 'Damaged' }
  ];

  /** ResourceStatus constants - exactly the Java enum names. */
  const STATUSES = ['ACTIVE', 'MAINTENANCE', 'INACTIVE'];

  let table = null;
  let editingId = null;
  let viewingRow = null;
  let actionRow = null;   /* row used by the quick-action modals */

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('equipment.html', 'Equipment');

    const canWrite = Auth.can('equipment.write');
    const canRead = Auth.can('equipment.read');
    const canExport = Auth.can('reports.read');

    const roleChip = document.querySelector('.page-actions [data-user-role]');
    if (roleChip) roleChip.textContent = Auth.role() || '—';
    if (!canExport) document.getElementById('reportsLinkBtn')?.classList.add('d-none');

    /* Quick actions. Condition / quantity write to the database, so they are
       gated with equipment.write; the availability check is read-only. */
    const customActions = [];
    if (canWrite) {
      customActions.push(
        { key: 'condition', title: 'Update condition', icon: 'bi-clipboard2-pulse', color: 'warning' },
        { key: 'quantity', title: 'Adjust quantity', icon: 'bi-arrow-left-right', color: 'info' });
    }
    if (canRead) {
      customActions.push(
        { key: 'availability', title: 'Check availability', icon: 'bi-calendar2-check', color: 'success' });
    }

    table = new CrudTable({
      mount: '#tableMount',
      apiPath: API,
      searchPlaceholder: 'Search by name, serial number, category or location…',
      emptyTitle: 'No equipment matches your search',
      emptyDetail: 'Clear the filters, or add a new item to the register.',
      createLabel: 'Add equipment',
      canCreate: canWrite,
      exportPath: canExport ? '/reports/equipment' : null,
      exportFilename: 'equipment.csv',
      pageSizes: [5, 10, 25, 50],

      filters: [
        { name: 'category', label: 'Category' },
        { name: 'location', label: 'Location' },
        { name: 'condition', label: 'Condition' },
        { name: 'status', label: 'Status' }
      ],

      /* only keys whitelisted in EquipmentRepository.SORT_COLUMNS */
      sortOptions: [
        { value: 'equipmentName:asc', label: 'Name (A → Z)' },
        { value: 'equipmentName:desc', label: 'Name (Z → A)' },
        { value: 'serialNumber:asc', label: 'Serial number (ascending)' },
        { value: 'category:asc', label: 'Category (A → Z)' },
        { value: 'quantity:desc', label: 'Quantity (high → low)' },
        { value: 'quantity:asc', label: 'Quantity (low → high)' },
        { value: 'location:asc', label: 'Location (A → Z)' },
        { value: 'condition:asc', label: 'Condition' },
        { value: 'purchaseDate:desc', label: 'Newest purchases' },
        { value: 'status:asc', label: 'Status' }
      ],
      defaultSort: 'equipmentName:asc',

      columns: [
        { key: 'serialNumber', label: 'Code', render: (v) => codeBadge(v, 'bg-dark') },
        {
          key: 'equipmentName',
          label: 'Equipment',
          render: (v, row) => `
            <span class="cell-strong">${escapeHtml(v)}</span>
            <span class="cell-sub">${escapeHtml(row.category || '')}</span>`
        },
        {
          key: 'location',
          label: 'Location',
          className: 'hide-mobile',
          cellClass: 'hide-mobile',
          render: (v) => escapeHtml(v || '—')
        },
        {
          key: 'quantity',
          label: 'Quantity',
          render: (v, row) => `
            <span class="cell-strong">${escapeHtml(v)}</span>
            ${row.availableQuantity !== undefined && row.availableQuantity !== null
              ? `<span class="cell-sub">${escapeHtml(row.availableQuantity)} available</span>` : ''}`
        },
        { key: 'condition', label: 'Condition', render: (v) => statusBadge(v) },
        { key: 'status', label: 'Status', render: (v) => statusBadge(v) }
      ],

      onView: showView,
      onEdit: canWrite ? (row) => openForm(row) : null,
      onDelete: canWrite ? (row) => remove(row) : null,
      onCreate: () => openForm(null),
      customActions: customActions,
      onCustom: handleCustom
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
      table.setFilterOptions('category',
        (options.categories || []).map((c) => ({ value: c, label: c })));
      table.setFilterOptions('location',
        (options.locations || []).map((l) => ({ value: l, label: l })));

      fillDatalist('categoryList', options.categories);
      fillDatalist('locationList', options.locations);
    } catch (error) {
      /* filters are optional - never block the page because of them */
      console.warn('Filter options could not be loaded:', error);
    }

    fillSelect(
      table.mountEl.querySelector('[data-filter="condition"]'),
      CONDITIONS.map((c) => ({ value: c.value, label: c.label })),
      'Condition: All'
    );
    fillSelect(
      table.mountEl.querySelector('[data-filter="status"]'),
      STATUSES.map((s) => ({ value: s, label: labelify(s) })),
      'Status: All'
    );
  }

  function fillDatalist(id, values) {
    const list = document.getElementById(id);
    if (!list) return;
    list.innerHTML = (values || [])
      .map((v) => `<option value="${escapeHtml(v)}"></option>`).join('');
  }

  /* ---------------------------------------------------------------- VIEW */

  function showView(row) {
    viewingRow = row;
    document.getElementById('viewName').textContent = row.equipmentName || 'Equipment';
    document.getElementById('viewCode').textContent =
      (row.serialNumber || '') + ' · ' + (row.category || '');
    document.getElementById('viewInitials').textContent = (row.equipmentName || '?')
      .split(/\s+/).filter(Boolean).map((p) => p[0]).slice(0, 2).join('').toUpperCase();

    const pairs = [
      ['Serial number', codeBadge(row.serialNumber, 'bg-dark')],
      ['Equipment name', escapeHtml(row.equipmentName || '—')],
      ['Category', escapeHtml(row.category || '—')],
      ['Location', escapeHtml(row.location || '—')],
      ['Quantity', escapeHtml(row.quantity)],
      ['Available quantity', escapeHtml(row.availableQuantity !== undefined
        && row.availableQuantity !== null ? row.availableQuantity : '—')],
      ['Purchase date', formatDate(row.purchaseDate)],
      ['Condition', statusBadge(row.condition)],
      ['Status', statusBadge(row.status)],
      ['Description', escapeHtml(row.description || '—')],
      ['Created on', formatDateTime(row.createdAt)]
    ];

    document.getElementById('viewBody').innerHTML = `
      <div class="kv-list">
        ${pairs.map(([k, v]) => `<div class="kv-row"><span class="k">${k}</span><span class="v">${v}</span></div>`).join('')}
      </div>`;

    document.getElementById('viewEditBtn').style.display =
      Auth.can('equipment.write') ? '' : 'none';

    openModal('viewModal');
  }

  document.getElementById('viewEditBtn').addEventListener('click', () => {
    if (!viewingRow) return;
    closeModal('viewModal');
    setTimeout(() => openForm(viewingRow), 350);
  });

  /* ------------------------------------------------------------ ADD/EDIT */

  function openForm(row) {
    if (!Auth.can('equipment.write')) return;
    editingId = row ? row.id : null;
    const form = document.getElementById('equipmentForm');
    resetForm(form);

    document.getElementById('formTitle').textContent = row ? 'Edit equipment' : 'Add equipment';
    document.getElementById('saveLabel').textContent = row ? 'Update equipment' : 'Save equipment';
    document.getElementById('quantity').value = 1;
    document.getElementById('condition').value = 'NEW';
    document.getElementById('status').value = 'ACTIVE';
    /* the server rejects purchase dates in the future */
    document.getElementById('purchaseDate').max = new Date().toISOString().slice(0, 10);

    if (row) {
      ['serialNumber', 'equipmentName', 'category', 'quantity', 'location',
       'purchaseDate', 'condition', 'status', 'description'].forEach((field) => {
        const input = form.querySelector(`[name="${field}"]`);
        if (input && row[field] !== undefined && row[field] !== null) {
          input.value = row[field];
        }
      });
    }

    openModal('formModal');
    setTimeout(() => document.getElementById('serialNumber').focus(), 400);
  }

  document.getElementById('equipmentForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));

    if (!form.checkValidity()) {
      form.classList.add('was-validated');
      return;
    }

    const payload = {
      serialNumber: field('serialNumber'),
      equipmentName: field('equipmentName'),
      category: field('category'),
      quantity: parseInt(field('quantity') || '0', 10),
      location: field('location'),
      purchaseDate: field('purchaseDate') || null,
      condition: field('condition') || 'NEW',
      status: field('status') || 'ACTIVE',
      description: field('description') || null
    };

    await withBusy(document.getElementById('saveBtn'), async () => {
      try {
        if (editingId) {
          const updated = await api.put(`${API}/${editingId}`, payload);
          toastSuccess(`${updated.equipmentName} was updated.`, 'Equipment updated');
        } else {
          const created = await api.post(API, payload);
          toastSuccess(`${created.equipmentName} (${created.serialNumber}) was added.`, 'Equipment added');
        }
        closeModal('formModal');
        await table.refresh();
      } catch (error) {
        if (error.errors) showFieldErrors(form, error.errors);
        toastError(errorMessage(error, 'The equipment record could not be saved.'));
      }
    }, 'Saving…');
  });

  /* ------------------------------------------------------------- DELETE */

  async function remove(row) {
    const confirmed = await confirmDialog({
      title: 'Delete equipment',
      message: `Delete ${row.equipmentName} (${row.serialNumber})? This removes the record from the database and cannot be undone.`,
      confirmText: 'Yes, delete',
      confirmClass: 'btn-danger'
    });
    if (!confirmed) return;

    try {
      await api.del(`${API}/${row.id}`);
      toastSuccess(`${row.equipmentName} was deleted.`, 'Equipment deleted');
      await table.refresh();
    } catch (error) {
      toastError(errorMessage(error, 'The equipment record could not be deleted.'));
    }
  }

  /* ------------------------------------------------------- QUICK ACTIONS */

  function handleCustom(key, row) {
    if (key === 'condition') {
      if (!Auth.can('equipment.write')) return;
      openCondition(row);
    }
    if (key === 'quantity') {
      if (!Auth.can('equipment.write')) return;
      openQuantity(row);
    }
    if (key === 'availability') openAvailability(row);
  }

  /* ---- 1. PUT /api/equipment/{id}/condition ---------------------------- */

  function openCondition(row) {
    actionRow = row;
    document.getElementById('conditionHint').textContent =
      `${row.equipmentName} (${row.serialNumber}) is currently ${labelify(row.condition).toLowerCase()}.`;
    document.getElementById('conditionSelect').value = row.condition || 'NEW';
    openModal('conditionModal');
  }

  document.getElementById('conditionForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!actionRow) return;
    const condition = document.getElementById('conditionSelect').value;
    if (!condition) return;

    await withBusy(document.getElementById('conditionSaveBtn'), async () => {
      try {
        const updated = await api.put(`${API}/${actionRow.id}/condition`, { condition });
        toastSuccess(
          `${updated.equipmentName} is now ${labelify(updated.condition).toLowerCase()}.`,
          'Condition updated');
        closeModal('conditionModal');
        await table.refresh();
      } catch (error) {
        toastError(errorMessage(error, 'The condition could not be updated.'));
      }
    }, 'Saving…');
  });

  /* ---- 2. PUT /api/equipment/{id}/quantity ----------------------------- */

  function openQuantity(row) {
    actionRow = row;
    const input = document.getElementById('quantityInput');
    const form = document.getElementById('quantityForm');
    form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));
    form.classList.remove('was-validated');

    document.getElementById('quantityHint').textContent =
      `${row.equipmentName} (${row.serialNumber}) currently has ${row.quantity} unit(s) in stock.`;
    input.value = row.quantity;
    openModal('quantityModal');
    setTimeout(() => input.focus(), 400);
  }

  document.getElementById('quantityForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!actionRow) return;

    const input = document.getElementById('quantityInput');
    const quantity = parseInt(input.value, 10);
    if (isNaN(quantity) || quantity < 0 || quantity > 100000) {
      input.classList.add('is-invalid');
      return;
    }

    await withBusy(document.getElementById('quantitySaveBtn'), async () => {
      try {
        const updated = await api.put(`${API}/${actionRow.id}/quantity`, { quantity });
        toastSuccess(
          `${updated.equipmentName} now has ${updated.quantity} unit(s) in stock.`,
          'Quantity updated');
        closeModal('quantityModal');
        await table.refresh();
      } catch (error) {
        if (error.errors) showFieldErrors(document.getElementById('quantityForm'), error.errors);
        toastError(errorMessage(error, 'The quantity could not be updated.'));
      }
    }, 'Saving…');
  });

  /* ---- 3. POST /api/equipment/{id}/availability ------------------------ */

  function openAvailability(row) {
    actionRow = row;
    const form = document.getElementById('availabilityForm');
    resetForm(form);

    document.getElementById('availabilityName').textContent =
      `${row.equipmentName} (${row.serialNumber})`;
    document.getElementById('availabilityDate').value = new Date().toISOString().slice(0, 10);
    hideAvailabilityResult();

    openModal('availabilityModal');
  }

  function hideAvailabilityResult() {
    const box = document.getElementById('availabilityResult');
    box.className = 'alert d-none mt-3 mb-0';
    box.innerHTML = '';
  }

  function showAvailabilityResult(available, message, ref) {
    const box = document.getElementById('availabilityResult');
    box.className = `alert ${available ? 'alert-success' : 'alert-danger'} mt-3 mb-0`;
    box.innerHTML = `
      <i class="bi ${available ? 'bi-check-circle-fill' : 'bi-x-octagon-fill'} me-1"></i>
      <strong>${available ? 'Available' : 'Not available'}</strong>
      <div class="small mt-1">${escapeHtml(message)}</div>
      ${ref ? `<div class="small mt-1">Conflicting booking: <code>${escapeHtml(ref)}</code></div>` : ''}`;
  }

  document.getElementById('availabilityForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!actionRow) return;

    const form = event.currentTarget;
    form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));

    const payload = {
      date: field('date', 'availabilityForm'),
      startTime: field('startTime', 'availabilityForm'),
      endTime: field('endTime', 'availabilityForm')
    };

    if (!payload.date || !payload.startTime || !payload.endTime) {
      form.classList.add('was-validated');
      return;
    }
    if (payload.endTime <= payload.startTime) {
      document.getElementById('availabilityEnd').classList.add('is-invalid');
      toastError('The end time must be after the start time.', 'Check the time window');
      return;
    }

    await withBusy(document.getElementById('availabilityCheckBtn'), async () => {
      try {
        const result = await api.post(`/equipment/${actionRow.id}/availability`, payload);
        showAvailabilityResult(!!result.available, result.message || 'No answer from the server.',
          result.conflictingBookingRef);
      } catch (error) {
        showAvailabilityResult(false, errorMessage(error, 'The availability check failed.'), null);
      }
    }, 'Checking…');
  });

  /* --------------------------------------------------------------- util */

  /** Reads one named field of a form in this page. */
  function field(name, formId) {
    const form = document.getElementById(formId || 'equipmentForm');
    const el = form ? form.querySelector(`[name="${name}"]`) : null;
    return el ? String(el.value).trim() : '';
  }
})();
