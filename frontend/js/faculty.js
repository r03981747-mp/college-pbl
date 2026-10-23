/* ============================================================================
 * smart-campus :: faculty.js
 * ----------------------------------------------------------------------------
 * The Faculty module - same shape as students.js:
 *
 *   1. guard the page  -> Auth.requireAuth()
 *   2. paint the shell -> AppShell.init('faculty.html', 'Faculty')
 *   3. build a CrudTable (search + filters + sort + paging + row actions)
 *   4. wire the view / add / edit / delete modals to the REST API
 *
 * Endpoints used (FacultyController):
 *   GET    /api/faculty            list + search + filter + sort + page
 *   GET    /api/faculty/options    departments / designations for the filters
 *   GET    /api/faculty/{id}       one record
 *   POST   /api/faculty            create
 *   PUT    /api/faculty/{id}       update
 *   DELETE /api/faculty/{id}       delete
 * ========================================================================== */

(function () {
  'use strict';

  const MODULE = 'faculty';
  const API = '/faculty';

  /** FacultyStatus enum - the exact constants the backend deserialises. */
  const STATUS_VALUES = ['ACTIVE', 'ON_LEAVE', 'INACTIVE'];

  let table = null;
  let editingId = null;
  let viewingRow = null;

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('faculty.html', 'Faculty');

    /* the header chip is filled by the page itself (the shell only paints
       the sidebar / topbar) */
    const roleChip = document.querySelector('.page-actions [data-user-role]');
    if (roleChip) roleChip.textContent = Auth.role() || '—';

    const canRead = Auth.can('faculty.read');
    const canWrite = Auth.can('faculty.write');

    /* STUDENT has no faculty.read - the API would answer 403 anyway */
    if (!canRead) {
      document.getElementById('accessDenied').classList.remove('d-none');
      const mount = document.getElementById('tableMount');
      if (mount) mount.style.display = 'none';
      return;
    }

    table = new CrudTable({
      mount: '#tableMount',
      apiPath: API,
      searchPlaceholder: 'Search by name, employee ID, e-mail, department or designation…',
      emptyTitle: 'No faculty members match your search',
      emptyDetail: 'Clear the filters, or add a new faculty member.',
      createLabel: 'Add faculty member',
      canCreate: canWrite,
      exportPath: Auth.can('reports.read') ? '/reports/faculty' : null,
      exportFilename: 'faculty.csv',
      pageSizes: [5, 10, 25, 50],

      filters: [
        { name: 'department', label: 'Department' },
        { name: 'designation', label: 'Designation' },
        { name: 'status', label: 'Status' }
      ],

      /* only keys whitelisted in FacultyRepository.SORT_COLUMNS are accepted */
      sortOptions: [
        { value: 'name:asc', label: 'Name (A → Z)' },
        { value: 'name:desc', label: 'Name (Z → A)' },
        { value: 'employeeId:asc', label: 'Employee ID (A → Z)' },
        { value: 'email:asc', label: 'Email (A → Z)' },
        { value: 'designation:asc', label: 'Designation (A → Z)' },
        { value: 'department:asc', label: 'Department (A → Z)' },
        { value: 'joiningDate:asc', label: 'Experience (longest first)' },
        { value: 'joiningDate:desc', label: 'Experience (shortest first)' },
        { value: 'status:asc', label: 'Status' }
      ],
      defaultSort: 'name:asc',

      columns: [
        { key: 'employeeId', label: 'Employee ID', render: (v) => codeBadge(v, 'bg-dark') },
        {
          key: 'name',
          label: 'Faculty member',
          render: (v, row) => `
            <span class="cell-strong">${escapeHtml(v)}</span>
            <span class="cell-sub">${escapeHtml(row.email || '')}</span>`
        },
        { key: 'department', label: 'Department' },
        { key: 'designation', label: 'Designation', cellClass: 'hide-mobile', className: 'hide-mobile' },
        {
          key: 'joiningDate',
          label: 'Experience',
          render: (v) => experienceCell(v)
        },
        { key: 'status', label: 'Status', render: (v) => statusBadge(v) }
      ],

      onView: canRead ? (row) => showView(row, canWrite) : null,
      onEdit: canWrite ? (row) => openForm(row) : null,
      onDelete: canWrite ? (row) => remove(row) : null,
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
      const departments = (options.departments || []).map((d) => ({ value: d, label: d }));
      const designations = (options.designations || []).map((d) => ({ value: d, label: d }));

      table.setFilterOptions('department', departments);
      table.setFilterOptions('designation', designations);

      /* the add / edit form uses free text + datalist so the very first
         faculty member can still be created in an empty database */
      fillDatalist('departmentList', departments);
      fillDatalist('designationList', designations);
    } catch (error) {
      /* filters are optional - never block the page because of them */
      console.warn('Filter options could not be loaded:', error);
    }

    fillSelect(
      table.mountEl.querySelector('[data-filter="status"]'),
      STATUS_VALUES.map((s) => ({ value: s, label: labelify(s) })),
      'Status: All'
    );
  }

  function fillDatalist(id, options) {
    const list = document.getElementById(id);
    if (!list) return;
    list.innerHTML = options
      .map((o) => `<option value="${escapeHtml(o.value)}"></option>`)
      .join('');
  }

  /* ---------------------------------------------------------------- VIEW */

  function showView(row, canEdit) {
    viewingRow = row;
    document.getElementById('viewName').textContent = row.name || 'Faculty member';
    document.getElementById('viewMeta').textContent =
      (row.employeeId || '') + ' · ' + (row.department || '');
    document.getElementById('viewInitials').textContent = (row.name || '?')
      .split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();

    const years = yearsOfService(row.joiningDate);
    const pairs = [
      ['Record ID', escapeHtml(row.id ?? '—')],
      ['Employee ID', codeBadge(row.employeeId, 'bg-dark')],
      ['Email', escapeHtml(row.email || '—')],
      ['Phone', escapeHtml(row.phone || '—')],
      ['Department', escapeHtml(row.department || '—')],
      ['Designation', escapeHtml(row.designation || '—')],
      ['Qualification', escapeHtml(row.qualification || '—')],
      ['Joining date', formatDate(row.joiningDate)],
      ['Years of service', years === null ? '—' : years],
      ['Status', statusBadge(row.status)],
      ['Linked account', row.userId ? 'User #' + escapeHtml(row.userId) : 'Not linked'],
      ['Created at', formatDateTime(row.createdAt)]
    ];

    document.getElementById('viewBody').innerHTML = `
      <div class="kv-list">
        ${pairs.map(([k, v]) => `<div class="kv-row"><span class="k">${k}</span><span class="v">${v}</span></div>`).join('')}
      </div>`;

    const editBtn = document.getElementById('viewEditBtn');
    editBtn.style.display = canEdit ? '' : 'none';

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
    const form = document.getElementById('facultyForm');
    resetForm(form);

    document.getElementById('formTitle').textContent = row ? 'Edit faculty member' : 'Add faculty member';
    document.getElementById('saveLabel').textContent = row ? 'Update faculty member' : 'Save faculty member';

    /* sensible defaults: joining today, status active */
    document.getElementById('joiningDate').value = todayIso();
    document.getElementById('status').value = 'ACTIVE';

    if (row) {
      ['employeeId', 'name', 'email', 'phone', 'department', 'designation',
       'qualification', 'joiningDate', 'status'].forEach((field) => {
        const input = form.querySelector(`[name="${field}"]`);
        if (input && row[field] !== undefined && row[field] !== null) {
          input.value = row[field];
        }
      });
    }

    openModal('formModal');
    setTimeout(() => document.getElementById('employeeId').focus(), 400);
  }

  document.getElementById('facultyForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));

    if (!form.checkValidity()) {
      form.classList.add('was-validated');
      return;
    }

    const payload = {
      employeeId: val('employeeId'),
      name: val('name'),
      email: val('email'),
      phone: val('phone'),
      department: val('department'),
      designation: val('designation'),
      qualification: val('qualification') || null,
      joiningDate: val('joiningDate') || null,
      status: val('status') || 'ACTIVE'
    };

    await withBusy(document.getElementById('saveBtn'), async () => {
      try {
        if (editingId) {
          const updated = await api.put(`${API}/${editingId}`, payload);
          toastSuccess(`${updated.name} was updated.`, 'Faculty updated');
        } else {
          const created = await api.post(API, payload);
          toastSuccess(`${created.name} was added to the register (${created.employeeId}).`, 'Faculty added');
        }
        closeModal('formModal');
        await table.refresh();
      } catch (error) {
        if (error.errors) showFieldErrors(form, error.errors);
        toastError(errorMessage(error, 'The faculty record could not be saved.'));
      }
    }, 'Saving…');
  });

  /* ------------------------------------------------------------- DELETE */

  async function remove(row) {
    const confirmed = await confirmDialog({
      title: 'Delete faculty member',
      message: `Delete ${row.name} (${row.employeeId})? This removes the record from the database and cannot be undone.`,
      confirmText: 'Yes, delete',
      confirmClass: 'btn-danger'
    });
    if (!confirmed) return;

    try {
      await api.del(`${API}/${row.id}`);
      toastSuccess(`${row.name} was deleted.`, 'Faculty deleted');
      await table.refresh();
    } catch (error) {
      toastError(errorMessage(error, 'The faculty record could not be deleted.'));
    }
  }

  /* --------------------------------------------------------------- util */

  function val(name) {
    const el = document.querySelector(`#facultyForm [name="${name}"]`);
    return el ? String(el.value).trim() : '';
  }

  function todayIso() {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
  }

  /** Completed years between the joining date and today (null when unknown). */
  function yearsOfService(joiningDate) {
    if (!joiningDate) return null;
    const start = new Date(String(joiningDate).slice(0, 10) + 'T00:00:00');
    if (isNaN(start.getTime())) return null;
    const now = new Date();
    let years = now.getFullYear() - start.getFullYear();
    const monthGap = now.getMonth() - start.getMonth();
    if (monthGap < 0 || (monthGap === 0 && now.getDate() < start.getDate())) years -= 1;
    return Math.max(years, 0);
  }

  /** "5 yrs" + "since 12 Jun 2019", or a dash when there is no joining date. */
  function experienceCell(joiningDate) {
    const years = yearsOfService(joiningDate);
    if (years === null) return '<span class="text-secondary">—</span>';
    return `
      <span class="cell-strong">${years} yr${years === 1 ? '' : 's'}</span>
      <span class="cell-sub">since ${escapeHtml(formatDate(joiningDate))}</span>`;
  }
})();
