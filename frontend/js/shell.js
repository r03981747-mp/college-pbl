/* ============================================================================
 * smart-campus :: shell.js
 * ----------------------------------------------------------------------------
 * Renders the sidebar, top bar and footer that every application page shares.
 *
 * A page only needs this markup:
 *
 *   <div class="app-shell">
 *     <aside class="sidebar" id="sidebar"></aside>
 *     <div class="overlay" id="sidebarOverlay"></div>
 *     <div class="main-area">
 *       <header class="topbar" id="topbar"></header>
 *       <main class="content"> ...page content... </main>
 *       <footer class="app-footer" id="appFooter"></footer>
 *     </div>
 *   </div>
 *
 * and then calls:
 *
 *   AppShell.init('students.html');
 *
 * The menu is declared once, here, so adding a page means adding one row.
 * Items marked with `roles` are hidden for other roles - a UI convenience;
 * the API would answer 403 regardless.
 * ========================================================================== */

const AppShell = (() => {

  /**
   * Single source of truth for the navigation.
   *
   * `desc` and `stat` are used by the dashboard module cards only:
   *   - `desc`      one-line explanation shown under the card title
   *   - `stat`      field name inside GET /api/dashboard/statistics
   *   - `statLabel` unit shown next to that real number
   * A module without `stat` simply shows icon + title + description - no
   * invented numbers. Because the cards are built from this very array the
   * sidebar and the dashboard can never drift apart or duplicate a page.
   */
  const MENU = [
    { section: 'Overview' },
    { key: 'index.html', label: 'Dashboard', icon: 'bi-grid-1x2-fill', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Live overview of the whole campus.' },

    { section: 'Academics' },
    { key: 'students.html', label: 'Students', icon: 'bi-people-fill', roles: 'ADMIN,FACULTY',
      desc: 'Student register, departments and semester intake.',
      stat: 'totalStudents', statLabel: 'students enrolled' },
    { key: 'faculty.html', label: 'Faculty', icon: 'bi-person-badge-fill', roles: 'ADMIN,FACULTY',
      desc: 'Teaching staff, designations and departments.',
      stat: 'totalFaculty', statLabel: 'teaching members' },
    { key: 'courses.html', label: 'Courses', icon: 'bi-journal-bookmark-fill', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Curriculum, course codes and running status.',
      stat: 'totalCourses', statLabel: 'courses offered' },
    { key: 'marks.html', label: 'Marks', icon: 'bi-clipboard-data-fill', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Published results, assessment entry and release.' },
    { key: 'attendance.html', label: 'Attendance', icon: 'bi-calendar2-check-fill', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Daily register, corrections and attendance history.' },
    { key: 'assignments.html', label: 'Assignments', icon: 'bi-journal-check', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Coursework, secure uploads and faculty review.' },
    { key: 'profile.html', label: 'Academic Profile', icon: 'bi-person-vcard-fill', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Marks, attendance, assignments and mentor overview.' },
    { key: 'eligibility.html', label: 'Eligibility', icon: 'bi-patch-check-fill', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Calculated eligibility status with clear reasons.' },
    { key: 'academic-settings.html', label: 'Academic Setup', icon: 'bi-sliders', roles: 'ADMIN',
      desc: 'Eligibility rules and class teacher/mentor setup.' },

    { section: 'Infrastructure' },
    { key: 'classrooms.html', label: 'Classrooms', icon: 'bi-building', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Room inventory, capacity and current status.',
      stat: 'totalClassrooms', statLabel: 'classrooms' },
    { key: 'laboratories.html', label: 'Laboratories', icon: 'bi-cpu-fill', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Lab infrastructure, in-charge and utilisation.',
      stat: 'totalLaboratories', statLabel: 'laboratories' },
    { key: 'equipment.html', label: 'Equipment', icon: 'bi-tools', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Assets, condition and maintenance records.',
      stat: 'totalEquipment', statLabel: 'items tracked' },

    { section: 'Campus life' },
    { key: 'activities.html', label: 'Activities', icon: 'bi-calendar2-event-fill', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Campus events, categories and schedules.',
      stat: 'upcomingActivities', statLabel: 'upcoming events' },
    { key: 'bookings.html', label: 'Bookings', icon: 'bi-calendar2-check-fill', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Reserve classrooms, labs and equipment.',
      stat: 'activeBookings', statLabel: 'active bookings' },

    { section: 'Administration' },
    { key: 'reports.html', label: 'Reports', icon: 'bi-bar-chart-line-fill', roles: 'ADMIN,FACULTY',
      desc: 'Live aggregates and printable summaries.' },
    { key: 'users.html', label: 'Users', icon: 'bi-shield-lock-fill', roles: 'ADMIN',
      desc: 'Accounts, roles and access control.' },
    { key: 'settings.html', label: 'Settings', icon: 'bi-gear-fill', roles: 'ADMIN,FACULTY,STUDENT',
      desc: 'Profile, password and system preferences.' }
  ];

  /** Menu entries a role may open, dashboard excluded (it is the current page). */
  function modulesFor(role, excludePage) {
    return MENU.filter((item) => !item.section && item.key !== excludePage)
      .filter((item) => !item.roles || item.roles.split(',').indexOf(role) !== -1);
  }

  let currentPage = 'index.html';

  /* ------------------------------------------------------------ sidebar */

  function sidebarHtml() {
    const user = Auth.currentUser() || {};
    const role = user.role || '';

    const items = MENU.map((item) => {
      if (item.section) {
        return `<div class="nav-section">${item.section}</div>`;
      }
      if (item.roles && item.roles.split(',').indexOf(role) === -1) return '';
      return `
        <a class="nav-link" href="${item.key}" data-page="${item.key}">
          <i class="bi ${item.icon}"></i>
          <span>${item.label}</span>
        </a>`;
    }).join('');

    const initials = (user.fullName || user.email || '?')
      .split(/[\s@.]+/).filter(Boolean).slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase()).join('');

    return `
      <div class="sidebar-brand">
        <div class="brand-mark"><i class="bi bi-mortarboard-fill"></i></div>
        <div class="brand-text">
          <h2>Smart Campus</h2>
          <span>INTEGRATED MGMT SYSTEM</span>
        </div>
      </div>

      <div class="sidebar-user">
        <div class="avatar">${escapeHtml(initials)}</div>
        <div class="who">
          <strong data-user-name>${escapeHtml(user.fullName || '—')}</strong>
          <small data-user-email>${escapeHtml(user.email || '')}</small>
        </div>
        <span class="role-pill" data-user-role>${escapeHtml(role)}</span>
      </div>

      <nav class="sidebar-nav">${items}</nav>

      <div class="sidebar-footer">
        <button type="button" class="logout-btn" data-action="logout">
          <i class="bi bi-box-arrow-right"></i> Sign out
        </button>
      </div>`;
  }

  /* ------------------------------------------------------------- topbar */

  function topbarHtml(title) {
    const user = Auth.currentUser() || {};
    const initials = (user.fullName || user.email || '?')
      .split(/[\s@.]+/).filter(Boolean).slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase()).join('');

    /* every module page gets a "back to dashboard" link in the breadcrumb;
       it reuses the plain <a href> routing the sidebar already uses. */
    const backLink = currentPage === 'index.html' ? '' : `
        <a href="index.html" class="crumb-back" title="Back to dashboard">
          <i class="bi bi-arrow-left"></i>
        </a>`;

    return `
      <button type="button" class="menu-toggle" id="menuToggle" aria-label="Toggle navigation">
        <i class="bi bi-list"></i>
      </button>

      <div class="crumbs">${backLink} Smart Campus &nbsp;/&nbsp; <strong>${escapeHtml(title)}</strong></div>

      <div class="topbar-right">
        <div class="search-box">
          <i class="bi bi-search"></i>
          <input type="search" id="globalSearch" placeholder="Search students, courses, bookings..."
                 autocomplete="off">
          <div id="globalSearchResults" class="dropdown-menu shadow w-100 p-0 overflow-hidden"
               style="display:none"></div>
        </div>

        <button class="icon-btn" type="button" data-action="refresh-page" title="Refresh page data">
          <i class="bi bi-arrow-clockwise"></i>
        </button>

        <div class="topbar-user" title="Signed in">
          <div class="meta text-end d-none d-sm-block">
            <strong>${escapeHtml(user.fullName || '—')}</strong>
            <span>${escapeHtml(user.role || '')}</span>
          </div>
          <div class="avatar">${escapeHtml(initials)}</div>
        </div>
      </div>`;
  }

  function footerHtml() {
    const year = new Date().getFullYear();
    return `
      <span>&copy; ${year} Smart Campus Integrated Management System</span>
      <span>Spring Boot 3 &nbsp;·&nbsp; MySQL 8 &nbsp;·&nbsp; Bootstrap 5 &nbsp;·&nbsp;
            REST API at <code>/api</code></span>`;
  }

  /* ------------------------------------------------------ global search */

  function bindGlobalSearch() {
    const input = document.getElementById('globalSearch');
    const results = document.getElementById('globalSearchResults');
    if (!input || !results) return;

    const run = debounce(async () => {
      const keyword = input.value.trim();
      if (keyword.length < 2) { results.style.display = 'none'; return; }

      const groups = [
        { label: 'Students', path: '/students/search', page: 'students.html', roles: ['ADMIN', 'FACULTY'],
          render: (r) => `${escapeHtml(r.name || '')} <small class="text-muted">${escapeHtml(r.usn || '')}</small>` },
        { label: 'Courses', path: '/courses/search', page: 'courses.html', roles: ['ADMIN', 'FACULTY', 'STUDENT'],
          render: (r) => `${escapeHtml(r.courseName || r.name || '')} <small class="text-muted">${escapeHtml(r.courseCode || '')}</small>` },
        { label: 'Bookings', path: '/bookings/search', page: 'bookings.html', roles: ['ADMIN', 'FACULTY', 'STUDENT'],
          render: (r) => `${escapeHtml(r.bookingRef || '')} <small class="text-muted">${escapeHtml(r.resourceName || '')}</small>` }
      ].filter((g) => g.roles.indexOf(Auth.role()) !== -1);

      const found = await Promise.all(groups.map(async (group) => {
        try {
          const rows = await apiGet(group.path, { keyword, limit: 4 });
          return { group, rows: Array.isArray(rows) ? rows : [] };
        } catch (e) {
          return { group, rows: [] };
        }
      }));

      const html = found
        .filter((entry) => entry.rows.length)
        .map((entry) => `
          <div class="py-1">
            <div class="px-3 py-1 text-uppercase fw-bold"
                 style="font-size:.62rem;color:#94a3b8;background:#f8fafc">${entry.group.label}</div>
            ${entry.rows.map((row, i) => `
              <a class="dropdown-item d-block px-3 py-2 small border-bottom-0"
                 href="${entry.group.page}?q=${encodeURIComponent(keyword)}">
                ${entry.group.render(row)}
              </a>`).join('')}
          </div>`).join('');

      results.innerHTML = html ||
        '<div class="px-3 py-3 small text-secondary">No matches found.</div>';
      results.style.display = 'block';
    }, 320);

    input.addEventListener('input', run);
    input.addEventListener('focus', run);
    document.addEventListener('click', (event) => {
      if (!event.target.closest('.search-box')) results.style.display = 'none';
    });
  }

  /* ------------------------------------------------------------- sign out */
  /* A light-switch confirmation that belongs to the same story as the login
     cord: a warm bulb, a short prompt, and a dimming exit before the existing
     POST /auth/logout runs.  Native <dialog> gives us the modal semantics,
     backdrop, Escape handling and focus containment. */

  let signOutPromise = null;

  function firstName() {
    const user = Auth.currentUser() || {};
    const full = String(user.fullName || user.email || '').trim();
    const parts = full.split(/\s+/).filter(Boolean);
    const titled = parts.length > 1 && /^(dr|prof|mr|mrs|ms|miss)\.?$/i.test(parts[0]);
    return parts[titled ? 1 : 0] || '';
  }

  function createSignOutDialog() {
    const name = firstName();
    const greeting = name ? `, ${escapeHtml(name)}` : '';

    const dialog = document.createElement('dialog');
    dialog.className = 'signout-dialog';
    dialog.setAttribute('aria-labelledby', 'signoutTitle');
    dialog.setAttribute('aria-describedby', 'signoutDesc');
    dialog.innerHTML = `
      <div class="signout-card">
        <button type="button" class="signout-close" data-signout-cancel
                aria-label="Close and stay signed in">
          <i class="bi bi-x-lg" aria-hidden="true"></i>
        </button>

        <div class="signout-bulb-stage" aria-hidden="true">
          <span class="signout-cord"></span>
          <span class="signout-bulb-glow"></span>
          <span class="signout-bulb"></span>
        </div>

        <span class="signout-eyebrow">
          <i class="bi bi-lightbulb-fill" aria-hidden="true"></i> Session switch
        </span>
        <h2 id="signoutTitle">Ready to switch off and sign out${greeting}?</h2>
        <p id="signoutDesc">
          Your secure session will end and you will return to the sign-in room.
          Nothing in the campus database is deleted.
        </p>

        <div class="signout-actions">
          <button type="button" class="btn signout-stay" data-signout-cancel>
            Stay signed in
          </button>
          <button type="button" class="btn signout-confirm" data-signout-confirm>
            Sign out <i class="bi bi-box-arrow-right" aria-hidden="true"></i>
          </button>
        </div>

        <p class="signout-note">You can sign back in at any time.</p>
      </div>`;
    return dialog;
  }

  function openSignOutDialog(trigger) {
    return new Promise((resolve) => {
      /* Use the actual Sign out control as the focus-return target; a pointer
         click does not always focus the button in every browser. */
      const opener = trigger || document.activeElement;
      const dialog = createSignOutDialog();
      const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const closeBtn = dialog.querySelector('.signout-close');
      const stayBtn = dialog.querySelector('.signout-stay');
      const confirmBtn = dialog.querySelector('[data-signout-confirm]');

      let settled = false;
      let firing = false;

      const finish = (confirmed) => {
        if (settled) return;
        settled = true;
        if (typeof dialog.close === 'function' && dialog.open) dialog.close();
        else dialog.removeAttribute('open');
        dialog.remove();
        if (!confirmed && opener && typeof opener.focus === 'function') opener.focus();
        resolve(confirmed);
      };

      dialog.querySelectorAll('[data-signout-cancel]').forEach((button) => {
        button.addEventListener('click', () => { if (!firing) finish(false); });
      });

      confirmBtn.addEventListener('click', () => {
        if (firing) return;
        firing = true;
        dialog.setAttribute('aria-busy', 'true');
        closeBtn.disabled = true;
        stayBtn.disabled = true;
        confirmBtn.disabled = true;

        /* The CSS animation is a bonus.  The timer below always runs, so a
           browser with animations switched off (or a failed effect) can never
           hold the logout hostage. */
        document.documentElement.classList.add('is-signing-out');
        dialog.classList.add('is-firing');

        window.setTimeout(() => finish(true), reduced ? 40 : 720);
      });

      /* Escape and the backdrop both stay signed in. */
      dialog.addEventListener('cancel', (event) => {
        event.preventDefault();
        if (!firing) finish(false);
      });
      dialog.addEventListener('click', (event) => {
        if (event.target === dialog && !firing) finish(false);
      });

      /* explicit focus trap in addition to the native modal behaviour */
      dialog.addEventListener('keydown', (event) => {
        if (event.key !== 'Tab') return;
        const items = Array.from(dialog.querySelectorAll(
          'button:not([disabled]), [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        )).filter((el) => el.getClientRects().length > 0);
        if (!items.length) return;

        const first = items[0];
        const last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      });

      document.body.appendChild(dialog);
      if (typeof dialog.showModal === 'function') dialog.showModal();
      else dialog.setAttribute('open', '');
      stayBtn.focus();                       /* safe default for a destructive action */
    });
  }

  function requestSignOut(trigger) {
    if (signOutPromise) return signOutPromise;
    signOutPromise = (async () => {
      try {
        const confirmed = await openSignOutDialog(trigger);
        if (!confirmed) return;
        await Auth.logout();                 /* unchanged server + session flow */
      } catch (error) {
        document.documentElement.classList.remove('is-signing-out');
        toastError(errorMessage(error, 'We could not complete the sign-out.'), 'Sign out failed');
      } finally {
        signOutPromise = null;
      }
    })();
    return signOutPromise;
  }

  /* ----------------------------------------------------------- sidebar UX */

  function bindSidebar() {
    const toggle = document.getElementById('menuToggle');
    const overlay = document.getElementById('sidebarOverlay');
    const sidebar = document.getElementById('sidebar');

    const close = () => {
      if (!sidebar) return;
      sidebar.classList.remove('open');
      if (overlay) overlay.classList.remove('show');
    };

    if (toggle) {
      toggle.addEventListener('click', () => {
        const open = sidebar.classList.toggle('open');
        if (overlay) overlay.classList.toggle('show', open);
      });
    }
    if (overlay) overlay.addEventListener('click', close);

    /* highlight the current page */
    document.querySelectorAll('.nav-link[data-page]').forEach((link) => {
      link.classList.toggle('active', link.dataset.page === currentPage);
      if (link.dataset.page === currentPage) link.setAttribute('aria-current', 'page');
    });

    /* sign out (delegated so it works for the sidebar button too) */
    document.querySelectorAll('[data-action="logout"]').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.preventDefault();
        requestSignOut(button);
      });
    });

    /* header refresh button */
    document.querySelectorAll('[data-action="refresh-page"]').forEach((button) => {
      button.addEventListener('click', () => {
        toastInfo('Reloading the page data...', 'Refresh');
        location.reload();
      });
    });
  }

  /* ---------------------------------------------------------------- init */

  /**
   * Builds the shell of a protected page.
   * @param {string} pageKey   file name, e.g. "students.html"
   * @param {string} title     heading shown in the breadcrumb
   */
  function init(pageKey, title) {
    currentPage = pageKey || 'index.html';

    const sidebar = document.getElementById('sidebar');
    const topbar = document.getElementById('topbar');
    const footer = document.getElementById('appFooter');

    if (sidebar) sidebar.innerHTML = sidebarHtml();
    if (topbar) topbar.innerHTML = topbarHtml(title || 'Dashboard');
    if (footer) footer.innerHTML = footerHtml();

    /* mark the page header title as well */
    document.querySelectorAll('[data-page-title]').forEach((el) => {
      el.textContent = title || 'Dashboard';
    });

    bindSidebar();
    bindGlobalSearch();
  }

  return { init, MENU, modulesFor, requestSignOut };
})();
