/* ============================================================================
 * smart-campus :: settings.js
 * ----------------------------------------------------------------------------
 * Self-service settings - available to every signed-in role.
 *
 *   1. guard the page  -> Auth.requireAuth()
 *   2. paint the shell -> AppShell.init('settings.html', 'Settings')
 *   3. change password -> POST /api/auth/change-password
 *        body = ChangePasswordRequest { currentPassword, newPassword }
 *        (confirmPassword is checked in the browser only - the DTO has 2 fields)
 *   4. my account      -> GET /api/auth/me      { user, permissions }
 *   5. session         -> GET /api/auth/status  { authenticated, user, permissions }
 *   6. sign out        -> confirmDialog -> Auth.logout()
 * ========================================================================== */

(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;

    AppShell.init('settings.html', 'Settings');

    document.getElementById('passwordForm').addEventListener('submit', changePassword);
    document.getElementById('sessionRefresh').addEventListener('click', loadStatus);
    document.getElementById('signOutBtn').addEventListener('click', signOut);

    await Promise.all([loadAccount(), loadStatus()]);
    renderSystem();
  }

  /* ------------------------------------------------------ change password */

  async function changePassword(event) {
    event.preventDefault();
    const form = event.currentTarget;
    form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));

    const current = document.getElementById('currentPassword').value;
    const next = document.getElementById('newPassword').value;
    const confirm = document.getElementById('confirmPassword').value;

    if (!current) {
      showFieldErrors(form, { currentPassword: 'Your current password is required.' });
      return;
    }
    if (next.length < 6) {
      showFieldErrors(form, { newPassword: 'The new password must be at least 6 characters.' });
      return;
    }
    if (next === current) {
      showFieldErrors(form, { newPassword: 'The new password must differ from the current one.' });
      return;
    }
    if (next !== confirm) {
      showFieldErrors(form, { confirmPassword: 'Both new passwords must match.' });
      return;
    }

    await withBusy(document.getElementById('changePasswordBtn'), async () => {
      try {
        await api.post('/auth/change-password', {
          currentPassword: current,
          newPassword: next
        });

        toastSuccess('Your password was changed. Please sign in again with the new password.',
          'Password changed');
        form.reset();

        setTimeout(() => { location.href = 'login.html?signedout=1'; }, 1500);
      } catch (error) {
        if (error.errors) showFieldErrors(form, error.errors);
        toastError(errorMessage(error, 'The password could not be changed.'));
      }
    }, 'Changing…');
  }

  /* ---------------------------------------------------------- my account */

  async function loadAccount() {
    const mount = document.getElementById('accountMount');
    mount.innerHTML = loadingBlock('Loading your account…');

    try {
      const data = await api.get('/auth/me');
      const account = (data && data.user) || {};
      const permissions = (data && data.permissions) || [];

      const rows = [
        ['Username', `<code>${escapeHtml(account.username || '—')}</code>`],
        ['Full name', escapeHtml(account.fullName || '—')],
        ['Email', escapeHtml(account.email || '—')],
        ['Role', statusBadge(account.role)],
        ['Account status', statusBadge(account.status)],
        ['Permission keys', `<strong>${formatNumber(permissions.length)}</strong>`]
      ];

      mount.innerHTML = `
        <div class="kv-list">
          ${rows.map(([key, value]) =>
            `<div class="kv-row"><span class="k">${key}</span><span class="v">${value}</span></div>`
          ).join('')}
        </div>`;
    } catch (error) {
      mount.innerHTML = errorBlock(errorMessage(error, 'Your account could not be loaded.'));
      mount.querySelector('[data-action="retry"]')
        ?.addEventListener('click', loadAccount);
    }
  }

  /* ------------------------------------------------------------- session */

  async function loadStatus() {
    const mount = document.getElementById('sessionMount');
    mount.innerHTML = loadingBlock('Checking the session…');

    try {
      const status = await api.get('/auth/status');
      const authenticated = !!(status && status.authenticated);
      const account = (status && status.user) || {};
      const permissions = (status && status.permissions) || [];
      const hasToken = !!getToken();

      const rows = [
        ['Authenticated', authenticated
          ? statusBadge('ACTIVE', 'Yes - session is active')
          : statusBadge('DISABLED', 'No active session')],
        ['Signed in as', escapeHtml(
          account.fullName || account.username || (authenticated ? '—' : 'Nobody signed in'))],
        ['Role', account.role ? statusBadge(account.role) : '—'],
        ['Permission keys', authenticated ? formatNumber(permissions.length) : '—'],
        ['Token in this browser', hasToken ? 'Yes' : 'No']
      ];

      mount.innerHTML = `
        <div class="kv-list">
          ${rows.map(([key, value]) =>
            `<div class="kv-row"><span class="k">${key}</span><span class="v">${value}</span></div>`
          ).join('')}
        </div>`;
    } catch (error) {
      mount.innerHTML = errorBlock(errorMessage(error, 'The session could not be read.'));
      mount.querySelector('[data-action="retry"]')
        ?.addEventListener('click', loadStatus);
    }
  }

  /* --------------------------------------------------------------- exit */

  async function signOut() {
    const confirmed = await confirmDialog({
      title: 'Sign out',
      message: 'Sign out of the Smart Campus portal now?',
      confirmText: 'Yes, sign out',
      confirmClass: 'btn-danger'
    });
    if (confirmed) await Auth.logout();
  }

  /* -------------------------------------------------- system information */

  function renderSystem() {
    const mount = document.getElementById('systemMount');
    const account = Auth.currentUser() || {};
    const permissions = getCachedPermissions();

    const rows = [
      ['Application', 'Smart Campus Integrated Management System'],
      ['API base', `<code>${escapeHtml(API_BASE)}</code>`],
      ['Page origin', `<code>${escapeHtml(location.origin)}</code>`],
      ['Current date and time', `<span id="sysClock">${escapeHtml(nowText())}</span>`],
      ['Signed-in role', account.role ? statusBadge(account.role) : '—']
    ];

    mount.innerHTML = `
      <div class="kv-list">
        ${rows.map(([key, value]) =>
          `<div class="kv-row"><span class="k">${key}</span><span class="v">${value}</span></div>`
        ).join('')}
      </div>

      <div class="divider"></div>

      <div class="small text-secondary mb-2">
        Permission keys granted to this session
        <strong>(${formatNumber(permissions.length)})</strong>:
      </div>
      <div class="d-flex flex-wrap gap-2">
        ${permissions.length
          ? permissions.map((key) =>
              `<span class="chip"><code>${escapeHtml(key)}</code></span>`).join('')
          : '<span class="text-secondary small">No permission keys cached in this browser.</span>'}
      </div>`;

    /* keeps the clock honest without any extra button */
    if (!window.__settingsClock) {
      window.__settingsClock = setInterval(() => {
        const clock = document.getElementById('sysClock');
        if (clock) clock.textContent = nowText();
      }, 1000);
    }
  }

  function nowText() {
    const now = new Date();
    const pad = (value) => String(value).padStart(2, '0');
    return `${formatDate(now.toISOString())}, ` +
      `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
  }
})();
