/* ============================================================================
 * smart-campus :: users.js
 * ----------------------------------------------------------------------------
 * Administrator module for login accounts.
 *
 *   1. guard the page  -> Auth.requireAuth() + Auth.can('users.read')
 *   2. paint the shell -> AppShell.init('users.html', 'Users')
 *   3. CrudTable on GET /api/users  (PagedResponse<UserInfo>)
 *   4. add / edit  -> POST /api/users  or  PUT /api/users/{id}
 *      (body = CreateUserRequest; a blank password keeps the old hash)
 *   5. enable/disable -> PUT /api/users/{id}/status  { "status": "ACTIVE" }
 *      (AccountStatus constants: ACTIVE | INACTIVE)
 *   6. delete      -> DELETE /api/users/{id}
 *
 * UserInfo never contains a password field, so nothing here can render one.
 * ========================================================================== */

(function () {
  'use strict';

  const API = '/users';

  let table = null;
  let editingId = null;

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('users.html', 'Users');

    /* The API is ADMIN only (@RequireRoles(Role.ADMIN)) - mirror it here. */
    if (!Auth.can('users.read')) {
      document.getElementById('accessDenied').classList.remove('d-none');
      return;
    }

    const canWrite = Auth.can('users.write');
    document.getElementById('tableMount').classList.remove('d-none');

    table = new CrudTable({
      mount: '#tableMount',
      apiPath: API,
      searchPlaceholder: 'Search by username, full name or email…',
      emptyTitle: 'No accounts match your search',
      emptyDetail: 'Clear the filters, or add a new account.',
      createLabel: 'Add account',
      canCreate: canWrite,
      pageSizes: [5, 10, 25, 50],

      filters: [
        { name: 'role', label: 'Role' },
        { name: 'status', label: 'Status' }
      ],

      sortOptions: [
        { value: 'fullName:asc', label: 'Name (A → Z)' },
        { value: 'fullName:desc', label: 'Name (Z → A)' },
        { value: 'username:asc', label: 'Username (A → Z)' },
        { value: 'email:asc', label: 'Email (A → Z)' },
        { value: 'role:asc', label: 'Role' },
        { value: 'status:asc', label: 'Status' },
        { value: 'createdAt:desc', label: 'Newest accounts' }
      ],
      defaultSort: 'fullName:asc',

      columns: [
        { key: 'username', label: 'Username', render: (v) => codeBadge(v, 'bg-dark') },
        {
          key: 'fullName',
          label: 'Account',
          render: (v, row) => `
            <span class="cell-strong">${escapeHtml(v || '')}</span>
            <span class="cell-sub">${escapeHtml(row.email || '')}</span>`
        },
        { key: 'role', label: 'Role', render: (v) => statusBadge(v) },
        { key: 'status', label: 'Status', render: (v) => statusBadge(v) }
      ],

      /* UserInfo has no last-login column - "created" is the newest sort key. */
      onEdit: canWrite ? (row) => openForm(row) : null,
      onDelete: canWrite ? remove : null,
      onCustom: canWrite ? onCustom : null,
      customActions: [
        { key: 'toggle', icon: 'bi-power', title: 'Enable or disable account', color: 'secondary' }
      ],
      onCreate: canWrite ? () => openForm(null) : null
    });

    /* static filter options - the repository supports ?role= and ?status= */
    table.setFilterOptions('role',
      ['ADMIN', 'FACULTY', 'STUDENT'].map((r) => ({ value: r, label: roleLabel(r) })));
    table.setFilterOptions('status',
      ['ACTIVE', 'INACTIVE'].map((s) => ({ value: s, label: labelify(s) })));

    document.getElementById('userForm').addEventListener('submit', save);
    await table.refresh();
  }

  /* ------------------------------------------------------------- helpers */

  function roleLabel(role) {
    const labels = { ADMIN: 'Administrator', FACULTY: 'Faculty', STUDENT: 'Student' };
    return labels[role] || labelify(role);
  }

  function isSelf(row) {
    const me = Auth.currentUser() || {};
    return row && me.id !== undefined && me.id !== null && String(row.id) === String(me.id);
  }

  function val(name) {
    const el = document.querySelector(`#userForm [name="${name}"]`);
    return el ? String(el.value).trim() : '';
  }

  function clearInvalid() {
    document.querySelectorAll('#userForm .is-invalid')
      .forEach((el) => el.classList.remove('is-invalid'));
  }

  /* ------------------------------------------------------------ ADD/EDIT */

  function openForm(row) {
    editingId = row ? row.id : null;
    const form = document.getElementById('userForm');
    resetForm(form);
    clearInvalid();

    document.getElementById('formTitle').textContent = row ? 'Edit account' : 'Add account';
    document.getElementById('saveLabel').textContent = row ? 'Update account' : 'Save account';

    const passwordInput = document.getElementById('password');
    passwordInput.required = !row;
    document.getElementById('passwordHint').textContent = row
      ? 'Leave blank to keep the current password.'
      : 'Required when creating an account.';

    document.getElementById('status').value = 'ACTIVE';

    if (row) {
      ['username', 'email', 'fullName', 'role', 'status'].forEach((field) => {
        const input = form.querySelector(`[name="${field}"]`);
        if (input && row[field] !== undefined && row[field] !== null) {
          input.value = row[field];
        }
      });
    }

    openModal('formModal');
    setTimeout(() => document.getElementById('username').focus(), 400);
  }

  async function save(event) {
    event.preventDefault();
    const form = event.currentTarget;
    clearInvalid();

    if (!form.checkValidity()) {
      form.classList.add('was-validated');
      return;
    }

    const username = val('username').toLowerCase();
    const password = val('password');

    if (!editingId && !password) {
      showFieldErrors(form, { password: 'Password is required for a new account.' });
      toastError('Enter a password for the new account.', 'Password required');
      return;
    }
    if (password && password.length < 6) {
      showFieldErrors(form, { password: 'Password must be at least 6 characters.' });
      return;
    }
    if (!/^[a-z0-9._-]{3,80}$/.test(username)) {
      showFieldErrors(form, {
        username: 'Use letters, digits, dot, underscore or hyphen (3–80 characters).'
      });
      return;
    }

    const payload = {
      username: username,
      email: val('email'),
      fullName: val('fullName'),
      role: val('role'),
      status: val('status') || 'ACTIVE',
      /* JSON null (not "") so @Size on the DTO is skipped when editing */
      password: password ? password : null
    };

    await withBusy(document.getElementById('saveBtn'), async () => {
      try {
        if (editingId) {
          const updated = await api.put(`${API}/${editingId}`, payload);
          toastSuccess(`${updated.fullName} was updated.`, 'Account updated');
        } else {
          const created = await api.post(API, payload);
          toastSuccess(`${created.fullName} can now sign in as ${created.username}.`,
            'Account created');
        }
        closeModal('formModal');
        await table.refresh();
      } catch (error) {
        if (error.errors) showFieldErrors(form, error.errors);
        toastError(errorMessage(error, 'The account could not be saved.'));
      }
    }, 'Saving…');
  }

  /* ------------------------------------------------- enable / disable */

  async function onCustom(key, row) {
    if (key === 'toggle') await toggleStatus(row);
  }

  async function toggleStatus(row) {
    if (isSelf(row)) {
      toastWarning('You cannot disable your own account - ask another administrator.',
        'Not allowed');
      return;
    }

    const enabling = row.status === 'INACTIVE';
    const nextStatus = enabling ? 'ACTIVE' : 'INACTIVE';

    const confirmed = await confirmDialog({
      title: enabling ? 'Enable account' : 'Disable account',
      message: enabling
        ? `Allow ${row.fullName} (${row.username}) to sign in again?`
        : `Block ${row.fullName} (${row.username}) from signing in? Open sessions of this
           account are closed immediately.`,
      confirmText: enabling ? 'Enable account' : 'Disable account',
      confirmClass: enabling ? 'btn-primary' : 'btn-danger'
    });
    if (!confirmed) return;

    try {
      const updated = await api.put(`${API}/${row.id}/status`, { status: nextStatus });
      toastSuccess(
        `${updated.fullName} is now ${updated.status === 'ACTIVE' ? 'active' : 'inactive'}.`,
        enabling ? 'Account enabled' : 'Account disabled');
      await table.refresh();
    } catch (error) {
      toastError(errorMessage(error, 'The account status could not be changed.'));
    }
  }

  /* ------------------------------------------------------------- DELETE */

  async function remove(row) {
    if (isSelf(row)) {
      toastWarning('You cannot delete your own account. The server rejects this as well.',
        'Not allowed');
      return;
    }

    const confirmed = await confirmDialog({
      title: 'Delete account',
      message: `Delete ${row.fullName} (${row.username})? The login is removed permanently
                and cannot be undone.`,
      confirmText: 'Yes, delete',
      confirmClass: 'btn-danger'
    });
    if (!confirmed) return;

    try {
      await api.del(`${API}/${row.id}`);
      toastSuccess(`${row.fullName} was deleted.`, 'Account deleted');
      await table.refresh();
    } catch (error) {
      toastError(errorMessage(error, 'The account could not be deleted.'));
    }
  }
})();
