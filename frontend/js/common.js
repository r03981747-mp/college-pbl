/* ============================================================================
 * smart-campus :: common.js
 * ----------------------------------------------------------------------------
 * Shared UI toolkit used by every page:
 *
 *   Toasts .............. toast(message, type)
 *   Confirm dialog ....... await confirmDialog({ title, message })
 *   Bootstrap modals ..... openModal(id) / closeModal(id)
 *   Formatting ........... escapeHtml, formatDate, formatTime, statusBadge
 *   Async buttons ......... withBusy(button, asyncFn)
 *   CRUD table ............ new CrudTable(config)   <-- search + filter + sort
 *                           + pagination + loading/empty/error states
 * ========================================================================== */

/* ============================================================== 1. TOASTS */

const ToastStyles = {
  success: { icon: 'bi-check-circle-fill', color: 'text-success' },
  error:   { icon: 'bi-x-octagon-fill',    color: 'text-danger' },
  warning: { icon: 'bi-exclamation-triangle-fill', color: 'text-warning' },
  info:    { icon: 'bi-info-circle-fill',  color: 'text-info' }
};

/** Shows a Bootstrap toast in the #toastHost container (created if missing). */
function toast(message, type = 'success', title = null) {
  let host = document.getElementById('toastHost');
  if (!host) {
    host = document.createElement('div');
    host.id = 'toastHost';
    host.className = 'toast-container position-fixed top-0 end-0 p-3 z-index-1080';
    host.style.zIndex = '1080';
    document.body.appendChild(host);
  }

  const style = ToastStyles[type] || ToastStyles.info;
  const id = 'toast-' + Date.now() + '-' + Math.floor(Math.random() * 1000);
  const heading = title || (type === 'error' ? 'Something went wrong' :
                             type === 'warning' ? 'Please note' :
                             type === 'info' ? 'Information' : 'Success');

  const wrapper = document.createElement('div');
  wrapper.className = 'toast align-items-center border-0 bg-white shadow-lg';
  wrapper.id = id;
  wrapper.setAttribute('role', 'alert');
  wrapper.setAttribute('aria-live', 'polite');
  wrapper.innerHTML = `
    <div class="d-flex">
      <div class="toast-body d-flex align-items-start gap-2 py-3">
        <i class="bi ${style.icon} ${style.color} fs-5 mt-1"></i>
        <div>
          <div class="fw-semibold small mb-1">${escapeHtml(heading)}</div>
          <div class="text-body small lh-sm">${escapeHtml(message)}</div>
        </div>
      </div>
      <button type="button" class="btn-close me-2 m-auto" data-bs-dismiss="toast"
              aria-label="Close"></button>
    </div>`;
  host.appendChild(wrapper);

  const instance = new bootstrap.Toast(wrapper, { delay: type === 'error' ? 6000 : 3500 });
  wrapper.addEventListener('hidden.bs.toast', () => wrapper.remove());
  instance.show();
}

const toastSuccess = (m, t) => toast(m, 'success', t);
const toastError   = (m, t) => toast(m, 'error', t);
const toastWarning = (m, t) => toast(m, 'warning', t);
const toastInfo    = (m, t) => toast(m, 'info', t);

/** Turns any thrown value into a user readable message. */
function errorMessage(error, fallback = 'The operation could not be completed.') {
  if (!error) return fallback;
  if (typeof error === 'string') return error;
  return error.message || fallback;
}

/* ==================================================== 2. CONFIRM DIALOG */

/**
 * Promise based confirmation box.
 *   const ok = await confirmDialog({ title: 'Delete', message: 'Sure?' });
 * @returns {Promise<boolean>} true when the user pressed the confirm button
 */
