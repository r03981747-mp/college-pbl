/* ============================================================================
 * smart-campus :: activities.js
 * ----------------------------------------------------------------------------
 * The Activities / campus event module (same shape as students.js):
 *
 *   1. guard the page  -> Auth.requireAuth()
 *   2. paint the shell -> AppShell.init('activities.html', 'Activities')
 *   3. "Upcoming events" strip -> GET /api/activities/upcoming?limit=4
 *   4. CrudTable over /api/activities (search, filters, date range, sorting)
 *   5. view / add / edit / delete modals wired to the REST API
 *
 * Writes are gated with activities.write (ADMIN + FACULTY on the server).
 * ========================================================================== */

(function () {
  'use strict';

  const MODULE = 'activities';
  const API = '/activities';

  /** ActivityCategory constants - exactly the Java enum names. */
  const CATEGORIES = ['TECHNICAL', 'CULTURAL', 'SPORTS', 'WORKSHOP',
                      'SEMINAR', 'CLUB_ACTIVITY', 'OTHER'];

  /** ActivityStatus constants - exactly the Java enum names. */
  const STATUSES = ['SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED'];

  let table = null;
  let editingId = null;
  let viewingRow = null;

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('activities.html', 'Activities');

    const canWrite = Auth.can('activities.write');
    const canExport = Auth.can('reports.read');

    const roleChip = document.querySelector('.page-actions [data-user-role]');
    if (roleChip) roleChip.textContent = Auth.role() || '—';
    if (!canExport) document.getElementById('reportsLinkBtn')?.classList.add('d-none');

    table = new CrudTable({
      mount: '#tableMount',
      apiPath: API,
      searchPlaceholder: 'Search by activity, organizer, venue or category…',
      emptyTitle: 'No activities match your search',
      emptyDetail: 'Clear the filters or the date range, or add a new activity.',
      createLabel: 'Add activity',
      canCreate: canWrite,
      exportPath: canExport ? '/reports/activities' : null,
      exportFilename: 'activities.csv',
      pageSizes: [5, 10, 25, 50],

      filters: [
        { name: 'category', label: 'Category' },
        { name: 'status', label: 'Status' },
        { name: 'organizer', label: 'Organizer' }
      ],

      /* only keys whitelisted in ActivityRepository.SORT_COLUMNS */
      sortOptions: [
        { value: 'startDate:desc', label: 'Start date (newest first)' },
        { value: 'startDate:asc', label: 'Start date (soonest first)' },
        { value: 'endDate:asc', label: 'End date (ascending)' },
        { value: 'activityName:asc', label: 'Activity (A → Z)' },
        { value: 'activityName:desc', label: 'Activity (Z → A)' },
        { value: 'category:asc', label: 'Category (A → Z)' },
        { value: 'organizer:asc', label: 'Organizer (A → Z)' },
        { value: 'venue:asc', label: 'Venue (A → Z)' },
        { value: 'status:asc', label: 'Status' }
      ],
      defaultSort: 'startDate:desc',

      columns: [
        {
          key: 'activityName',
          label: 'Activity',
          render: (v, row) => `
            <span class="cell-strong">${escapeHtml(v)}</span>
            <span class="cell-sub">${escapeHtml(labelify(row.category))}</span>`
        },
        {
          key: 'startDate',
          label: 'Dates',
          render: (v, row) => {
            const start = formatDate(v);
            const end = formatDate(row.endDate);
            const days = start === end ? start : `${start} → ${end}`;
            const times = row.startTime && row.endTime
              ? `${formatTime(row.startTime)} – ${formatTime(row.endTime)}` : '';
            return `<span class="cell-strong">${days}</span>
                    ${times ? `<span class="cell-sub">${times}</span>` : ''}`;
          }
        },
        {
          key: 'venue',
          label: 'Venue',
          className: 'hide-mobile',
          cellClass: 'hide-mobile',
          render: (v) => escapeHtml(v || '—')
        },
        { key: 'status', label: 'Status', render: (v) => statusBadge(v) },
        {
          key: 'maxParticipants',
          label: 'Capacity',
          render: (v) => (v > 0 ? `${formatNumber(v)} seats` : '—')
        }
      ],

      onView: showView,
      onEdit: canWrite ? (row) => openForm(row) : null,
      onDelete: canWrite ? (row) => remove(row) : null,
      onCreate: () => openForm(null)
    });

    /* restore ?q= coming from the upcoming-events cards / global search */
    const q = new URLSearchParams(location.search).get('q');
    if (q) {
      const input = table.mountEl.querySelector('[data-toolbar="search"]');
      if (input) input.value = q;
      table.query.keyword = q;
    }

    bindDateFilters();

    await Promise.all([table.refresh(), loadOptions(), loadUpcoming()]);
  }

  /* ----------------------------------------------------- upcoming events */

  async function loadUpcoming() {
    const host = document.getElementById('upcomingMount');
    if (!host) return;

    try {
      const events = await apiGet(API + '/upcoming', { limit: 4 });
      const rows = Array.isArray(events) ? events : [];

      if (!rows.length) {
        host.innerHTML = emptyBlock('No upcoming events',
          'Everything on the calendar is finished. New events will show up here as soon as they are scheduled.');
        return;
      }

      host.innerHTML = rows.map((event) => {
        const title = event.activityName || 'Untitled activity';
        const href = `activities.html?q=${encodeURIComponent(title)}`;
        const parts = String(event.startDate || '').split('-');
        const day = parts.length === 3 ? parseInt(parts[2], 10) : '—';
        const month = parts.length === 3 ? monthShort(parts[1]) : '';

        return `
          <a class="event-row text-decoration-none text-body" href="${href}"
             title="Search the register for ${escapeHtml(title)}">
            <div class="event-date">
              <span class="d">${escapeHtml(day)}</span>
              <span class="m">${escapeHtml(month)}</span>
            </div>
            <div class="event-body">
              <div class="event-title">${escapeHtml(title)}</div>
              <div class="event-meta">
                ${escapeHtml(event.venue || 'Venue TBA')} ·
                ${escapeHtml(formatTime(event.startTime || ''))} ·
                ${escapeHtml(labelify(event.category))}
              </div>
            </div>
            ${statusBadge(event.status)}
          </a>`;
      }).join('');
    } catch (error) {
      host.innerHTML = `
        <div class="text-center py-4 px-3">
          <div class="display-6 text-danger opacity-50 mb-2"><i class="bi bi-cloud-slash-fill"></i></div>
          <div class="fw-semibold text-danger">Could not load the upcoming events</div>
          <div class="small text-muted mt-1">${escapeHtml(errorMessage(error, 'Please try again.'))}</div>
          <button type="button" class="btn btn-outline-primary btn-sm mt-3"
                  id="upcomingRetry">
            <i class="bi bi-arrow-clockwise me-1"></i>Try again
          </button>
        </div>`;
      document.getElementById('upcomingRetry').addEventListener('click', loadUpcoming);
    }
  }

  function monthShort(monthNumber) {
    const months = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN',
                    'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];
    return months[parseInt(monthNumber, 10) - 1] || '';
  }

  /* --------------------------------------------------------- filter lists */

  async function loadOptions() {
    try {
      const options = await apiGet(API + '/options');
      table.setFilterOptions('organizer',
        (options.organizers || []).map((o) => ({ value: o, label: o })));
    } catch (error) {
      /* filters are optional - never block the page because of them */
      console.warn('Filter options could not be loaded:', error);
    }

    fillSelect(
      table.mountEl.querySelector('[data-filter="category"]'),
      CATEGORIES.map((c) => ({ value: c, label: labelify(c) })),
      'Category: All'
    );
    fillSelect(
      table.mountEl.querySelector('[data-filter="status"]'),
      STATUSES.map((s) => ({ value: s, label: labelify(s) })),
      'Status: All'
    );
  }

  /* --------------------------------------------------------- date filter */

  function bindDateFilters() {
    const from = document.getElementById('fromDate');
    const to = document.getElementById('toDate');
    const clear = document.getElementById('clearDates');
    if (!from || !to || !clear) return;

    from.addEventListener('change', () => {
      if (from.value) to.min = from.value;
      else to.removeAttribute('min');
      table.setFilter('fromDate', from.value);
    });

    to.addEventListener('change', () => {
      if (to.value) from.max = to.value;
      else from.removeAttribute('max');
      table.setFilter('toDate', to.value);
    });

    clear.addEventListener('click', async () => {
      const wasSet = !!(table.query.filters.fromDate || table.query.filters.toDate);
      from.value = '';
      to.value = '';
      from.removeAttribute('max');
      to.removeAttribute('min');

      delete table.query.filters.fromDate;
      delete table.query.filters.toDate;
      table.query.page = 0;
      await table.refresh();

      toastInfo(wasSet
        ? 'The date range was cleared.'
        : 'There was no date range to clear.', 'Filter reset');
    });
  }

  /* ---------------------------------------------------------------- VIEW */

  function showView(row) {
    viewingRow = row;
    document.getElementById('viewName').textContent = row.activityName || 'Activity';
    document.getElementById('viewMeta').textContent =
      `${labelify(row.category)} · ${row.organizer || 'Organizer TBA'}`;
    document.getElementById('viewInitials').textContent = (row.activityName || '?')
      .split(/\s+/).filter(Boolean).map((p) => p[0]).slice(0, 2).join('').toUpperCase();

    const pairs = [
      ['Activity name', escapeHtml(row.activityName || '—')],
      ['Category', escapeHtml(labelify(row.category))],
      ['Organizer', escapeHtml(row.organizer || '—')],
      ['Venue', escapeHtml(row.venue || '—')],
      ['Start date', formatDate(row.startDate)],
      ['End date', formatDate(row.endDate)],
      ['Start time', formatTime(row.startTime)],
      ['End time', formatTime(row.endTime)],
      ['Duration', row.durationInDays !== undefined && row.durationInDays !== null
        ? `${escapeHtml(row.durationInDays)} day(s)` : '—'],
      ['Maximum participants', row.maxParticipants > 0 ? `${formatNumber(row.maxParticipants)} seats` : '—'],
      ['Status', statusBadge(row.status)],
      ['Description', escapeHtml(row.description || '—')],
      ['Created on', formatDateTime(row.createdAt)]
    ];

    document.getElementById('viewBody').innerHTML = `
      <div class="kv-list">
        ${pairs.map(([k, v]) => `<div class="kv-row"><span class="k">${k}</span><span class="v">${v}</span></div>`).join('')}
      </div>`;

    document.getElementById('viewEditBtn').style.display =
      Auth.can('activities.write') ? '' : 'none';

    openModal('viewModal');
  }

  document.getElementById('viewEditBtn').addEventListener('click', () => {
    if (!viewingRow) return;
    closeModal('viewModal');
    setTimeout(() => openForm(viewingRow), 350);
  });

  /* ------------------------------------------------------------ ADD/EDIT */

  function openForm(row) {
    if (!Auth.can('activities.write')) return;
    editingId = row ? row.id : null;
    const form = document.getElementById('activityForm');
    resetForm(form);

    document.getElementById('formTitle').textContent = row ? 'Edit activity' : 'Add activity';
    document.getElementById('saveLabel').textContent = row ? 'Update activity' : 'Save activity';
    document.getElementById('category').value = 'TECHNICAL';
    document.getElementById('status').value = 'SCHEDULED';
    document.getElementById('maxParticipants').value = 50;

    if (row) {
      ['activityName', 'category', 'organizer', 'venue', 'startDate', 'endDate',
       'startTime', 'endTime', 'maxParticipants', 'status', 'description'].forEach((field) => {
        const input = form.querySelector(`[name="${field}"]`);
        if (input && row[field] !== undefined && row[field] !== null) {
          input.value = row[field];
        }
      });
    }

    openModal('formModal');
    setTimeout(() => document.getElementById('activityName').focus(), 400);
  }

  document.getElementById('activityForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));

    if (!form.checkValidity()) {
      form.classList.add('was-validated');
      return;
    }

    const payload = {
      activityName: field('activityName'),
      description: field('description') || null,
      category: field('category') || 'OTHER',
      organizer: field('organizer'),
      venue: field('venue'),
      startDate: field('startDate'),
      endDate: field('endDate'),
      startTime: field('startTime'),
      endTime: field('endTime'),
      maxParticipants: parseInt(field('maxParticipants') || '0', 10),
      status: field('status') || 'SCHEDULED'
    };

    /* the same rules the server enforces, checked early for a friendly message */
    if (payload.endDate && payload.startDate && payload.endDate < payload.startDate) {
      document.getElementById('endDate').classList.add('is-invalid');
      toastError('The end date cannot be before the start date.', 'Check the dates');
      return;
    }
    if (payload.endTime && payload.startTime && payload.endTime <= payload.startTime) {
      document.getElementById('endTime').classList.add('is-invalid');
      toastError('The end time must be after the start time.', 'Check the times');
      return;
    }

    await withBusy(document.getElementById('saveBtn'), async () => {
      try {
        if (editingId) {
          const updated = await api.put(`${API}/${editingId}`, payload);
          toastSuccess(`${updated.activityName} was updated.`, 'Activity updated');
        } else {
          const created = await api.post(API, payload);
          toastSuccess(`${created.activityName} was added to the calendar.`, 'Activity created');
        }
        closeModal('formModal');
        await Promise.all([table.refresh(), loadUpcoming()]);
      } catch (error) {
        if (error.errors) showFieldErrors(form, error.errors);
        toastError(errorMessage(error, 'The activity could not be saved.'));
      }
    }, 'Saving…');
  });

  /* ------------------------------------------------------------- DELETE */

  async function remove(row) {
    const confirmed = await confirmDialog({
      title: 'Delete activity',
      message: `Delete ${row.activityName} on ${formatDate(row.startDate)}? This removes the record from the database and cannot be undone.`,
      confirmText: 'Yes, delete',
      confirmClass: 'btn-danger'
    });
    if (!confirmed) return;

    try {
      await api.del(`${API}/${row.id}`);
      toastSuccess(`${row.activityName} was deleted.`, 'Activity deleted');
      await Promise.all([table.refresh(), loadUpcoming()]);
    } catch (error) {
      toastError(errorMessage(error, 'The activity could not be deleted.'));
    }
  }

  /* --------------------------------------------------------------- util */

  /** Reads one named field of the activity form. */
  function field(name) {
    const form = document.getElementById('activityForm');
    const el = form ? form.querySelector(`[name="${name}"]`) : null;
    return el ? String(el.value).trim() : '';
  }
})();
