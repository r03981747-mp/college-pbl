/* ============================================================================
 * smart-campus :: students.js
 * ----------------------------------------------------------------------------
 * The Students module.  This file is the TEMPLATE that every other module
 * (faculty, courses, classrooms...) follows:
 *
 *   1. guard the page  -> Auth.requireAuth()
 *   2. paint the shell -> AppShell.init(page, title)
 *   3. build a CrudTable (search + filters + sort + paging + row actions)
 *   4. wire the view / add / edit / delete modals to the REST API
 * ========================================================================== */

(function () {
  'use strict';

  const MODULE = 'students';
  const API = '/students';

  let table = null;
  let editingId = null;
  let viewingRow = null;

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('students.html', 'Students');

    /* role chip in the top bar (the shell only paints sidebar + topbar) */
    const roleChip = document.querySelector('.page-actions [data-user-role]');
    if (roleChip) roleChip.textContent = Auth.role() || '—';

    /* the Reports shortcut would only lead to an "access denied" panel */
    if (!Auth.can('reports.read')) {
      const reportsLink = document.querySelector('.page-actions a[href="reports.html"]');
      if (reportsLink) reportsLink.style.display = 'none';
    }

    /* students.read = "personal, read-only view".
       The register endpoint (GET /api/students) is annotated
       @RequireRoles({ADMIN, FACULTY}) and answers 403 for a student, so
       instead of rendering an error state we show the student their OWN
       record from GET /api/students/me.  Both branches below are only a UI
       convenience - the real gate is @RequireRoles on the controller. */
    if (!Auth.can('students.read')) {
      showDenied('Your account does not carry the <code>students.read</code> permission.');
      return;
    }

    if (Auth.role() === 'STUDENT') {
      await renderOwnRecord();
      return;
    }

    const canWrite = Auth.can('students.write');

    table = new CrudTable({
      mount: '#tableMount',
      apiPath: API,
      searchPlaceholder: 'Search by name, USN, email or phone…',
      emptyTitle: 'No students match your search',
      emptyDetail: 'Clear the filters, or add a new student.',
      createLabel: 'Add student',
      canCreate: canWrite,
      exportPath: Auth.can('reports.read') ? '/reports/students' : null,
      exportFilename: 'students.csv',
      pageSizes: [5, 10, 25, 50],

      filters: [
        { name: 'department', label: 'Department' },
        { name: 'semester', label: 'Semester' },
        { name: 'status', label: 'Status' }
      ],

      sortOptions: [
        { value: 'name:asc', label: 'Name (A → Z)' },
        { value: 'name:desc', label: 'Name (Z → A)' },
        { value: 'usn:asc', label: 'USN (ascending)' },
        { value: 'semester:asc', label: 'Semester (low → high)' },
        { value: 'semester:desc', label: 'Semester (high → low)' },
        { value: 'admissionYear:desc', label: 'Newest admissions' },
        { value: 'status:asc', label: 'Status' }
      ],
      defaultSort: 'name:asc',

      columns: [
        { key: 'usn', label: 'USN', render: (v) => codeBadge(v, 'bg-dark') },
        {
          key: 'name',
          label: 'Student',
          render: (v, row) => `
            <span class="cell-strong">${escapeHtml(v)}</span>
            <span class="cell-sub">${escapeHtml(row.email || '')}</span>`
        },
        { key: 'department', label: 'Department', hideMobile: false },
        {
          key: 'semester',
          label: 'Semester',
          render: (v, row) => `${escapeHtml(v)}${row.section ? ' · ' + escapeHtml(row.section) : ''}`
        },
        { key: 'phone', label: 'Phone', className: 'hide-mobile', render: (v) => escapeHtml(v || '—') },
        { key: 'status', label: 'Status', render: (v) => statusBadge(v) }
      ],

      onView: canWrite || Auth.role() === 'FACULTY' ? showView : null,
      onEdit: canWrite ? (row) => openForm(row) : null,
      onDelete: canWrite ? remove : null,
      onCreate: () => openForm(null)
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

  /** Reveals the built-in #accessDenied panel and hides the data table. */
  function showDenied(html) {
    const panel = document.getElementById('accessDenied');
    if (html) {
      const detail = panel.querySelector('.denial-detail');
      if (detail) detail.innerHTML = html;
    }
    panel.classList.remove('d-none');
    const mount = document.getElementById('tableMount');
    if (mount) mount.style.display = 'none';
  }

  /**
   * STUDENT branch: a personal, read-only view of one's own record.
   *
   * GET /api/students/me is resolved server-side from the session token, so a
   * student can never ask for somebody else's row - the register endpoint
   * itself still answers 403 for this role.
   */
  async function renderOwnRecord() {
    const mount = document.getElementById('tableMount');
    try {
      const me = await api.get('/students/me');
      if (!me) throw new Error('No student record is linked to this login account.');

      const pairs = [
        ['USN', codeBadge(me.usn, 'bg-dark')],
        ['Email', escapeHtml(me.email || '—')],
        ['Phone', escapeHtml(me.phone || '—')],
        ['Gender', labelify(me.gender)],
        ['Date of birth', formatDate(me.dateOfBirth)],
        ['Department', escapeHtml(me.department || '—')],
        ['Semester', escapeHtml(me.semester || '—')],
        ['Section', escapeHtml(me.section || '—')],
        ['Admission year', escapeHtml(me.admissionYear || '—')],
        ['Status', statusBadge(me.status)]
      ];

      mount.innerHTML = `
        <div class="card mb-3">
          <div class="card-body py-3">
            <div class="info-banner mb-0">
              <i class="bi bi-person-badge"></i>
              <div>
                <strong>Personal read-only view.</strong> Students see their own
                record only. The register endpoint <code>GET /api/students</code>
                is limited to administrators and faculty and answers
                <strong>403</strong> for this account.
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-body">
            <div class="d-flex align-items-center gap-3 flex-wrap mb-3">
              <span class="avatar-circle">${escapeHtml((me.name || '?').split(' ')
                .map((p) => p[0]).slice(0, 2).join('').toUpperCase())}</span>
              <div>
                <div class="fs-5 fw-semibold mb-0">${escapeHtml(me.name || '')}</div>
                <div class="small text-muted">${escapeHtml(me.usn || '')} · ${escapeHtml(me.department || '')}</div>
              </div>
              <button type="button" class="btn btn-outline-primary btn-sm ms-auto" id="ownViewBtn">
                <i class="bi bi-eye me-1"></i>View full details
              </button>
            </div>
            <div class="kv-list">
              ${pairs.map(([k, v]) => `<div class="kv-row"><span class="k">${k}</span><span class="v">${v}</span></div>`).join('')}
            </div>
          </div>
        </div>`;

      document.getElementById('ownViewBtn').addEventListener('click', () => showView(me));
    } catch (error) {
      showDenied(escapeHtml(error.message || 'We could not find a student record for this account.'));
    }
  }

  async function loadOptions() {
    try {
      const options = await apiGet(API + '/options');
      table.setFilterOptions('department', (options.departments || []).map((d) => ({ value: d, label: d })));

      const departmentSelect = document.getElementById('department');
      fillSelect(departmentSelect, (options.departments || []).map((d) => ({ value: d, label: d })),
        'Select department');
    } catch (error) {
      /* filters are optional - never block the page because of them */
      console.warn('Filter options could not be loaded:', error);
    }

    fillSelect(
      table.mountEl.querySelector('[data-filter="semester"]'),
      [1, 2, 3, 4, 5, 6, 7, 8].map((s) => ({ value: s, label: 'Semester ' + s })),
      'Semester: All'
    );
    fillSelect(
      table.mountEl.querySelector('[data-filter="status"]'),
      ['ACTIVE', 'INACTIVE', 'COMPLETED'].map((s) => ({ value: s, label: labelify(s) })),
      'Status: All'
    );
  }

  /* ---------------------------------------------------------------- VIEW */

  function showView(row) {
    viewingRow = row;
    document.getElementById('viewName').textContent = row.name || 'Student';
    document.getElementById('viewUsn').textContent = (row.usn || '') + ' · ' + (row.department || '');
    document.getElementById('viewInitials').textContent = (row.name || '?')
      .split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();

    const pairs = [
      ['USN / Roll number', codeBadge(row.usn, 'bg-dark')],
      ['Email', escapeHtml(row.email || '—')],
      ['Phone', escapeHtml(row.phone || '—')],
      ['Gender', labelify(row.gender)],
      ['Date of birth', formatDate(row.dateOfBirth)],
      ['Department', escapeHtml(row.department || '—')],
      ['Semester', escapeHtml(row.semester || '—')],
      ['Section', escapeHtml(row.section || '—')],
      ['Admission year', escapeHtml(row.admissionYear || '—')],
      ['Status', statusBadge(row.status)],
      ['Address', escapeHtml(row.address || '—')],
      ['Linked account', row.userId ? 'Yes (login enabled)' : 'Not linked']
    ];

    document.getElementById('viewBody').innerHTML = `
      <div class="kv-list">
        ${pairs.map(([k, v]) => `<div class="kv-row"><span class="k">${k}</span><span class="v">${v}</span></div>`).join('')}
      </div>`;

    const editBtn = document.getElementById('viewEditBtn');
    editBtn.style.display = Auth.can('students.write') ? '' : 'none';

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
    const form = document.getElementById('studentForm');
    resetForm(form);

    document.getElementById('formTitle').textContent = row ? 'Edit student' : 'Add student';
    document.getElementById('saveLabel').textContent = row ? 'Update student' : 'Save student';
    document.getElementById('admissionYear').value = new Date().getFullYear();
    document.getElementById('semester').value = 1;
    document.getElementById('status').value = 'ACTIVE';

    if (row) {
      ['usn', 'name', 'email', 'phone', 'gender', 'dateOfBirth', 'department',
       'semester', 'section', 'admissionYear', 'address', 'status'].forEach((field) => {
        const input = form.querySelector(`[name="${field}"]`);
        if (input && row[field] !== undefined && row[field] !== null) {
          input.value = row[field];
        }
      });
    }

    openModal('formModal');
    setTimeout(() => document.getElementById('usn').focus(), 400);
  }

  document.getElementById('studentForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));

    if (!form.checkValidity()) {
      form.classList.add('was-validated');
      return;
    }

    const payload = {
      usn: val('usn'),
      name: val('name'),
      email: val('email'),
      phone: val('phone') || null,
      gender: val('gender') || null,
      dateOfBirth: val('dateOfBirth') || null,
      department: val('department') || null,
      semester: parseInt(val('semester') || '1', 10),
      section: val('section') || null,
      admissionYear: parseInt(val('admissionYear') || '0', 10) || null,
      address: val('address') || null,
      status: val('status') || 'ACTIVE'
    };

    await withBusy(document.getElementById('saveBtn'), async () => {
      try {
        if (editingId) {
          const updated = await api.put(`${API}/${editingId}`, payload);
          toastSuccess(`${updated.name} was updated successfully.`, 'Student updated');
        } else {
          const created = await api.post(API, payload);
          toastSuccess(`${created.name} was added to the register (${created.usn}).`, 'Student added');
        }
        closeModal('formModal');
        await table.refresh();
      } catch (error) {
        if (error.errors) showFieldErrors(form, error.errors);
        toastError(errorMessage(error, 'The student could not be saved.'));
      }
    }, editingId ? 'Saving…' : 'Saving…');
  });

  /* ------------------------------------------------------------- DELETE */

  async function remove(row) {
    const confirmed = await confirmDialog({
      title: 'Delete student',
      message: `Delete ${row.name} (${row.usn})? This removes the record from the database and cannot be undone.`,
      confirmText: 'Yes, delete',
      confirmClass: 'btn-danger'
    });
    if (!confirmed) return;

    try {
      await api.del(`${API}/${row.id}`);
      toastSuccess(`${row.name} was deleted.`, 'Student deleted');
      await table.refresh();
    } catch (error) {
      toastError(errorMessage(error, 'The student could not be deleted.'));
    }
  }

  /* --------------------------------------------------------------- util */

  function val(name) {
    const el = document.querySelector(`#studentForm [name="${name}"]`);
    return el ? String(el.value).trim() : '';
  }
})();
