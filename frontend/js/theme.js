/* ============================================================================
 * theme.js - module accent themes, background styles and 3D tile behaviour
 * ----------------------------------------------------------------------------
 * Loaded with `defer` from <head> so the correct background style is on <html>
 * before the first paint (no flash of the wrong theme).
 *
 * WHAT IT OWNS
 *   MODULE_THEMES  the ONE map from an existing module page -> its colours.
 *                  theme.js writes these as CSS variables (--theme-*) on
 *                  <html> and as (--accent-*) on the matching tile, so no
 *                  colour is ever duplicated in CSS.
 *   STYLES         the optional background style selector
 *                  (default / light / dark / blue / green / purple).
 *   Tile behaviour pointer tilt, sheen, active state and the click that
 *                  applies the module theme before the existing link
 *                  navigates to the existing page.
 *
 * IT DOES NOT OWN routing, markup of the modules, or any API call - the cards
 * stay plain <a href="students.html"> links built by dashboard.js, so the
 * browser (and the keyboard, and middle-click, and back/forward) keeps working
 * exactly as before.
 * ========================================================================== */

const Theme = (() => {
  'use strict';

  const MODULE_STORE = 'sc.theme.module';   /* page key of the last module opened */
  const STYLE_STORE = 'sc.theme.style';     /* chosen background style            */
  const COLOR_STORE = 'sc.theme.color';     /* chosen GLOBAL accent colour ('' = module default) */

  /**
   * Single source of truth for module colours.
   *
   *   accent      hex used for icons, borders and the active ring
   *   rgb         the same colour as "r, g, b" so it can be used in rgba()
   *   soft        light wash behind icons / chips / the active tile
   *   tint        pastel of the accent laid over neutral chips and surfaces
   *   wash        strength (0 - 1) of the full-page background tint
   *
   * Only pages that already exist in js/shell.js are listed here.
   */
  const MODULE_THEMES = {
    'index.html': {
      slug: 'default', label: 'Dashboard',
      accent: '#2f6fed', rgb: '47, 111, 237', soft: '#e8f0ff', tint: '#dbe6ff', wash: 0
    },
    'students.html': {
      slug: 'students', label: 'Students',
      accent: '#2f6fed', rgb: '47, 111, 237', soft: '#e8f0ff', tint: '#dbe6ff', wash: .075
    },
    'faculty.html': {
      slug: 'faculty', label: 'Faculty',
      accent: '#7c3aed', rgb: '124, 58, 237', soft: '#f1eafe', tint: '#e9dfff', wash: .075
    },
    'courses.html': {
      slug: 'courses', label: 'Courses',
      accent: '#4f46e5', rgb: '79, 70, 229', soft: '#eceefe', tint: '#dfe3ff', wash: .075
    },
    'classrooms.html': {
      slug: 'classrooms', label: 'Classrooms',
      accent: '#0d9488', rgb: '13, 148, 136', soft: '#e4f7f4', tint: '#cfeee9', wash: .075
    },
    'laboratories.html': {
      slug: 'laboratories', label: 'Laboratories',
      accent: '#0891b2', rgb: '8, 145, 178', soft: '#e4f7fb', tint: '#cfeaf3', wash: .075
    },
    'equipment.html': {
      slug: 'equipment', label: 'Equipment',
      accent: '#ea580c', rgb: '234, 88, 12', soft: '#ffefe2', tint: '#ffe0cc', wash: .075
    },
    'activities.html': {
      slug: 'activities', label: 'Activities',
      accent: '#db2777', rgb: '219, 39, 119', soft: '#fdeaf4', tint: '#fbd6e7', wash: .075
    },
    'bookings.html': {
      slug: 'bookings', label: 'Bookings',
      accent: '#8b5cf6', rgb: '139, 92, 246', soft: '#f1ecff', tint: '#e3d9ff', wash: .075
    },
    'reports.html': {
      slug: 'reports', label: 'Reports',
      accent: '#475569', rgb: '71, 85, 105', soft: '#eef1f7', tint: '#dee4ee', wash: .075
    },
    'users.html': {
      slug: 'users', label: 'Users',
      accent: '#d97706', rgb: '217, 119, 6', soft: '#fdf1e2', tint: '#f8e3c4', wash: .075
    },
    'settings.html': {
      slug: 'settings', label: 'Settings',
      accent: '#64748b', rgb: '100, 116, 139', soft: '#f1f5f9', tint: '#e3e9f1', wash: .075
    }
  };

  /** Optional background styles - the accent of the open module sits on top. */
  const STYLES = [
    { id: 'default', label: 'Default', swatch: '#eef2fb' },
    { id: 'light', label: 'Light', swatch: '#ffffff' },
    { id: 'dark', label: 'Dark', swatch: '#0b1220' },
    { id: 'blue', label: 'Blue', swatch: '#eef4ff' },
    { id: 'green', label: 'Green', swatch: '#edfaf3' },
    { id: 'purple', label: 'Purple', swatch: '#f5f2ff' }
  ];

  /**
   * The circular "Choose Your Theme Color" swatches.
   * Pure CSS circles (no emoji, no image) - the colour is the whole point,
   * the label is what makes the choice readable to everyone.
   */
  const PALETTE = [
    { label: 'Blue',   hex: '#3B82F6' },
    { label: 'Green',  hex: '#22C55E' },
    { label: 'Purple', hex: '#8B5CF6' },
    { label: 'Orange', hex: '#F97316' },
    { label: 'Red',    hex: '#EF4444' },
    { label: 'Pink',   hex: '#EC4899' },
    { label: 'Yellow', hex: '#EAB308' },
    { label: 'Teal',   hex: '#14B8A6' },
    { label: 'Indigo', hex: '#6366F1' },
    { label: 'Dark',   hex: '#1F2937' },
    { label: 'White',  hex: '#FFFFFF' }
  ];

  const TILT_MAX = 7;               /* degrees - deliberately restrained */
  const NAV_DELAY = 190;            /* ms the theme gets to start animating */
  const root = document.documentElement;

  let activeKey = 'index.html';     /* module whose theme is on screen */
  let globalColor = '';             /* chosen global accent, '' = module default */
  let menuBtn = null;
  let menu = null;
  let fab = null;                   /* floating picker (pages without the menu) */
  let fabBtn = null;
  let fabPanel = null;

  /* ------------------------------------------------------------- helpers */

  const reduced = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const precisePointer = () =>
    window.matchMedia('(hover: hover) and (pointer: fine)').matches;

  function storeGet(key, fallback) {
    try { return localStorage.getItem(key) || fallback; } catch (e) { return fallback; }
  }
  function storeSet(key, value) {
    try { localStorage.setItem(key, value); } catch (e) { /* private mode - theme stays for this page */ }
  }

  /** File name of the page currently open, e.g. "students.html". */
  function pageFile() {
    const file = (window.location.pathname.split('/').pop() || '').toLowerCase();
    return file || 'index.html';
  }

  /** The theme of a module page, falling back to the dashboard default. */
  function moduleTheme(key) {
    return MODULE_THEMES[key] || MODULE_THEMES['index.html'];
  }

  /* ------------------------------------------------------- colour maths */
  /* Small, dependency-free helpers used only by the circular colour picker.
     They turn a chosen hex into readable CSS: a soft wash, a tint, a shadow
     and - just as important - a foreground colour that stays legible on top
     of it (white text on yellow, dark text on white, ...).               */

  function hexToRgb(hex) {
    let value = String(hex).replace('#', '').trim();
    if (value.length === 3) value = value.split('').map((c) => c + c).join('');
    const num = parseInt(value, 16);
    if (value.length !== 6 || Number.isNaN(num)) return [47, 111, 237];
    return [(num >> 16) & 255, (num >> 8) & 255, num & 255];
  }

  function rgbToHex(rgb) {
    return '#' + rgb.map((c) => {
      const part = Math.max(0, Math.min(255, Math.round(c))).toString(16);
      return part.length === 1 ? '0' + part : part;
    }).join('').toUpperCase();
  }

  /** mix of two colours, `weight` of the SECOND one (0 = a, 1 = b). */
  function mixRgb(a, b, weight) {
    return a.map((channel, i) => channel * (1 - weight) + b[i] * weight);
  }
  function mixHex(a, b, weight) {
    return rgbToHex(mixRgb(hexToRgb(a), hexToRgb(b), weight));
  }

  /** WCAG relative luminance of an [r, g, b] triple. */
  function luminance(rgb) {
    const lin = rgb.map((c) => {
      const v = c / 255;
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2];
  }

  /** Ink that reads best ON the solid colour (nav pill, badges, ...). */
  function inkFor(rgb) {
    const l = luminance(rgb);
    const withWhite = 1.05 / (l + 0.05);
    const withInk = (l + 0.05) / 0.056;          /* #0f172a is almost black */
    return withWhite >= withInk ? '#ffffff' : '#0f172a';
  }

  /** The colour that reads best as TEXT on the current card background. */
  function accentTextFor(rgb, dark) {
    const l = luminance(rgb);
    if (dark) return l < 0.28 ? rgbToHex(mixRgb(rgb, [226, 232, 240], .6)) : rgbToHex(rgb);
    return l > 0.28 ? rgbToHex(mixRgb(rgb, [31, 41, 55], .55)) : rgbToHex(rgb);
  }

  /** "" or a normalised #RRGGBB - anything else is refused. */
  function normalizeColor(hex) {
    if (!hex) return '';
    const found = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(String(hex).trim());
    if (!found) return '';
    let value = found[1];
    if (value.length === 3) value = value.split('').map((c) => c + c).join('');
    return '#' + value.toUpperCase();
  }

  /**
   * Which module themes this page.
   * Module pages always show their own colour (so a page can never disagree
   * with itself); the dashboard keeps whatever was selected last.
   */
  function initialKey() {
    const page = pageFile();
    if (page !== 'index.html' && MODULE_THEMES[page]) {
      storeSet(MODULE_STORE, page);
      return page;
    }
    const stored = storeGet(MODULE_STORE, 'index.html');
    return MODULE_THEMES[stored] ? stored : 'index.html';
  }

  /* ------------------------------------------------- module accent theme */

  /**
   * The SHARED accent of the UI: the picked global colour when there is one,
   * otherwise the colour of the module that is open.  Written as --theme-*
   * (and, while a global colour is active, on the --sc-* brand variables the
   * existing buttons already read).
   */
  function paintShared() {
    const theme = moduleTheme(activeKey);
    const dark = root.dataset.style === 'dark';
    const hex = globalColor || theme.accent;
    const rgb = hexToRgb(hex);
    const rgbText = rgb.join(', ');

    root.style.setProperty('--theme-primary', hex);
    root.style.setProperty('--theme-accent', hex);
    root.style.setProperty('--theme-accent-rgb', rgbText);
    root.style.setProperty('--theme-shadow', `rgba(${rgbText}, .32)`);
    root.style.setProperty('--theme-on-accent', inkFor(rgb));
    root.style.setProperty('--theme-accent-text', accentTextFor(rgb, dark));

    if (globalColor) {
      /* derived wash/tint so every colour, including white, stays balanced */
      const base = dark ? '#131c2e' : '#ffffff';
      root.style.setProperty('--theme-soft', mixHex(hex, base, .88));
      root.style.setProperty('--theme-tint', mixHex(hex, base, .80));
      /* the existing primary buttons follow the chosen colour ... */
      root.style.setProperty('--sc-primary', hex);
      root.style.setProperty('--sc-primary-dark', mixHex(hex, '#0f172a', .28));
      root.style.setProperty('--sc-primary-soft', `rgba(${rgbText}, .16)`);
    } else {
      /* ... and step aside completely when the colour is cleared */
      root.style.setProperty('--theme-soft', theme.soft);
      root.style.setProperty('--theme-tint', theme.tint);
      root.style.removeProperty('--sc-primary');
      root.style.removeProperty('--sc-primary-dark');
      root.style.removeProperty('--sc-primary-soft');
    }
  }

  /** Writes the module colours as CSS variables on <html>. */
  function applyModuleTheme(key, persist) {
    const theme = moduleTheme(key);
    activeKey = MODULE_THEMES[key] ? key : 'index.html';

    root.dataset.moduleTheme = theme.slug;
    /* module identity: always the module's own colour, even when a global
       colour is chosen - active/selected states keep saying which module */
    root.style.setProperty('--theme-module', theme.accent);
    root.style.setProperty('--theme-module-rgb', theme.rgb);
    root.style.setProperty('--theme-module-tint', theme.tint);
    root.style.setProperty('--theme-wash-strength', String(theme.wash));

    paintShared();

    if (persist) storeSet(MODULE_STORE, activeKey);
    syncActiveCards();
    return theme;
  }

  /**
   * The circular colour picker: '' goes back to the module's own colour.
   * Only a visual preference is stored - never anything sensitive.
   */
  function applyColor(hex, persist) {
    globalColor = normalizeColor(hex);
    if (persist) storeSet(COLOR_STORE, globalColor);
    paintShared();
    syncPalette();
    return globalColor;
  }

  /**
   * Inline custom properties for one tile, taken from the same map:
   *   <a class="module-card" style="--accent:#0d9488; --accent-rgb:13,148,136; ...">
   */
  function cardVars(key) {
    const theme = moduleTheme(key);
    return `--accent:${theme.accent};--accent-rgb:${theme.rgb};--accent-soft:${theme.soft}`;
  }

  /** True when the tile matches the module currently themed on screen. */
  function isActive(key) {
    const theme = moduleTheme(key);
    return theme.slug !== 'default' && theme.slug === moduleTheme(activeKey).slug;
  }

  /** Highlight every tile that matches the theme currently on screen. */
  function syncActiveCards(scope) {
    const host = scope || document;
    if (!host || !host.querySelectorAll) return;
    host.querySelectorAll('.module-card[data-module]').forEach((card) => {
      const on = isActive(card.getAttribute('data-module'));
      card.classList.toggle('is-active', on);
      if (on) card.setAttribute('aria-current', 'true');
      else card.removeAttribute('aria-current');
    });
  }

  /* -------------------------------------------------- background styles */

  function applyStyle(id, persist) {
    const style = STYLES.find((item) => item.id === id) || STYLES[0];
    root.dataset.style = style.id;
    if (style.id === 'dark') root.setAttribute('data-bs-theme', 'dark');
    else root.removeAttribute('data-bs-theme');
    paintShared();                 /* wash/tint/legibility depend on the style */
    if (persist) storeSet(STYLE_STORE, style.id);
    syncStyleMenu();
    return style;
  }

  function syncStyleMenu() {
    const chosen = storeGet(STYLE_STORE, 'default');
    if (menu) {
      menu.querySelectorAll('[data-style-id]').forEach((option) => {
        option.setAttribute('aria-checked', String(option.dataset.styleId === chosen));
      });
    }
    if (menuBtn) menuBtn.dataset.style = chosen;
  }

  /* -------------------------------------------------------- tile pointer */

  let tiltFrame = 0;
  let tiltPending = null;

  const clamp = (value, min, max) => Math.min(Math.max(value, min), max);

  function queueTilt(card, event) {
    const box = card.getBoundingClientRect();
    if (!box.width || !box.height) return;
    const px = clamp((event.clientX - box.left) / box.width, 0, 1);
    const py = clamp((event.clientY - box.top) / box.height, 0, 1);

    tiltPending = {
      card,
      x: ((0.5 - py) * TILT_MAX * 2).toFixed(2) + 'deg',
      y: ((px - 0.5) * TILT_MAX * 2).toFixed(2) + 'deg',
      mx: (px * 100).toFixed(1) + '%',
      my: (py * 100).toFixed(1) + '%'
    };
    if (tiltFrame) return;
    tiltFrame = window.requestAnimationFrame(() => {
      tiltFrame = 0;
      if (!tiltPending) return;
      const { card, x, y, mx, my } = tiltPending;
      card.style.setProperty('--tilt-x', x);
      card.style.setProperty('--tilt-y', y);
      card.style.setProperty('--sheen-x', mx);
      card.style.setProperty('--sheen-y', my);
      tiltPending = null;
    });
  }

  function resetTilt(card) {
    if (!card) return;
    card.style.setProperty('--tilt-x', '0deg');
    card.style.setProperty('--tilt-y', '0deg');
    card.style.setProperty('--sheen-x', '50%');
    card.style.setProperty('--sheen-y', '6%');
  }

  function resetAllTilts() {
    document.querySelectorAll('.module-card[data-module]').forEach(resetTilt);
  }

  function bindTilePointer() {
    if (reduced() || !precisePointer()) return;
    /* only the dashboard has tiles - no point tracking the pointer elsewhere */
    if (!document.getElementById('moduleGrid')) return;

    document.addEventListener('pointermove', (event) => {
      const target = event.target;
      if (!target || !target.closest) return;
      const card = target.closest('.module-card[data-module]');
      if (card) queueTilt(card, event);
    }, { passive: true });

    document.addEventListener('pointerout', (event) => {
      const target = event.target;
      if (!target || !target.closest) return;
      const card = target.closest('.module-card[data-module]');
      if (card && (!event.relatedTarget || !card.contains(event.relatedTarget))) resetTilt(card);
    });

    document.addEventListener('pointercancel', resetAllTilts);
    document.addEventListener('mouseleave', resetAllTilts);
    window.addEventListener('blur', resetAllTilts);
  }

  /* --------------------------------------------------- tile activation */

  /**
   * Click / Enter / Space on a tile: paint the module theme first, then let
   * the existing link do the navigation it has always done.
   */
  function activate(card, event) {
    const key = card.getAttribute('data-module') || card.getAttribute('href');
    if (key) applyModuleTheme(key, true);

    if (event.defaultPrevented) return;
    /* new tab / new window / context menu are the browser's business */
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    if (typeof event.button === 'number' && event.button !== 0) return;

    const href = card.getAttribute('href');
    if (!href || reduced()) return;            /* reduced motion: go straight there */

    /* let the background colour actually start moving before the page swaps */
    event.preventDefault();
    window.setTimeout(() => { window.location.assign(href); }, NAV_DELAY);
  }

  function bindTiles() {
    document.addEventListener('click', (event) => {
      const target = event.target;
      if (!target || !target.closest) return;
      const card = target.closest('.module-card[data-module]');
      if (card) activate(card, event);
    });
  }

  /* -------------------------------------------------- background selector */

  /* One circle = one colour. The check mark, ring and aria-checked state make
     the selection readable without relying on colour alone. */
  function swatchHtml(color, role) {
    return `
      <button type="button" class="color-swatch" role="${role || 'menuitemradio'}"
              aria-checked="false" data-color="${color.hex}" data-label="${color.label}"
              style="--sw:${color.hex};--sw-ink:${inkFor(hexToRgb(color.hex))}"
              title="${color.label}" aria-label="${color.label}">
        <i class="bi bi-check-lg" aria-hidden="true"></i>
      </button>`;
  }

  function customSwatchHtml(role, inputId) {
    return `
      <button type="button" class="color-swatch is-custom" role="${role || 'menuitemradio'}"
              aria-checked="false" data-custom-color
              title="Pick a custom color" aria-label="Custom color">
        <i class="bi bi-plus-lg" aria-hidden="true"></i>
      </button>
      <input type="color" class="color-native" id="${inputId || 'themeNativeColor'}"
             data-native-color value="#2f6fed" tabindex="-1" aria-hidden="true">`;
  }

  function menuHtml() {
    const options = STYLES.map((style) => `
      <button type="button" class="theme-opt" role="menuitemradio"
              data-style-id="${style.id}" aria-checked="false">
        <span class="swatch" style="background:${style.swatch}" aria-hidden="true"></span>
        <span>${style.label}</span>
        <i class="bi bi-check-lg tick" aria-hidden="true"></i>
      </button>`).join('');

    const swatches = PALETTE.map((color) => swatchHtml(color, 'menuitemradio')).join('');

    return `
      <div role="group" aria-label="Background style">
        <div class="theme-menu-label">Background style</div>
        ${options}
      </div>
      <div class="theme-menu-divider" role="separator"></div>
      <div role="group" aria-label="Choose your theme color">
        <div class="theme-menu-label">Choose Your Theme Color</div>
        <div class="palette">
          ${swatches}
          ${customSwatchHtml('menuitemradio', 'themeNativeColor')}
        </div>
        <p class="palette-readout" id="paletteReadout" data-palette-readout aria-live="polite"></p>
      </div>
      <div class="theme-menu-divider" role="separator"></div>
      <button type="button" class="theme-opt is-reset" role="menuitem" data-reset-accent>
        <i class="bi bi-arrow-counterclockwise" aria-hidden="true"></i>
        <span>Reset accent</span>
      </button>`;
  }

  /** Every mounted circular picker: the dashboard menu and/or floating panel. */
  function paletteScopes() {
    return [menu, fabPanel].filter(Boolean);
  }

  /** Paints the chosen swatch: ring + check + "Selected: Blue" text. */
  function syncPalette() {
    paletteScopes().forEach(paintPalette);
  }

  function paintPalette(scope) {
    const chosen = globalColor;                       /* '' or #RRGGBB */
    const match = PALETTE.find((color) => color.hex.toUpperCase() === chosen);
    const custom = Boolean(chosen) && !match;

    scope.querySelectorAll('[data-color]').forEach((swatch) => {
      const on = Boolean(match) && swatch.dataset.color.toUpperCase() === chosen;
      swatch.setAttribute('aria-checked', String(on));
    });

    const customBtn = scope.querySelector('[data-custom-color]');
    if (customBtn) {
      customBtn.setAttribute('aria-checked', String(custom));
      customBtn.style.setProperty('--sw', custom ? chosen : 'transparent');
      customBtn.title = custom ? 'Custom color ' + chosen : 'Pick a custom color';
      customBtn.setAttribute('aria-label', custom ? 'Custom color ' + chosen : 'Custom color');
    }

    const readout = scope.querySelector('[data-palette-readout]');
    if (readout) {
      const label = custom
        ? 'Custom (' + chosen + ')'
        : match ? match.label : 'Module default';
      readout.innerHTML = 'Selected: <strong></strong>';
      readout.querySelector('strong').textContent = label;
    }

    const native = scope.querySelector('[data-native-color]');
    if (native && chosen) native.value = chosen;
  }

  /** Arrow-key movement across the circular radio group (Tab still leaves it). */
  function bindPaletteKeys(scope) {
    if (!scope || scope.dataset.paletteKeys === 'on') return;
    scope.dataset.paletteKeys = 'on';

    scope.addEventListener('keydown', (event) => {
      if (['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp', 'Home', 'End'].indexOf(event.key) === -1) return;
      const items = Array.from(scope.querySelectorAll('[data-color], [data-custom-color]'));
      const index = items.indexOf(document.activeElement);
      if (index === -1) return;

      let next = index;
      if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = (index + 1) % items.length;
      if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = (index - 1 + items.length) % items.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = items.length - 1;

      event.preventDefault();
      items[next].focus();
    });
  }

  function setMenu(open) {
    if (!menu || !menuBtn) return;
    if (open) menu.removeAttribute('hidden');
    else menu.setAttribute('hidden', '');
    menuBtn.setAttribute('aria-expanded', String(open));
    menuBtn.dataset.open = String(open);
    if (open) {
      /* open upward when the button sits low on the screen, so the colour
         palette never runs off the bottom (CSS honours .is-up >= 576px) */
      const box = menuBtn.getBoundingClientRect();
      const fits = box.bottom + 10 + menu.offsetHeight <= window.innerHeight;
      menu.classList.toggle('is-up', !fits);
      const checked = menu.querySelector('[aria-checked="true"]') || menu.querySelector('button');
      if (checked) checked.focus();
    } else {
      menu.classList.remove('is-up');
    }
  }

  /* --------------------------- floating picker (login + module pages) */
  /* The dashboard already exposes the full menu beside its module grid. Every
     other page - including login.html - gets the same circular palette in a
     compact floating panel, so the stored choice is always easy to reach. */

  function floatingPickerHtml() {
    return `
      <button type="button" class="theme-fab-btn" id="themeFabBtn"
              aria-haspopup="dialog" aria-expanded="false"
              aria-controls="themeFabPanel" aria-label="Choose theme color"
              title="Choose theme color">
        <i class="bi bi-palette-fill" aria-hidden="true"></i>
      </button>
      <div class="theme-fab-panel" id="themeFabPanel" role="dialog"
           aria-label="Choose your theme color" hidden>
        <div class="theme-fab-head">
          <span>Choose Your Theme Color</span>
          <button type="button" class="theme-fab-close" data-theme-fab-close
                  aria-label="Close theme color picker">
            <i class="bi bi-x-lg" aria-hidden="true"></i>
          </button>
        </div>
        <div class="palette" role="radiogroup" aria-label="Theme colors">
          ${PALETTE.map((color) => swatchHtml(color, 'radio')).join('')}
          ${customSwatchHtml('radio', 'themeFabNativeColor')}
        </div>
        <p class="palette-readout" data-palette-readout aria-live="polite"></p>
        <button type="button" class="theme-fab-reset" data-reset-accent>
          <i class="bi bi-arrow-counterclockwise" aria-hidden="true"></i>
          <span>Reset accent</span>
        </button>
      </div>`;
  }

  function setFab(open) {
    if (!fabPanel || !fabBtn) return;
    fabPanel.hidden = !open;
    fabBtn.setAttribute('aria-expanded', String(open));
    fabBtn.classList.toggle('is-open', open);
    if (open) {
      const checked = fabPanel.querySelector('[aria-checked="true"]') || fabPanel.querySelector('[data-color]');
      if (checked) checked.focus();
    }
  }

  function bindFloatingPicker() {
    if (fab || !document.body) return;
    fab = document.createElement('div');
    fab.className = 'theme-fab';
    fab.innerHTML = floatingPickerHtml();
    document.body.appendChild(fab);

    fabBtn = fab.querySelector('#themeFabBtn');
    fabPanel = fab.querySelector('#themeFabPanel');
    if (!fabBtn || !fabPanel) return;

    bindPaletteKeys(fabPanel);
    syncPalette();

    fabBtn.addEventListener('click', (event) => {
      event.stopPropagation();
      setFab(fabPanel.hidden);
    });

    fab.querySelector('[data-theme-fab-close]').addEventListener('click', () => {
      setFab(false);
      fabBtn.focus();
    });

    const native = fabPanel.querySelector('[data-native-color]');
    if (native) {
      native.addEventListener('input', () => applyColor(native.value, true));
      native.addEventListener('change', () => {
        if (applyColor(native.value, true) && typeof toastInfo === 'function') {
          toastInfo('Theme color: custom ' + native.value, 'Theme');
        }
      });
    }

    fab.addEventListener('click', (event) => {
      const target = event.target;
      if (!target || !target.closest) return;
      event.stopPropagation();

      if (target.closest('[data-reset-accent]')) {
        applyColor('', true);                 /* back to this page's own accent */
        if (typeof toastInfo === 'function') toastInfo('Accent reset to the module default', 'Theme');
        return;
      }

      const swatch = target.closest('[data-color]');
      if (swatch) {
        applyColor(swatch.dataset.color, true);
        if (typeof toastInfo === 'function') toastInfo('Theme color: ' + swatch.dataset.label, 'Theme');
        return;
      }

      if (target.closest('[data-custom-color]') && native) {
        native.click();                       /* the OS picker owns the focus */
      }
    });

    document.addEventListener('click', (event) => {
      if (fabPanel.hidden) return;
      if (fab.contains(event.target)) return;
      setFab(false);
    });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape' || fabPanel.hidden) return;
      setFab(false);
      fabBtn.focus();
    });
  }

  function bindStyleMenu() {
    menuBtn = document.getElementById('themeStyleBtn');
    menu = document.getElementById('themeStyleMenu');
    if (!menuBtn || !menu) {
      bindFloatingPicker();
      return;
    }

    menu.innerHTML = menuHtml();
    bindPaletteKeys(menu);
    syncStyleMenu();
    syncPalette();

    menuBtn.addEventListener('click', (event) => {
      event.stopPropagation();
      setMenu(menu.hasAttribute('hidden'));
    });

    /* the native colour well behind the "+" swatch */
    const native = menu.querySelector('#themeNativeColor');
    if (native) {
      native.addEventListener('input', () => applyColor(native.value, true));
      native.addEventListener('change', () => {
        if (applyColor(native.value, true) && typeof toastInfo === 'function') {
          toastInfo('Theme color: custom ' + native.value, 'Theme');
        }
      });
    }

    menu.addEventListener('click', (event) => {
      const target = event.target;
      if (!target || !target.closest) return;
      event.stopPropagation();

      const reset = target.closest('[data-reset-accent]');
      if (reset) {
        applyColor('', true);                 /* back to the module's colour */
        applyModuleTheme('index.html', true);
        setMenu(false);
        menuBtn.focus();
        if (typeof toastInfo === 'function') toastInfo('Accent reset to the module default', 'Theme');
        return;
      }

      const swatch = target.closest('[data-color]');
      if (swatch) {
        applyColor(swatch.dataset.color, true);
        setMenu(false);
        menuBtn.focus();
        if (typeof toastInfo === 'function') {
          toastInfo('Theme color: ' + swatch.dataset.label, 'Theme');
        }
        return;
      }

      if (target.closest('[data-custom-color]')) {
        if (native) native.click();           /* the OS picker owns the focus */
        return;
      }

      const option = target.closest('[data-style-id]');
      if (option) {
        const style = applyStyle(option.dataset.styleId, true);
        setMenu(false);
        menuBtn.focus();
        if (typeof toastInfo === 'function') toastInfo(`Background style: ${style.label}`, 'Theme');
      }
    });

    document.addEventListener('click', (event) => {
      if (menu.hasAttribute('hidden')) return;
      if (menu.contains(event.target) || menuBtn.contains(event.target)) return;
      setMenu(false);
    });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape' || menu.hasAttribute('hidden')) return;
      setMenu(false);
      menuBtn.focus();
    });
  }

  /* ----------------------------------------------------------------- boot */

  function injectWash() {
    if (!document.body || document.querySelector('.theme-wash')) return;
    const wash = document.createElement('div');
    wash.className = 'theme-wash';
    wash.setAttribute('aria-hidden', 'true');
    document.body.insertBefore(wash, document.body.firstChild);
  }

  function init() {
    /* read every stored preference before the first paint of the accent */
    globalColor = normalizeColor(storeGet(COLOR_STORE, ''));
    applyStyle(storeGet(STYLE_STORE, 'default'), false);
    applyModuleTheme(initialKey(), false);
    injectWash();
    bindTiles();
    bindTilePointer();
    bindStyleMenu();
  }

  init();

  return {
    moduleTheme,
    cardVars,
    isActive,
    applyModuleTheme,
    applyColor,
    applyStyle,
    syncActiveCards,
    styles: STYLES,
    palette: PALETTE,
    accentColor: () => globalColor
  };
})();