function confirmDialog({
  title = 'Please confirm',
  message = 'Are you sure?',
  confirmText = 'Yes, continue',
  cancelText = 'Cancel',
  confirmClass = 'btn-primary'
} = {}) {
  return new Promise((resolve) => {
    const id = 'confirmDialog';
    let host = document.getElementById(id);
    if (host) host.remove();

    host = document.createElement('div');
    host.id = id;
    host.innerHTML = `
      <div class="modal fade" tabindex="-1" data-bs-backdrop="static">
        <div class="modal-dialog modal-dialog-centered modal-sm">
          <div class="modal-content border-0 shadow">
            <div class="modal-header border-0 pb-0">
              <h6 class="modal-title fw-semibold">
                <i class="bi bi-question-circle text-primary me-1"></i>${escapeHtml(title)}
              </h6>
              <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body pt-1">
              <p class="text-secondary small mb-0">${escapeHtml(message)}</p>
            </div>
            <div class="modal-footer border-0 pt-0">
              <button type="button" class="btn btn-light btn-sm border" data-bs-dismiss="modal">
                ${escapeHtml(cancelText)}
              </button>
              <button type="button" class="btn btn-sm ${confirmClass}" data-confirm="ok">
                ${escapeHtml(confirmText)}
              </button>
            </div>
          </div>
        </div>
      </div>`;
    document.body.appendChild(host);

    const modalEl = host.querySelector('.modal');
    const instance = new bootstrap.Modal(modalEl);

    let answered = false;
    const finish = (value) => {
      if (answered) return;
      answered = true;
      instance.hide();
      modalEl.addEventListener('hidden.bs.modal', () => host.remove(), { once: true });
      setTimeout(() => { if (document.body.contains(host)) host.remove(); }, 400);
      resolve(value);
    };

    host.querySelector('[data-confirm="ok"]').addEventListener('click', () => finish(true));
    modalEl.addEventListener('hide.bs.prevent', (e) => e.preventDefault());
    modalEl.addEventListener('hidden.bs.modal', () => finish(false));

    instance.show();
    const confirmBtn = host.querySelector('[data-confirm="ok"]');
    if (confirmBtn) confirmBtn.focus();
  });
}

/* ========================================================= 3. MODALS */

function openModal(id) {
  const el = typeof id === 'string' ? document.getElementById(id) : id;
  if (!el) return;
  bootstrap.Modal.getOrCreateInstance(el, { backdrop: 'static', keyboard: false }).show();
}

function closeModal(id) {
  const el = typeof id === 'string' ? document.getElementById(id) : id;
  if (!el) return;
  const instance = bootstrap.Modal.getInstance(el);
  if (instance) instance.hide();
}

/** Clears every input/select/textarea inside a form or modal body. */
function resetForm(formOrId) {
  const form = typeof formOrId === 'string' ? document.getElementById(formOrId) : formOrId;
  if (!form) return;
  form.reset();
  form.querySelectorAll('input, select, textarea').forEach((field) => {
    if (field.type === 'checkbox' || field.type === 'radio') field.checked = false;
    else field.value = '';
    field.classList.remove('is-invalid', 'is-valid');
  });
  form.querySelectorAll('.invalid-feedback').forEach((el) => { el.textContent = ''; });
}

/** Shows server side validation messages under the matching field. */
function showFieldErrors(formOrId, errors) {
  const form = typeof formOrId === 'string' ? document.getElementById(formOrId) : formOrId;
  if (!form) return;
  if (!errors) {
    form.querySelectorAll('.invalid-feedback').forEach((el) => { el.textContent = ''; });
    return;
  }
  Object.keys(errors).forEach((field) => {
    const input = form.querySelector(`[name="${field}"]`);
    if (!input) return;
    input.classList.add('is-invalid');
    let feedback = input.parentElement.querySelector('.invalid-feedback');
    if (!feedback) {
      feedback = document.createElement('div');
      feedback.className = 'invalid-feedback';
      input.parentElement.appendChild(feedback);
    }
    feedback.textContent = errors[field];
  });
}

/* ==================================================== 4. FORMATTING HELPERS */

