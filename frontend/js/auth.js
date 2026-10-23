/* ============================================================================
 * smart-campus :: auth.js
 * ----------------------------------------------------------------------------
 * UI half of the authentication layer.
 *
 * IMPORTANT - what this file does is CONVENIENCE ONLY, never security.
 * The real decisions (is this token valid? may this role open /api/students?)
 * are taken by the Spring Boot interceptor and the @RequireRoles annotations.
 * This file only:
 *   - calls POST /api/auth/login to obtain a token
 *   - stores the token and the user shown in the sidebar
 *   - redirects visitors of protected pages to login.html
 *   - hides menu items the current role would be denied anyway (403)
 * ========================================================================== */

const Auth = (() => {
  let cachedUser = null;
  let cachedPermissions = [];
  let readyPromise = null;

  /* ------------------------------------------------------------ session API */

  /** Asks the server whether there is a live session (never throws). */
  async function status() {
    try {
      const data = await apiRequest('/auth/status');
      return data || { authenticated: false };
    } catch (e) {
      return { authenticated: false };
    }
  }

  /** Full profile of the signed-in user (role comes from the database). */
  async function me() {
    const data = await apiRequest('/auth/me');
    cachedUser = data.user;
    cachedPermissions = data.permissions || [];
    saveSession(getToken(), cachedUser, cachedPermissions, isRemembered());
    return data;
  }

  /** Sign in. Throws ApiError with a friendly message on bad credentials. */
  async function login(email, password, remember) {
    const data = await apiRequest('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: email, password: password, rememberMe: !!remember })
    });

    saveSession(data.token, data.user, data.permissions, !!remember);
    cachedUser = data.user;
    cachedPermissions = data.permissions || [];
    return data;
  }

  /** Sign out on the server, then wipe the browser session. */
  async function logout(silent) {
    try {
      if (getToken()) await apiRequest('/auth/logout', { method: 'POST' });
    } catch (e) {
      /* already gone server side - ignore */
    }
    clearSession();
    cachedUser = null;
    cachedPermissions = [];
    if (!silent) location.href = 'login.html?signedout=1';
  }

  function isRemembered() {
    return !!localStorage.getItem(TOKEN_KEY);
  }

  /* --------------------------------------------------------- page bootstrap */

  /**
   * Guard for every protected page.
   * Redirects to login.html when there is no session.
   * @returns {Promise<null|object>} null when redirected, otherwise the user
   */
  function requireAuth() {
    const token = getToken();
    if (!token) {
      location.href = 'login.html';
      return Promise.resolve(null);
    }
    return me()
      .then((data) => data.user)
      .catch((error) => {
        if (error.status === 401 || error.status === 403) {
          clearSession();
          location.href = 'login.html?expired=1';
        }
        return null;
      });
  }

  /** Resolves the current user from cache (used by role-aware rendering). */
  function currentUser() {
    return cachedUser || getCachedUser();
  }

  function role() {
    const user = currentUser();
    return user ? user.role : null;
  }

  /** True when the server reported this permission key. UI hint only. */
  function can(permissionKey) {
    if (!cachedPermissions.length) cachedPermissions = getCachedPermissions();
    return cachedPermissions.indexOf(permissionKey) !== -1;
  }

  /** True for ADMIN and FACULTY - drives the "Add" button on most pages. */
  function canWrite(moduleName) {
    return can(moduleName + '.create') || can(moduleName + '.update');
  }

  function isAdmin() {
    return role() === 'ADMIN';
  }

  /* -------------------------------------------------------------- app shell */

  /**
   * Fills the sidebar/header of a protected page:
   * user avatar + name + role badge, active menu link, logout button.
   *
   * @param {string} pageKey file name of the current page, e.g. "students.html"
   */
  function initShell(pageKey) {
    const user = currentUser();
    if (!user) return;

    const nameEls = document.querySelectorAll('[data-user-name]');
    nameEls.forEach((el) => { el.textContent = user.fullName || user.email; });

    const emailEls = document.querySelectorAll('[data-user-email]');
    emailEls.forEach((el) => { el.textContent = user.email; });

    const roleEls = document.querySelectorAll('[data-user-role]');
    roleEls.forEach((el) => { el.textContent = user.role; });

    const moduleEls = document.querySelectorAll('[data-user-module]');
    moduleEls.forEach((el) => { el.textContent = user.department || 'Smart Campus'; });

    /* initials avatar, e.g. "Rahul Gowda" -> "RG" */
    const initials = (user.fullName || user.email || '?')
      .split(/[\s@.]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('');
    document.querySelectorAll('[data-user-initials]').forEach((el) => { el.textContent = initials; });

    /* active link in the sidebar */
    document.querySelectorAll('.nav-link[data-page]').forEach((link) => {
      link.classList.toggle('active', link.dataset.page === pageKey);
    });

    /* role-specific visibility: hidden menu items are a UI convenience,
       the API would answer 403 anyway if they were opened directly. */
    document.querySelectorAll('[data-roles]').forEach((el) => {
      const allowed = el.dataset.roles.split(',').map((s) => s.trim());
      el.classList.toggle('d-none', allowed.indexOf(role()) === -1);
    });

    /* logout button - the shared shell supplies the light-switch dialog;
       the generic confirm remains only as a fallback for a page without it */
    document.querySelectorAll('[data-action="logout"]').forEach((btn) => {
      btn.addEventListener('click', async (event) => {
        event.preventDefault();
        if (typeof AppShell !== 'undefined' && typeof AppShell.requestSignOut === 'function') {
          AppShell.requestSignOut(btn);
          return;
        }
        const confirmed = await confirmDialog({
          title: 'Sign out',
          message: 'Do you want to sign out of the Smart Campus portal?',
          confirmText: 'Yes, sign out',
          confirmClass: 'btn-danger'
        });
        if (confirmed) await logout();
      });
    });
  }

  /** Reads ?name=value from the URL (used by login.html for messages). */
  function urlParam(name) {
    return new URLSearchParams(location.search).get(name);
  }

  return {
    status, me, login, logout, requireAuth,
    currentUser, role, can, canWrite, isAdmin,
    initShell, urlParam
  };
})();
