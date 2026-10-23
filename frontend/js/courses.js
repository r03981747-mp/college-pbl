/* ============================================================================
 * smart-campus :: courses.js
 * ----------------------------------------------------------------------------
 * The Courses module - same shape as students.js, plus the extra
 * "assign faculty" action:
 *
 *   GET    /api/courses                list + search + filter + sort + page
 *   GET    /api/courses/options        departments for the filters
 *   GET    /api/courses/unassigned     courses without a teacher (banner)
 *   GET    /api/courses/{id}           one record
 *   POST   /api/courses                create
 *   PUT    /api/courses/{id}           update
 *   PUT    /api/courses/{id}/faculty   assign (?facultyId=) / unassign (?)
 *   DELETE /api/courses/{id}           delete
 *
 * Students may open this page (courses.read) and see the catalogue, so only
 * viewers are wired for everybody; every write action is gated with
 * courses.write and re-checked by the backend.
 * ========================================================================== */

(function () {
  'use strict';

  const MODULE = 'courses';
  const API = '/courses';

  /** CourseStatus enum - the exact constants the backend deserialises. */
  const STATUS_VALUES = ['ACTIVE', 'INACTIVE'];

  let table = null;
  let editingId = null;
  let editingRow = null;
  let viewingRow = null;
  let assignRow = null;
  let unassignedList = [];

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('courses.html', 'Courses');

    /* the header chip is filled by the page itself (the shell only paints
       the sidebar / topbar) */
    const roleChip = document.querySelector('.page-actions [data-user-role]');
    if (roleChip) roleChip.textContent = Auth.role() || '—';

    const canRead = Auth.can('courses.read');
    const canWrite = Auth.can('courses.write');
    const canExport = Auth.can('reports.read');

    table = new CrudTable({
      mount: '#tableMount',
      apiPath: API,
      searchPlaceholder: 'Search by code, course name, department or teacher…',
      emptyTitle: 'No courses match your search',
      emptyDetail: 'Clear the filters, or add a new course.',
      createLabel: 'Add course',
      canCreate: canWrite,
      exportPath: canExport ? '/reports/courses' : null,
      exportFilename: 'courses.csv',
      pageSizes: [5, 10, 25, 50],

      filters: [
        { name: 'department', label: 'Department' },
        { name: 'semester', label: 'Semester' },
        { name: 'status', label: 'Status' }
      ],

      /* only keys whitelisted in CourseRepository.SORT_COLUMNS are accepted */
      sortOptions: [
        { value: 'courseCode:asc', label: 'Code (A → Z)' },
        { value: 'courseCode:desc', label: 'Code (Z → A)' },
        { value: 'courseName:asc', label: 'Course name (A → Z)' },
        { value: 'department:asc', label: 'Department (A → Z)' },
        { value: 'semester:asc', label: 'Semester (low → high)' },
        { value: 'semester:desc', label: 'Semester (high → low)' },
        { value: 'credits:desc', label: 'Credits (high → low)' },
        { value: 'facultyName:asc', label: 'Faculty (A → Z)' },
        { value: 'status:asc', label: 'Status' }
      ],
      defaultSort: 'courseCode:asc',

      columns: [
        { key: 'courseCode', label: 'Code', render: (v) => codeBadge(v, 'bg-dark') },
        {
          key: 'courseName',
          label: 'Course',
          render: (v, row) => `
            <span class="cell-strong">${escapeHtml(v)}</span>
            <span class="cell-sub">${escapeHtml(row.department || '')}</span>`
        },
        { key: 'semester', label: 'Semester' },
        { key: 'credits', label: 'Credits', className: 'hide-mobile', cellClass: 'hide-mobile' },
        { key: 'facultyName', label: 'Faculty', render: facultyCell },
        { key: 'status', label: 'Status', render: (v) => statusBadge(v) }
      ],

      onView: canRead ? (row) => showView(row, canWrite) : null,
      onEdit: canWrite ? (row) => openForm(row) : null,
      onDelete: canWrite ? (row) => remove(row) : null,
      onCreate: canWrite ? () => openForm(null) : null,

      /* the "assign faculty" row action - write access only */
      customActions: canWrite
        ? [{ key: 'assign', icon: 'bi-person-plus', title: 'Assign faculty', color: 'primary' }]
        : [],
      onCustom: canWrite
        ? (key, row) => { if (key === 'assign') openAssign(row); }
        : null
    });

    /* restore ?q= coming from the global search box */
    const q = new URLSearchParams(location.search).get('q');
    if (q) {
      const input = table.mountEl.querySelector('[data-toolbar="search"]');
      if (input) input.value = q;
      table.query.keyword = q;
    }

    await Promise.all([table.refresh(), loadOptions()]);

    /* /api/courses/unassigned requires ADMIN or FACULTY - same set as
       courses.write, so students never see this banner */
    if (canWrite) loadUnassignedCount();
  }

  /* --------------------------------------------------------- filter lists */

  async function loadOptions() {
    try {
      const options = await apiGet(API + '/options');
      const departments = (options.departments || []).map((d) => ({ value: d, label: d }));

      table.setFilterOptions('department', departments);
      /* the add / edit form accepts free text so a brand new catalogue
         can still receive its first department */
      fillDatalist('departmentList', departments);
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

  /** The assigned teacher, or the warning chip when nobody is assigned. */
  function facultyCell(value) {
    if (value) return `<span class="cell-strong">${escapeHtml(value)}</span>`;
    return statusBadge('PENDING', 'Unassigned');
  }

  /* ---------------------------------------------------------------- VIEW */

  function showView(row, canEdit) {
    viewingRow = row;
    document.getElementById('viewName').textContent = row.courseName || 'Course';
    document.getElementById('viewMeta').textContent =
      (row.courseCode || '') + ' · ' + (row.department || '');
    document.getElementById('viewInitials').textContent = (row.courseCode || '?')
      .slice(0, 2).toUpperCase();

    const pairs = [
      ['Record ID', escapeHtml(row.id ?? '—')],
      ['Course code', codeBadge(row.courseCode, 'bg-dark')],
      ['Course name', escapeHtml(row.courseName || '—')],
      ['Department', escapeHtml(row.department || '—')],
      ['Semester', escapeHtml(row.semester ?? '—')],
      ['Credits', escapeHtml(row.credits ?? '—')],
      ['Assigned faculty', row.facultyName
        ? escapeHtml(row.facultyName) + (row.facultyId ? ' <span class="text-secondary">(record #' + escapeHtml(row.facultyId) + ')</span>' : '')
        : 'Unassigned'],
      ['Description', escapeHtml(row.description || '—')],
      ['Status', statusBadge(row.status)],
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
    editingRow = row || null;
    const form = document.getElementById('courseForm');
    resetForm(form);

    document.getElementById('formTitle').textContent = row ? 'Edit course' : 'Add course';
    document.getElementById('saveLabel').textContent = row ? 'Update course' : 'Save course';

    /* sensible defaults accepted by the server (semester 1-8, credits 1-10) */
    document.getElementById('semester').value = 1;
    document.getElementById('credits').value = 4;
    document.getElementById('status').value = 'ACTIVE';

    if (row) {
      ['courseCode', 'courseName', 'department', 'semester', 'credits',
       'description', 'status'].forEach((field) => {
        const input = form.querySelector(`[name="${field}"]`);
        if (input && row[field] !== undefined && row[field] !== null) {
          input.value = row[field];
        }
      });
    }

    openModal('formModal');
    setTimeout(() => document.getElementById('courseCode').focus(), 400);
  }

  document.getElementById('courseForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));

    if (!form.checkValidity()) {
      form.classList.add('was-validated');
      return;
    }

    const payload = {
      courseCode: val('courseCode'),
      courseName: val('courseName'),
      department: val('department'),
      semester: parseInt(val('semester') || '1', 10),
      credits: parseInt(val('credits') || '4', 10),
      facultyId: editingRow ? (editingRow.facultyId ?? null) : null,
      description: val('description') || null,
      status: val('status') || 'ACTIVE'
    };

    await withBusy(document.getElementById('saveBtn'), async () => {
      try {
        if (editingId) {
          /* never clobber a teacher assigned after this page was loaded -
             the form does not edit the assignment, so read it back first */
          const current = await api.get(`${API}/${editingId}`);
          payload.facultyId = current.facultyId ?? null;

          const updated = await api.put(`${API}/${editingId}`, payload);
          toastSuccess(`${updated.courseCode} was updated.`, 'Course updated');
        } else {
          const created = await api.post(API, payload);
          toastSuccess(`${created.courseCode} was added to the course catalogue.`, 'Course added');
        }
        closeModal('formModal');
        await table.refresh();
        if (Auth.can('courses.write')) loadUnassignedCount();
      } catch (error) {
        if (error.errors) showFieldErrors(form, error.errors);
        toastError(errorMessage(error, 'The course could not be saved.'));
      }
    }, 'Saving…');
  });

  /* ------------------------------------------------------------- DELETE */

  async function remove(row) {
    const confirmed = await confirmDialog({
      title: 'Delete course',
      message: `Delete ${row.courseCode} (${row.courseName})? This removes the record from the database and cannot be undone.`,
      confirmText: 'Yes, delete',
      confirmClass: 'btn-danger'
    });
    if (!confirmed) return;

    try {
      await api.del(`${API}/${row.id}`);
      toastSuccess(`${row.courseCode} was deleted.`, 'Course deleted');
      await table.refresh();
      if (Auth.can('courses.write')) loadUnassignedCount();
    } catch (error) {
      toastError(errorMessage(error, 'The course could not be deleted.'));
    }
  }

  /* --------------------------------------------------- ASSIGN FACULTY */

  function openAssign(row) {
    if (!Auth.can('courses.write')) return;
    assignRow = row;

    document.getElementById('assignCourse').innerHTML = `
      ${codeBadge(row.courseCode, 'bg-dark')}
      <span class="ms-2">${escapeHtml(row.courseName || '')}</span>
      ${row.facultyName
        ? `<div class="cell-sub mt-1">Current teacher: ${escapeHtml(row.facultyName)}</div>`
        : `<div class="cell-sub mt-1">No teacher assigned yet.</div>`}`;

    const select = document.getElementById('assignFaculty');
    fillSelect(select, [], 'Loading faculty…', '');
    select.disabled = true;
    document.getElementById('assignSaveBtn').disabled = true;

    openModal('assignModal');
    loadFacultySelect(select, row);
  }

  async function loadFacultySelect(select, row) {
    const saveBtn = document.getElementById('assignSaveBtn');
    try {
      const page = await apiGet('/faculty', { size: 200, sort: 'name', direction: 'asc' });
      const members = (page && page.content) || [];
      const options = members
        .filter((f) => f.status !== 'INACTIVE' || f.id === row.facultyId)
        .map((f) => ({
          value: f.id,
          label: [f.name, f.employeeId, f.status === 'INACTIVE' ? '(inactive)' : '']
            .filter(Boolean).join(' · ')
        }));

      /* never lose an existing assignment that is missing from this page of
         the faculty list - keep it selected so "save" cannot unassign by
         accident */
      if (row.facultyId && !options.some((o) => String(o.value) === String(row.facultyId))) {
        options.push({
          value: row.facultyId,
          label: (row.facultyName || 'Currently assigned teacher') +
                 ` (record #${row.facultyId})`
        });
      }

      fillSelect(select, options, 'No faculty (unassign)', String(row.facultyId || ''));
      select.disabled = false;
      saveBtn.disabled = false;

      if (!options.length) {
        toastWarning('No active faculty member is available to assign.', 'Faculty list empty');
      }
    } catch (error) {
      /* the list could not be read - keep saving disabled instead of
         letting an empty list silently unassign the course */
      fillSelect(select, [], 'Faculty list unavailable', '');
      select.disabled = true;
      saveBtn.disabled = true;
      toastError(errorMessage(error, 'The faculty list could not be loaded.'));
    }
  }

  document.getElementById('assignSaveBtn').addEventListener('click', () => {
    if (!assignRow) return;
    const select = document.getElementById('assignFaculty');

    withBusy(document.getElementById('assignSaveBtn'), async () => {
      try {
        const facultyId = select.value ? Number(select.value) : null;
        /* no facultyId in the query string -> the backend unassigns */
        const updated = await api.put(
          `${API}/${assignRow.id}/faculty${queryString({ facultyId })}`);

        if (facultyId) {
          const label = select.selectedOptions[0] ? select.selectedOptions[0].textContent : '';
          toastSuccess(`${updated.courseCode} is now taught by ${label}.`, 'Faculty assigned');
        } else {
          toastSuccess(`${updated.courseCode} no longer has an assigned faculty member.`,
            'Faculty unassigned');
        }

        closeModal('assignModal');
        await table.refresh();
        loadUnassignedCount();
      } catch (error) {
        toastError(errorMessage(error, 'The faculty assignment could not be saved.'));
      }
    }, 'Saving…');
  });

  /* --------------------------------------------- UNASSIGNED COURSES UI */

  async function loadUnassignedCount() {
    try {
      const rows = await apiGet(API + '/unassigned');
      showBanner(Array.isArray(rows) ? rows : []);
    } catch (error) {
      /* the banner is a helper - never block the page because of it */
      document.getElementById('unassignedBanner').classList.add('d-none');
      console.warn('Unassigned courses could not be loaded:', error);
    }
  }

  function showBanner(rows) {
    const banner = document.getElementById('unassignedBanner');
    document.getElementById('unassignedCount').textContent = rows.length;
    document.getElementById('unassignedText').textContent = rows.length === 1
      ? 'course still has no assigned faculty member.'
      : 'courses still have no assigned faculty member.';
    banner.classList.toggle('d-none', rows.length === 0);
  }

  document.getElementById('unassignedBtn').addEventListener('click', async () => {
    openModal('unassignedModal');
    await renderUnassigned();
  });

  document.getElementById('unassignedRefreshBtn').addEventListener('click', () => renderUnassigned());

  async function renderUnassigned() {
    const body = document.getElementById('unassignedBody');
    body.innerHTML = loadingBlock('Loading unassigned courses…');

    try {
      const rows = await apiGet(API + '/unassigned');
      const list = Array.isArray(rows) ? rows : [];
      unassignedList = list;
      showBanner(list);

      if (!list.length) {
        body.innerHTML = emptyBlock('Every course has a faculty member',
          'There is nothing to assign right now.');
        return;
      }

      body.innerHTML = `
        <table class="table table-hover align-middle mb-0">
          <thead class="table-light">
            <tr>
              <th class="text-uppercase text-secondary fw-semibold" style="font-size:.68rem;">Code</th>
              <th class="text-uppercase text-secondary fw-semibold" style="font-size:.68rem;">Course</th>
              <th class="text-uppercase text-secondary fw-semibold" style="font-size:.68rem;">Semester</th>
              <th class="text-end text-uppercase text-secondary fw-semibold" style="font-size:.68rem;">Action</th>
            </tr>
          </thead>
          <tbody>
            ${list.map((course) => `
              <tr>
                <td class="align-middle">${codeBadge(course.courseCode, 'bg-dark')}</td>
                <td class="align-middle">
                  <span class="cell-strong">${escapeHtml(course.courseName || '')}</span>
                  <span class="cell-sub">${escapeHtml(course.department || '')}</span>
                </td>
                <td class="align-middle">${escapeHtml(course.semester ?? '—')}</td>
                <td class="text-end align-middle">
                  <button type="button" class="btn btn-sm btn-outline-primary border-0"
                          data-assign-course="${escapeHtml(course.id)}"
                          title="Assign faculty">
                    <i class="bi bi-person-plus"></i>
                  </button>
                </td>
              </tr>`).join('')}
          </tbody>
        </table>`;
    } catch (error) {
      body.innerHTML = `
        <div class="text-center py-4 px-2">
          <div class="display-6 text-danger opacity-50 mb-2"><i class="bi bi-cloud-slash-fill"></i></div>
          <div class="fw-semibold text-danger">Could not load the unassigned courses</div>
          <div class="small text-muted mt-1">${escapeHtml(errorMessage(error, 'Please try again.'))}</div>
        </div>`;
    }
  }

  document.getElementById('unassignedBody').addEventListener('click', (event) => {
    const button = event.target.closest('[data-assign-course]');
    if (!button) return;
    const id = Number(button.dataset.assignCourse);
    const course = unassignedList.find((c) => c.id === id);
    if (!course) return;
    closeModal('unassignedModal');
    setTimeout(() => openAssign(course), 350);
  });

  /* --------------------------------------------------------------- util */

  function val(name) {
    const el = document.querySelector(`#courseForm [name="${name}"]`);
    return el ? String(el.value).trim() : '';
  }
})();