function escapeHtml(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** "STUDENT" -> "Student", "Resource not found" unchanged. */
function labelify(value) {
  if (value === null || value === undefined || value === '') return '—';
  const text = String(value).replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** 2026-01-05 -> 05 Jan 2026 */
function formatDate(value) {
  if (!value) return '—';
  const parts = String(value).slice(0, 10).split('-');
  if (parts.length !== 3) return value;
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const monthIndex = parseInt(parts[1], 10) - 1;
  if (isNaN(monthIndex)) return value;
  return `${parts[2]} ${months[monthIndex] || parts[1]} ${parts[0]}`;
}

/** 10:00:00 or 10:00 -> 10:00 */
function formatTime(value) {
  if (!value) return '—';
  return String(value).slice(0, 5);
}

function formatDateTime(value) {
  if (!value) return '—';
  const text = String(value);
  // API timestamps arrive as ISO local date-time strings (2026-10-30T07:10:11),
  // while date-only values must not gain a bogus time suffix.
  const match = text.match(/^\d{4}-\d{2}-\d{2}[T ](\d{2}:\d{2})/);
  return match ? `${formatDate(text)} ${match[1]}` : formatDate(text);
}

function formatNumber(value) {
  if (value === null || value === undefined || value === '') return '0';
  return Number(value).toLocaleString('en-IN');
}

/** Classes used by statusBadge() so every page looks identical. */
const BadgePalette = {
  APPROVED: 'bg-success-subtle text-success border-success-emphasis',
  ACTIVE: 'bg-success-subtle text-success border-success-emphasis',
  COMPLETED: 'bg-success-subtle text-success border-success-emphasis',
  AVAILABLE: 'bg-success-subtle text-success border-success-emphasis',
  GOOD: 'bg-success-subtle text-success border-success-emphasis',
  PUBLISHED: 'bg-success-subtle text-success border-success-emphasis',
  ENABLED: 'bg-success-subtle text-success border-success-emphasis',
  PRESENT: 'bg-success-subtle text-success border-success-emphasis',
  ELIGIBLE: 'bg-success-subtle text-success border-success-emphasis',
  GRADED: 'bg-success-subtle text-success border-success-emphasis',

  PENDING: 'bg-warning-subtle text-warning border-warning-emphasis',
  LATE: 'bg-warning-subtle text-warning border-warning-emphasis',
  SUBMITTED: 'bg-info-subtle text-info border-info-emphasis',
  ONGOING: 'bg-warning-subtle text-warning border-warning-emphasis',
  MAINTENANCE: 'bg-warning-subtle text-warning border-warning-emphasis',
  REPAIR: 'bg-warning-subtle text-warning border-warning-emphasis',
  DRAFT: 'bg-warning-subtle text-warning border-warning-emphasis',
  BORROWED: 'bg-warning-subtle text-warning border-warning-emphasis',

  REJECTED: 'bg-danger-subtle text-danger border-danger-emphasis',
  ABSENT: 'bg-danger-subtle text-danger border-danger-emphasis',
  NOTELIGIBLE: 'bg-danger-subtle text-danger border-danger-emphasis',
  INACTIVE: 'bg-danger-subtle text-danger border-danger-emphasis',
  DISABLED: 'bg-danger-subtle text-danger border-danger-emphasis',
  DECOMMISSIONED: 'bg-danger-subtle text-danger border-danger-emphasis',
  DAMAGED: 'bg-danger-subtle text-danger border-danger-emphasis',
  FAILED: 'bg-danger-subtle text-danger border-danger-emphasis',

  CANCELLED: 'bg-secondary-subtle text-secondary border-secondary-emphasis',
  ARCHIVED: 'bg-secondary-subtle text-secondary border-secondary-emphasis',

  SCHEDULED: 'bg-info-subtle text-info border-info-emphasis',
  REGISTERED: 'bg-info-subtle text-info border-info-emphasis',
  UPCOMING: 'bg-info-subtle text-info border-info-emphasis'
};

/** Builds a Bootstrap badge for any status/enum value. */
function statusBadge(value, customLabel = null) {
  if (value === null || value === undefined || value === '') return '—';
  const key = String(value).toUpperCase().replace(/[^A-Z]/g, '');
  const palette = BadgePalette[key] || 'bg-primary-subtle text-primary border-primary-emphasis';
  return `<span class="badge rounded-pill border ${palette} fw-medium">${escapeHtml(
    customLabel !== null ? customLabel : labelify(value)
  )}</span>`;
}

/** Uppercase code badges such as room / lab / booking references. */
function codeBadge(value, colorClass = 'bg-dark') {
  if (!value) return '—';
  return `<span class="badge ${colorClass} font-monospace">${escapeHtml(value)}</span>`;
}

/** Fills a <select> with options, optionally keeping the current value. */
function fillSelect(select, options, placeholder = null, selectedValue = null) {
  if (!select) return;
  const current = selectedValue !== null ? selectedValue : select.value;
  const items = Array.isArray(options)
    ? options.map((o) => (typeof o === 'object' ? o : { value: o, label: o }))
    : [];

  let html = placeholder !== null ? `<option value="">${escapeHtml(placeholder)}</option>` : '';
  items.forEach((item) => {
    const value = String(item.value);
    const selected = String(current) === value ? ' selected' : '';
    html += `<option value="${escapeHtml(value)}"${selected}>${escapeHtml(item.label)}</option>`;
  });
  select.innerHTML = html;
}

/* ================================================= 5. ASYNC BUTTON HELPER */

/** Disables a button while an async action runs, restores it afterwards. */
async function withBusy(button, asyncFn, busyText = null) {
  if (!button) return asyncFn();
  const original = button.innerHTML;
  const originalDisabled = button.disabled;
  button.disabled = true;
  button.innerHTML = busyText ||
    '<span class="spinner-border spinner-border-sm me-1" role="status"></span>Working...';
  try {
    return await asyncFn();
  } finally {
    button.disabled = originalDisabled;
    button.innerHTML = original;
  }
}

/* ================================================ 6. STATE BLOCKS */

function stateBlock(icon, title, detail = '', extraClass = '') {
  return `
    <div class="text-center py-5 px-3 ${extraClass}">
      <div class="display-6 text-muted opacity-25 mb-2"><i class="bi ${icon}"></i></div>
      <div class="fw-semibold text-secondary">${escapeHtml(title)}</div>
      ${detail ? `<div class="small text-muted mt-1">${escapeHtml(detail)}</div>` : ''}
    </div>`;
}

function loadingBlock(label = 'Loading data from the server...') {
  return `
    <div class="text-center py-5">
      <div class="spinner-border text-primary mb-3" role="status" style="width:2.2rem;height:2.2rem;"></div>
      <div class="small text-secondary">${escapeHtml(label)}</div>
    </div>`;
}

function errorBlock(message) {
  return `
    <div class="text-center py-5 px-3">
      <div class="display-6 text-danger opacity-50 mb-2"><i class="bi bi-cloud-slash-fill"></i></div>
      <div class="fw-semibold text-danger">Could not load the data</div>
      <div class="small text-muted mt-1">${escapeHtml(message)}</div>
      <button type="button" class="btn btn-outline-primary btn-sm mt-3" data-action="retry">
        <i class="bi bi-arrow-clockwise me-1"></i>Try again
      </button>
    </div>`;
}

function emptyBlock(title, detail = '') {
  return stateBlock('bi-inbox', title, detail);
}

/* ================================================= 7. CRUD TABLE */

/**
 * Generic, reusable data table.
 *
 * One configuration object gives a page:
 *   - keyword search box
 *   - filter drop-downs
 *   - sort selector
 *   - page size selector + pagination
 *   - loading / empty / error states
 *   - per-row view / edit / delete actions
 *   - optional CSV export button
 *
 * Example
 * -------
 *   const table = new CrudTable({
 *     apiPath: '/students',
 *     columns: [{ key: 'name', label: 'Name' }, ...],
 *     filters: [{ name: 'department', label: 'Department', options: [] }],
 *     onView: (row) => showStudent(row),
 *     onEdit: (row) => openForm(row),
 *     onDelete: (row) => remove(row)
 *   });
 *   await table.refresh();
 */
class CrudTable {
  constructor(config) {
    this.cfg = Object.assign({
      mount: '#tableMount',
      apiPath: '',
      columns: [],
      filters: [],
      searchPlaceholder: 'Search...',
      sortOptions: [],
      defaultSort: '',
      pageSize: 10,
      pageSizes: [5, 10, 25, 50],
      createLabel: 'Add new',
      exportPath: null,
      exportLabel: 'Export CSV',
      emptyTitle: 'No records found',
      emptyDetail: 'Try a different search term, or add a new record.',
      canCreate: true,
      onView: null,
      onEdit: null,
      onDelete: null,
      onRowClick: null,
      rowClass: null
    }, config);

    this.query = {
      keyword: '',
      page: 0,
      size: this.cfg.pageSize,
      sort: this.cfg.defaultSort,
      filters: {}
    };
    this.data = [];
    this.totalElements = 0;
    this.totalPages = 0;
    this.loading = false;
    this.error = null;

    this.mountEl = typeof this.cfg.mount === 'string'
      ? document.querySelector(this.cfg.mount)
      : this.cfg.mount;

    this.renderShell();
    this.bindToolbar();
  }

  /* ------------------------------------------------------- rendering */

  renderShell() {
    if (!this.mountEl) return;

    const filterHtml = this.cfg.filters.map((filter) => `
      <div class="col-auto">
        <label class="form-label small text-secondary mb-1">${escapeHtml(filter.label)}</label>
        <select class="form-select form-select-sm" data-filter="${escapeHtml(filter.name)}">
          <option value="">${escapeHtml(filter.label)}: All</option>
        </select>
      </div>`).join('');

    const sortHtml = this.cfg.sortOptions.length ? `
      <div class="col-auto">
        <label class="form-label small text-secondary mb-1">Sort by</label>
        <select class="form-select form-select-sm" data-toolbar="sort">
          ${this.cfg.sortOptions.map((o) =>
            `<option value="${escapeHtml(o.value)}"${o.value === this.query.sort ? ' selected' : ''}>${escapeHtml(o.label)}</option>`
          ).join('')}
        </select>
      </div>` : '';

    this.mountEl.innerHTML = `
      <div class="card border-0 shadow-sm rounded-4 overflow-hidden">
        <div class="card-body pb-0">
          <div class="row g-2 align-items-end mb-3">
            <div class="col-12 col-md-4">
              <label class="form-label small text-secondary mb-1">Search</label>
              <div class="input-group input-group-sm">
                <span class="input-group-text bg-light"><i class="bi bi-search"></i></span>
                <input type="search" class="form-control" data-toolbar="search"
                       placeholder="${escapeHtml(this.cfg.searchPlaceholder)}">
              </div>
            </div>
            ${filterHtml}
            ${sortHtml}
            <div class="col-auto ms-auto">
              <div class="d-flex gap-2 align-items-end">
                ${this.cfg.exportPath ? `
                  <button type="button" class="btn btn-outline-secondary btn-sm"
                          data-action="export" title="${escapeHtml(this.cfg.exportLabel)}">
                    <i class="bi bi-file-earmark-spreadsheet me-1"></i>${escapeHtml(this.cfg.exportLabel)}
                  </button>` : ''}
                <button type="button" class="btn btn-outline-primary btn-sm" data-action="refresh">
                  <i class="bi bi-arrow-clockwise"></i>
                </button>
                ${this.cfg.canCreate ? `
                  <button type="button" class="btn btn-primary btn-sm" data-action="create">
                    <i class="bi bi-plus-lg me-1"></i>${escapeHtml(this.cfg.createLabel)}
                  </button>` : ''}
              </div>
            </div>
          </div>
        </div>

        <div class="table-responsive" data-region="table"></div>

        <div class="card-footer bg-white border-top d-flex flex-wrap
                    gap-2 align-items-center justify-content-between py-2"
             data-region="footer">
          <div class="small text-secondary" data-region="summary">—</div>
          <div class="d-flex gap-2 align-items-center" data-region="pager"></div>
        </div>
      </div>`;
  }

  bindToolbar() {
    if (!this.mountEl) return;

    const search = this.mountEl.querySelector('[data-toolbar="search"]');
    if (search) {
      search.addEventListener('input', debounce(() => {
        this.query.keyword = search.value.trim();
        this.query.page = 0;
        this.refresh();
      }, 350));
    }

    this.mountEl.querySelectorAll('[data-filter]').forEach((select) => {
      select.addEventListener('change', () => {
        const name = select.dataset.filter;
        if (select.value) this.query.filters[name] = select.value;
        else delete this.query.filters[name];
        this.query.page = 0;
        this.refresh();
      });
    });

    const sort = this.mountEl.querySelector('[data-toolbar="sort"]');
    if (sort) {
      sort.addEventListener('change', () => {
        this.query.sort = sort.value;
        this.query.page = 0;
        this.refresh();
      });
    }

    this.mountEl.addEventListener('click', (event) => {
      const button = event.target.closest('button, a[data-row-action]');
      if (!button) return;
      const action = button.dataset.action;

      if (action === 'refresh') this.refresh();
      if (action === 'retry') this.refresh();
      if (action === 'create' && this.cfg.onCreate) this.cfg.onCreate();
      if (action === 'export') this.exportCsv();
      if (action === 'prev') { this.goToPage(this.query.page - 1); }
      if (action === 'next') { this.goToPage(this.query.page + 1); }
      if (action === 'size') { /* handled by change event below */ }

      const rowAction = button.dataset.rowAction;
      if (rowAction) {
        const index = parseInt(button.dataset.rowIndex, 10);
        const row = this.data[index];
        if (!row) return;
        if (rowAction === 'view' && this.cfg.onView) this.cfg.onView(row);
        if (rowAction === 'edit' && this.cfg.onEdit) this.cfg.onEdit(row);
        if (rowAction === 'delete' && this.cfg.onDelete) this.cfg.onDelete(row);
        if (rowAction === 'custom' && this.cfg.onCustom) {
          this.cfg.onCustom(button.dataset.customKey, row);
        }
      }
    });

    this.mountEl.addEventListener('change', (event) => {
      const sizeSelect = event.target.closest('[data-toolbar="size"]');
      if (sizeSelect) {
        this.query.size = parseInt(sizeSelect.value, 10);
        this.query.page = 0;
        this.refresh();
      }
    });
  }

  goToPage(page) {
    if (page < 0 || (this.totalPages && page >= this.totalPages)) return;
    this.query.page = page;
    this.refresh();
  }

  /* ------------------------------------------------------- data loading */

  params() {
    const params = Object.assign({
      keyword: this.query.keyword,
      page: this.query.page,
      size: this.query.size
    }, this.query.filters);
    if (this.query.sort) {
      const parts = this.query.sort.split(':');
      params.sort = parts[0];
      params.direction = parts[1] || 'asc';
    }
    return params;
  }

  async refresh() {
    if (!this.mountEl) return;
    this.loading = true;
    this.error = null;
    this.renderBody();

    try {
      const page = await apiGet(this.cfg.apiPath, this.params());
      this.data = (page && page.content) || [];
      this.totalElements = (page && page.totalElements) || 0;
      this.totalPages = (page && page.totalPages) || 0;
      this.currentSort = (page && page.sort) || '';
    } catch (error) {
      this.error = errorMessage(error, 'Unable to load records.');
      this.data = [];
      this.totalElements = 0;
      this.totalPages = 0;
    } finally {
      this.loading = false;
      this.renderBody();
      this.renderFooter();
      if (this.cfg.onLoaded) this.cfg.onLoaded(this);
    }
  }

  /* ------------------------------------------------------- rendering */

  renderBody() {
    const region = this.mountEl && this.mountEl.querySelector('[data-region="table"]');
    if (!region) return;

    if (this.loading) { region.innerHTML = loadingBlock(); return; }
    if (this.error) { region.innerHTML = errorBlock(this.error); return; }
    if (!this.data.length) {
      region.innerHTML = emptyBlock(this.cfg.emptyTitle, this.cfg.emptyDetail);
      return;
    }

    const head = this.cfg.columns.map((col) => `
      <th class="text-uppercase tiny-letter-spacing text-secondary fw-semibold align-middle
                 ${col.className || ''}"
          style="font-size:.68rem;letter-spacing:.04em;">
        ${escapeHtml(col.label)}
      </th>`).join('');

    const body = this.data.map((row, index) => {
      const cells = this.cfg.columns.map((col) => {
        const raw = col.key.split('.').reduce((obj, part) => (obj ? obj[part] : undefined), row);
        const content = col.render ? col.render(raw, row, index) : escapeHtml(raw);
        return `<td class="align-middle ${col.cellClass || ''}">${content}</td>`;
      }).join('');

      const actions = this.renderActions(row, index);
      const rowClass = this.cfg.rowClass ? this.cfg.rowClass(row) : '';
      const clickable = this.cfg.onRowClick ? ' data-row-click="' + index + '" style="cursor:pointer;"' : '';

      return `<tr class="${rowClass}"${clickable}>${cells}${actions ? `<td class="text-end align-middle">${actions}</td>` : ''}</tr>`;
    }).join('');

    const actionsColumn = (this.cfg.onView || this.cfg.onEdit || this.cfg.onDelete || this.cfg.onCustom)
      ? '<th class="text-end text-uppercase text-secondary fw-semibold" style="font-size:.68rem;">Actions</th>'
      : '';

    region.innerHTML = `
      <table class="table table-hover align-middle mb-0">
        <thead class="table-light">
          <tr>${head}${actionsColumn}</tr>
        </thead>
        <tbody>${body}</tbody>
      </table>`;

    if (this.cfg.onRowClick) {
      region.querySelectorAll('[data-row-click]').forEach((tr) => {
        tr.addEventListener('click', (event) => {
          if (event.target.closest('button')) return;
          this.cfg.onRowClick(this.data[parseInt(tr.dataset.rowClick, 10)]);
        });
      });
    }
  }

  renderActions(row, index) {
    const buttons = [];
    if (this.cfg.onView) {
      buttons.push(`<button class="btn btn-sm btn-outline-secondary border-0"
        data-row-action="view" data-row-index="${index}" title="View">
        <i class="bi bi-eye"></i></button>`);
    }
    if (this.cfg.onEdit) {
      buttons.push(`<button class="btn btn-sm btn-outline-primary border-0"
        data-row-action="edit" data-row-index="${index}" title="Edit">
        <i class="bi bi-pencil-square"></i></button>`);
    }
    if (this.cfg.onCustom && this.cfg.customActions) {
      this.cfg.customActions.forEach((item) => {
        buttons.push(`<button class="btn btn-sm btn-outline-${item.color || 'secondary'} border-0"
          data-row-action="custom" data-custom-key="${escapeHtml(item.key)}"
          data-row-index="${index}" title="${escapeHtml(item.title)}">
          <i class="bi ${item.icon}"></i></button>`);
      });
    }
    if (this.cfg.onDelete) {
      buttons.push(`<button class="btn btn-sm btn-outline-danger border-0"
        data-row-action="delete" data-row-index="${index}" title="Delete">
        <i class="bi bi-trash3"></i></button>`);
    }
    if (!buttons.length) return '';
    return `<div class="btn-group btn-group-sm">${buttons.join('')}</div>`;
  }

  renderFooter() {
    const footer = this.mountEl && this.mountEl.querySelector('[data-region="footer"]');
    if (!footer) return;

    const summary = footer.querySelector('[data-region="summary"]');
    const pager = footer.querySelector('[data-region="pager"]');

    if (this.error) {
      summary.textContent = '—';
      pager.innerHTML = '';
      return;
    }

    const from = this.totalElements === 0 ? 0 : this.query.page * this.query.size + 1;
    const to = Math.min((this.query.page + 1) * this.query.size, this.totalElements);
    summary.innerHTML = `Showing <strong>${from}–${to}</strong> of
      <strong>${formatNumber(this.totalElements)}</strong> record(s)`;

    pager.innerHTML = `
      <label class="small text-secondary me-1">Rows</label>
      <select class="form-select form-select-sm" style="width:auto" data-toolbar="size">
        ${this.cfg.pageSizes.map((s) =>
          `<option value="${s}"${s === this.query.size ? ' selected' : ''}>${s}</option>`).join('')}
      </select>
      <div class="btn-group btn-group-sm">
        <button class="btn btn-outline-secondary" data-action="prev"
                ${this.query.page === 0 ? 'disabled' : ''}>
          <i class="bi bi-chevron-left"></i>
        </button>
        <button class="btn btn-outline-secondary" disabled>
          Page ${this.query.page + 1} / ${Math.max(this.totalPages, 1)}
        </button>
        <button class="btn btn-outline-secondary" data-action="next"
                ${this.query.page + 1 >= Math.max(this.totalPages, 1) ? 'disabled' : ''}>
          <i class="bi bi-chevron-right"></i>
        </button>
      </div>`;
  }

  /** Fills one filter drop-down after its options have been fetched. */
  setFilterOptions(name, options, placeholder = null) {
    const select = this.mountEl && this.mountEl.querySelector(`[data-filter="${name}"]`);
    if (!select) return;
    const current = this.query.filters[name] || '';
    fillSelect(select, options, placeholder || `${labelify(name)}: All`, current);
  }

  /** Records a filter chosen from outside the toolbar (e.g. a card button). */
  setFilter(name, value) {
    if (value) this.query.filters[name] = value;
    else delete this.query.filters[name];
    const select = this.mountEl && this.mountEl.querySelector(`[data-filter="${name}"]`);
    if (select) select.value = value || '';
    this.query.page = 0;
    return this.refresh();
  }

  /** Downloads the CSV of the current view from the reports API. */
  async exportCsv() {
    if (!this.cfg.exportPath) return;
    await downloadCsv(withQuery(this.cfg.exportPath, this.params()),
                      this.cfg.exportFilename || 'report.csv');
  }
}

/* ============================================== 8. CSV DOWNLOAD */

/**
 * Fetches a CSV from the reports API and saves it with an <a download>.
 * Uses the browser File API - file handling demonstration on the front end.
 */
async function downloadCsv(path, fallbackName) {
  try {
    const response = await apiRequest(path, { headers: { Accept: 'text/csv' } });
    if (!(response instanceof Response)) {
      throw new ApiError('The server did not return a file.', 500);
    }

    const disposition = response.headers.get('content-disposition') || '';
    const match = disposition.match(/filename="?([^"]+)"?/);
    const fileName = match ? match[1] : fallbackName;

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);

    toastSuccess(`${fileName} has been downloaded.`, 'CSV exported');
  } catch (error) {
    toastError(errorMessage(error, 'The CSV file could not be generated.'));
  }
}

/* ============================================== 9. MISC */

function debounce(fn, delay = 300) {
  let timer;
  return function (...args) {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
}

/** Capitalises each word: "rahul gowda" -> "Rahul Gowda". */
function titleCase(value) {
  if (!value) return '';
  return String(value).replace(/\w\S*/g, (word) =>
    word.charAt(0).toUpperCase() + word.slice(1).toLowerCase());
}

/** Picks the first non-empty value from an object array. */
function pick(obj, ...keys) {
  for (const key of keys) {
    if (obj && obj[key] !== undefined && obj[key] !== null && obj[key] !== '') return obj[key];
  }
  return '';
}

/** Global helper: reads all named fields of a form into a plain object. */
function formData(formOrId) {
  const form = typeof formOrId === 'string' ? document.getElementById(formOrId) : formOrId;
  const result = {};
  if (!form) return result;
  new FormData(form).forEach((value, key) => { result[key] = value; });
  return result;
}
