/* ============================================================================
 * smart-campus :: dashboard.js
 * ----------------------------------------------------------------------------
 * Loads GET /api/dashboard/statistics and paints every card, chart and list.
 *
 * NOTE - none of the numbers below are written into this file.  They all come
 * from aggregate SQL queries executed by DashboardRepository, so adding a
 * student in the Students module immediately changes the dashboard.
 * ========================================================================== */

(function () {
  'use strict';

  /** Palette shared by every chart so the page looks consistent. */
  const Palette = {
    blue: '#2f6fed',
    cyan: '#0891b2',
    green: '#16a34a',
    orange: '#d97706',
    red: '#dc2626',
    purple: '#7c3aed',
    slate: '#64748b'
  };

  const charts = {};
  let stats = null;
  let signedInUser = null;
  let greetingKey = '';

  /* ------------------------------------------------------------ bootstrap */

  document.addEventListener('DOMContentLoaded', boot);

  async function boot() {
    const user = await Auth.requireAuth();
    if (!user) return;
    signedInUser = user;

    AppShell.init('index.html', 'Dashboard');
    renderGreeting(user);
    bindModuleSearch();

    document.getElementById('refreshStats').addEventListener('click', load);
    await load();

    /* keeps the clock honest and re-greets when the local period changes */
    window.setInterval(tickClock, 15000);

    document.getElementById('pageLoader').style.display = 'none';
  }

  async function load() {
    const button = document.getElementById('refreshStats');
    try {
      stats = await apiGet('/dashboard/statistics');
      renderCards(stats);
      renderCharts(stats);
      renderModules(stats);
      renderEvents(stats.upcomingEvents || []);
      renderRecentBookings(stats.recentBookings || []);
    } catch (error) {
      toastError(errorMessage(error, 'The dashboard statistics could not be loaded.'));
    } finally {
      if (button) button.disabled = false;
    }
  }

  /* ---------------------------------------------------- time-based greeting */

  /** Greeting and clock follow the browser's own local time zone. */
  function localNow() {
    const now = new Date();
    const parts = new Intl.DateTimeFormat(undefined, {
      hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
      weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    }).formatToParts(now);

    const map = {};
    parts.forEach((part) => { map[part.type] = part.value; });

    let zone = '';
    try {
      const zonePart = new Intl.DateTimeFormat(undefined, { timeZoneName: 'short' })
        .formatToParts(now)
        .find((part) => part.type === 'timeZoneName');
      if (zonePart) zone = zonePart.value;
    } catch (e) {
      /* very old browsers: the clock is still correct, only the label is blank */
    }

    return {
      hour: now.getHours(),
      time: `${map.hour}:${map.minute}`,
      date: `${map.weekday}, ${map.day} ${map.month} ${map.year}`,
      zone: zone || 'local'
    };
  }

  /**
   * Morning 05:00-11:59 · Afternoon 12:00-16:59 · Evening 17:00-20:59 ·
   * Night 21:00-04:59 — always the visitor's local clock.
   */
  function greetingFor(hour) {
    if (hour >= 5 && hour < 12) return 'Good Morning';
    if (hour >= 12 && hour < 17) return 'Good Afternoon';
    if (hour >= 17 && hour < 21) return 'Good Evening';
    return 'Good Night';
  }

  /**
   * First name of the signed-in user, straight from the authenticated session.
   * A leading title is skipped so "Dr. Priya Sharma" is greeted as "Priya".
   */
  function firstName() {
    const user = signedInUser || Auth.currentUser() || {};
    const full = String(user.fullName || user.email || '').trim();
    const parts = full.split(/\s+/).filter(Boolean);
    const titled = parts.length > 1 && /^(dr|prof|mr|mrs|ms|miss)\.?$/i.test(parts[0]);
    return parts[titled ? 1 : 0] || 'there';
  }

  function initials() {
    const user = signedInUser || Auth.currentUser() || {};
    return (user.fullName || user.email || '?')
      .split(/[\s@.]+/).filter(Boolean).slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase()).join('');
  }

  function renderGreeting(user) {
    const now = localNow();
    const text = `${greetingFor(now.hour)}, ${firstName()}!`;
    const line = document.getElementById('greetingLine');
    const hero = document.getElementById('greetingHero');

    if (line) line.textContent = text;
    greetingKey = text;

    const sub = document.getElementById('welcomeLine');
    if (sub) {
      sub.textContent = 'Welcome back to your Smart Campus Dashboard. '
        + `Every figure below is a live query against smart_campus_db.`;
    }

    const role = document.getElementById('heroRole');
    if (role) role.textContent = user.role || '';

    const avatar = document.getElementById('heroAvatar');
    if (avatar) avatar.textContent = initials();

    /* one gentle entrance, never re-run on every refresh */
    if (hero && !hero.classList.contains('is-ready')) {
      hero.classList.add('is-ready');
    }

    paintClock(now);
  }

  function paintClock(now) {
    const clock = document.getElementById('localClock');
    const date = document.getElementById('localDate');
    const zone = document.getElementById('localZone');
    if (clock) clock.textContent = now.time;
    if (date) date.textContent = now.date;
    if (zone) zone.textContent = now.zone;
  }

  /** Refreshes the clock and re-greets only when the local period changes. */
  function tickClock() {
    const now = localNow();
    paintClock(now);
    const next = `${greetingFor(now.hour)}, ${firstName()}!`;
    if (next === greetingKey) return;

    greetingKey = next;
    const line = document.getElementById('greetingLine');
    if (!line) return;
    line.textContent = next;
    const hero = document.getElementById('greetingHero');
    if (hero) {
      hero.classList.remove('is-greeted');
      void hero.offsetWidth;               /* restart the CSS animation */
      hero.classList.add('is-greeted');
    }
  }

  /* ---------------------------------------------------------- module cards */

  /** One square tile per existing module - built from the shared AppShell.MENU. */
  function renderModules(data) {
    const grid = document.getElementById('moduleGrid');
    const count = document.getElementById('moduleCount');
    if (!grid) return;

    const modules = AppShell.modulesFor(Auth.role(), 'index.html');

    grid.innerHTML = modules.map((module) => moduleCardHtml(module, data)).join('');
    if (count) {
      count.innerHTML = `<i class="bi bi-grid"></i> ${modules.length} modules`;
    }

    applyModuleSearch();
  }

  function moduleCardHtml(module, data) {
    /* colours come from the shared Theme map - see js/theme.js */
    const active = Theme.isActive(module.key);
    const themeLabel = Theme.moduleTheme(module.key).label;

    /* real number only - omitted entirely when the API has none */
    const raw = module.stat ? data[module.stat] : null;
    const metric = (raw === null || raw === undefined || raw === '')
      ? ''
      : `<span class="module-metric">
           <strong>${Number(raw).toLocaleString('en-IN')}</strong>
           <span>${escapeHtml(module.statLabel || '')}</span>
         </span>`;

    const haystack = `${module.label} ${module.desc || ''}`.toLowerCase();

    return `
      <a class="module-card${active ? ' is-active' : ''}" href="${module.key}"
         data-module="${module.key}"
         data-module-search="${escapeHtml(haystack)}"
         ${active ? 'aria-current="true" ' : ''}aria-label="Open the ${escapeHtml(module.label)} module"
         style="${Theme.cardVars(module.key)}">
        <span class="module-glow" aria-hidden="true"></span>
        <span class="module-active-mark" title="${escapeHtml(themeLabel)} theme" aria-hidden="true">
          <i class="bi bi-check-lg"></i>
        </span>
        <span class="module-icon"><i class="bi ${module.icon}"></i></span>
        <span class="module-body">
          <span class="module-title">${escapeHtml(module.label)}</span>
          <span class="module-desc">${escapeHtml(module.desc || '')}</span>
          ${metric}
        </span>
        <span class="module-open" aria-hidden="true">
          Open <i class="bi bi-arrow-right"></i>
        </span>
      </a>`;
  }

  /* ------------------------------------------------------- module search */

  function bindModuleSearch() {
    const input = document.getElementById('moduleSearch');
    if (!input) return;
    input.addEventListener('input', applyModuleSearch);
  }

  /** Filters the tiles already in the DOM - no extra request, no new routing. */
  function applyModuleSearch() {
    const grid = document.getElementById('moduleGrid');
    const empty = document.getElementById('moduleEmpty');
    const input = document.getElementById('moduleSearch');
    if (!grid) return;

    const keyword = (input ? input.value : '').trim().toLowerCase();
    let shown = 0;

    grid.querySelectorAll('[data-module-search]').forEach((card) => {
      const hit = !keyword || card.dataset.moduleSearch.indexOf(keyword) !== -1;
      card.classList.toggle('d-none', !hit);
      if (hit) shown++;
    });

    if (empty) empty.classList.toggle('d-none', shown !== 0);
  }

  /* ------------------------------------------------------------- stat cards */

  function renderCards(data) {
    const set = (key, value) => {
      const el = document.querySelector(`[data-stat="${key}"]`);
      if (el) animateNumber(el, value);
    };

    set('totalStudents', data.totalStudents);
    set('totalFaculty', data.totalFaculty);
    set('totalCourses', data.totalCourses);
    set('activeBookings', data.activeBookings);
    set('upcomingActivities', data.upcomingActivities);
    set('resources',
      (data.totalClassrooms || 0) + (data.totalLaboratories || 0) + (data.totalEquipment || 0));

    const foot = (key, text) => {
      const el = document.querySelector(`[data-foot="${key}"]`);
      if (el) el.textContent = text;
    };

    foot('students', 'enrolled across departments');
    foot('faculty', 'teaching members');
    foot('courses', 'offered this term');
    foot('resources',
      `${data.totalClassrooms} rooms · ${data.totalLaboratories} labs · ${data.totalEquipment} items`);
    foot('bookings', 'pending or approved reservations');
    foot('activities', 'scheduled on the campus calendar');
  }

  /** Small count-up animation so the card does not simply "pop". */
  function animateNumber(element, target) {
    const end = Number(target) || 0;
    const duration = 600;
    const start = performance.now();
    const from = Number(String(element.textContent).replace(/\D/g, '')) || 0;

    function step(now) {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      element.textContent = Math.round(from + (end - from) * eased).toLocaleString('en-IN');
      if (progress < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
  }

  /* ---------------------------------------------------------------- charts */

  function baseOptions(extra) {
    return Object.assign({
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 8, font: { size: 11 } } },
        tooltip: { backgroundColor: '#0f172a', padding: 10, cornerRadius: 8 }
      }
    }, extra || {});
  }

  function renderCharts(data) {
    renderStatusChart(data.bookingsByStatus || {});
    renderDepartmentChart(data.studentsByDepartment || {});
    renderResourceChart(data.resourceUtilization || {});
    renderSemesterChart(data.studentsBySemester || {});
  }

  function destroy(name) {
    if (charts[name]) { charts[name].destroy(); delete charts[name]; }
  }

  function renderStatusChart(source) {
    destroy('status');
    const labels = Object.keys(source);
    const values = Object.values(source);
    const colors = [Palette.orange, Palette.blue, Palette.green, Palette.red, Palette.slate];

    charts.status = new Chart(document.getElementById('chartBookingStatus'), {
      type: 'doughnut',
      data: {
        labels: labels.length ? labels : ['No bookings yet'],
        datasets: [{
          data: values.length ? values : [1],
          backgroundColor: labels.length ? colors : ['#e2e8f0'],
          borderWidth: 2,
          borderColor: '#fff'
        }]
      },
      options: baseOptions({
        cutout: '64%',
        plugins: {
          legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 8, font: { size: 11 } } }
        }
      })
    });
  }

  function renderDepartmentChart(source) {
    destroy('dept');
    const labels = Object.keys(source);

    charts.dept = new Chart(document.getElementById('chartDepartments'), {
      type: 'bar',
      data: {
        labels: labels.map((l) => shortLabel(l)),
        datasets: [{
          label: 'Students',
          data: Object.values(source),
          backgroundColor: Palette.blue,
          borderRadius: 7,
          maxBarThickness: 34
        }]
      },
      options: baseOptions({
        indexAxis: 'y',
        plugins: { legend: { display: false } },
        scales: {
          x: { beginAtZero: true, ticks: { precision: 0, font: { size: 11 } }, grid: { color: '#eef1f7' } },
          y: { ticks: { font: { size: 11 } }, grid: { display: false } }
        }
      })
    });
  }

  function renderResourceChart(source) {
    destroy('res');
    const keys = Object.keys(source);
    const toTotal = (k) => (source[k] && source[k].total) || 0;
    const toActive = (k) => (source[k] && source[k].active) || 0;
    const toMaintenance = (k) => (source[k] && source[k].maintenance) || 0;
    const toInactive = (k) => (source[k] && source[k].inactive) || 0;

    charts.res = new Chart(document.getElementById('chartResources'), {
      type: 'bar',
      data: {
        labels: keys.map(shortLabel),
        datasets: [
          { label: 'Active', data: keys.map(toActive), backgroundColor: Palette.green, borderRadius: 6 },
          { label: 'Maintenance', data: keys.map(toMaintenance), backgroundColor: Palette.orange, borderRadius: 6 },
          { label: 'Inactive', data: keys.map(toInactive), backgroundColor: '#cbd5e1', borderRadius: 6 }
        ]
      },
      options: baseOptions({
        plugins: {
          legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 8, font: { size: 11 } } }
        },
        scales: {
          x: { stacked: true, ticks: { font: { size: 11 } }, grid: { display: false } },
          y: { stacked: true, beginAtZero: true, ticks: { precision: 0, font: { size: 11 } }, grid: { color: '#eef1f7' } }
        }
      })
    });

    /* remember the totals for the tooltip-less summary under the chart */
    const total = keys.reduce((sum, key) => sum + toTotal(key), 0);
    void total;
  }

  function renderSemesterChart(source) {
    destroy('sem');
    const labels = Object.keys(source);

    charts.sem = new Chart(document.getElementById('chartSemesters'), {
      type: 'line',
      data: {
        labels: labels,
        datasets: [{
          label: 'Students',
          data: Object.values(source),
          borderColor: Palette.purple,
          backgroundColor: 'rgba(124, 58, 237, .12)',
          fill: true,
          tension: .35,
          pointRadius: 4,
          pointBackgroundColor: '#fff',
          pointBorderWidth: 2
        }]
      },
      options: baseOptions({
        plugins: { legend: { display: false } },
        scales: {
          x: { ticks: { font: { size: 11 } }, grid: { display: false } },
          y: { beginAtZero: true, ticks: { precision: 0, font: { size: 11 } }, grid: { color: '#eef1f7' } }
        }
      })
    });
  }

  function shortLabel(label) {
    if (!label) return '';
    return String(label).length > 18 ? String(label).slice(0, 17) + '…' : label;
  }

  /* -------------------------------------------------------- upcoming events */

  function renderEvents(events) {
    const host = document.getElementById('eventList');
    if (!host) return;

    if (!events.length) {
      host.innerHTML = emptyBlock('No upcoming events',
        'Create one from the Activities module.');
      return;
    }

    host.innerHTML = events.slice(0, 5).map((event) => `
      <div class="event-row">
        <div class="event-date">
          <span class="d">${new Date(event.eventDate || event.startDate || Date.now()).getDate()}</span>
          <span class="m">${monthName(event.eventDate || event.startDate)}</span>
        </div>
        <div class="event-body">
          <div class="event-title">${escapeHtml(event.title || event.name || 'Untitled')}</div>
          <div class="event-meta">
            ${escapeHtml(event.venue || 'Venue TBA')} ·
            ${formatTime(event.startTime || '00:00')} ·
            ${escapeHtml(event.category || 'Event')}
          </div>
        </div>
        ${statusBadge(event.status)}
      </div>`).join('');
  }

  function monthName(value) {
    if (!value) return '';
    const months = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN',
                    'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];
    const parts = String(value).split('-');
    return months[parseInt(parts[1], 10) - 1] || '';
  }

  /* ----------------------------------------------------- recent bookings */

  function renderRecentBookings(rows) {
    const host = document.getElementById('recentBookingsTable');
    if (!host) return;

    if (!rows.length) {
      host.innerHTML = emptyBlock('No bookings yet',
        'Reservations created in the Bookings module appear here.');
      return;
    }

    host.innerHTML = `
      <table class="table table-hover align-middle mb-0">
        <thead class="table-light">
          <tr>
            <th class="text-uppercase" style="font-size:.67rem">Reference</th>
            <th class="text-uppercase" style="font-size:.67rem">Resource</th>
            <th class="text-uppercase" style="font-size:.67rem">Requested by</th>
            <th class="text-uppercase" style="font-size:.67rem">Date</th>
            <th class="text-uppercase" style="font-size:.67rem">Time</th>
            <th class="text-uppercase" style="font-size:.67rem">Status</th>
          </tr>
        </thead>
        <tbody>
          ${rows.map((row) => `
            <tr>
              <td class="mono fw-semibold">${escapeHtml(row.bookingRef)}</td>
              <td>
                <span class="cell-strong">${escapeHtml(row.resourceName)}</span>
                <span class="cell-sub">${escapeHtml(row.resourceType)}</span>
              </td>
              <td>${escapeHtml(row.requestedByName || '—')}</td>
              <td>${formatDate(row.bookingDate)}</td>
              <td class="mono">${formatTime(row.startTime)} – ${formatTime(row.endTime)}</td>
              <td>${statusBadge(row.status, row.statusLabel)}</td>
            </tr>`).join('')}
        </tbody>
      </table>`;
  }
})();
