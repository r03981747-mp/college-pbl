/* ============================================================================
 * lightcord.js - the pull-the-cord light switch on the login page
 * ----------------------------------------------------------------------------
 * A purely VISUAL interaction:
 *
 *   pull 1  ->  the room lights up and the existing login card fades in
 *   pull 2  ->  the room goes dark again and the card fades out
 *
 * WHAT IT NEVER DOES
 *   - it does not log anyone in or out
 *   - it does not clear the email / password the visitor already typed
 *   - it does not touch the session, the token or localStorage
 *   - it never counts as authentication: #loginForm still posts to
 *     POST /api/auth/login through js/login.js exactly as before, so wrong
 *     credentials still show the server's own error and a good login still
 *     redirects to the dashboard.
 *
 * INPUT
 *   mouse/touch drag on the cord, a plain click on the handle (fallback) and
 *   the keyboard (it is a real <button>: Enter / Space).  During a drag the
 *   page never scrolls (touch-action: none) and the cord springs back like a
 *   real pull chain.  With prefers-reduced-motion the CSS drops the movement
 *   and the switch simply changes state.
 * ========================================================================== */

(function () {
  'use strict';

  const page = document.getElementById('authPage');
  const rig = document.getElementById('lightRig');
  const handle = document.getElementById('lightHandle');
  const hint = document.getElementById('lightHint');
  const card = document.querySelector('.auth-card');
  if (!page || !rig || !handle) return;            /* another page / no scene */

  const MAX_PULL = 56;      /* px the chain can stretch (before rig scaling) */
  const THRESHOLD = 24;     /* px of travel that counts as a real pull       */
  const DRAG_START = 6;     /* px before a press becomes a drag              */

  let dragging = false;
  let startY = 0;
  let scale = 1;
  let pull = 0;
  let travelled = 0;
  let swallowClick = false; /* a finished drag must not toggle a second time */

  const isOn = () => page.getAttribute('data-light') === 'on';

  /* ------------------------------------------------------------- painting */

  function setPull(px) {
    pull = Math.max(0, Math.min(MAX_PULL, px));
    rig.style.setProperty('--pull', pull.toFixed(1) + 'px');
  }

  function setLight(on) {
    page.setAttribute('data-light', on ? 'on' : 'off');
    handle.setAttribute('aria-pressed', String(on));
    /* the cord controls the sign-in card: expose that relationship to
       assistive technology instead of relying on the animation alone */
    handle.setAttribute('aria-expanded', String(on));
    handle.setAttribute('aria-label', on ? 'Turn the light off' : 'Turn the light on');
    const sr = handle.querySelector('.light-sr-only');
    if (sr) sr.textContent = on
      ? 'Pull to turn the light off'
      : 'Pull to turn the light on';

    /* keep the hidden form out of the accessibility tree and tab order while
       the room is dark; it becomes fully available the moment the light is on */
    if (card) {
      if ('inert' in card) card.inert = !on;
      if (on) card.removeAttribute('aria-hidden');
      else card.setAttribute('aria-hidden', 'true');
    }

    /* #lightHint is role="status": the new instruction is announced */
    if (hint) {
      hint.textContent = on
        ? 'Light on — pull the cord again to turn it off.'
        : 'Pull the cord to turn on the light.';
    }
  }

  function toggle() {
    setLight(!isOn());
  }

  /* --------------------------------------------------------------- drag */

  function beginDrag(event) {
    if (typeof event.button === 'number' && event.button !== 0) return;
    swallowClick = false;
    dragging = true;
    travelled = 0;
    startY = event.clientY;
    scale = parseFloat(window.getComputedStyle(rig).getPropertyValue('--rig-scale')) || 1;
    rig.classList.add('is-dragging');
    try { handle.setPointerCapture(event.pointerId); } catch (e) { /* old browser */ }
    if (event.cancelable) event.preventDefault();
  }

  function moveDrag(event) {
    if (!dragging) return;
    const dy = (event.clientY - startY) / (scale || 1);   /* screen px -> rig px */
    if (Math.abs(dy) > DRAG_START) travelled = Math.max(travelled, Math.abs(dy));
    setPull(dy);
    if (event.cancelable) event.preventDefault();         /* no page scrolling */
  }

  function endDrag() {
    if (!dragging) return;
    dragging = false;
    rig.classList.remove('is-dragging');
    const pulled = pull;
    setPull(0);                                           /* springs back in CSS */

    if (travelled >= THRESHOLD && pulled >= THRESHOLD) {
      swallowClick = true;
      window.setTimeout(() => { swallowClick = false; }, 400);
      toggle();
    }
  }

  function cancelDrag() {
    if (!dragging) return;
    dragging = false;
    rig.classList.remove('is-dragging');
    setPull(0);
  }

  handle.addEventListener('pointerdown', beginDrag);
  handle.addEventListener('pointermove', moveDrag);
  handle.addEventListener('pointerup', endDrag);
  handle.addEventListener('pointercancel', cancelDrag);

  /* keyboard + plain click on the handle (the drag-free fallback) */
  handle.addEventListener('click', (event) => {
    if (swallowClick) { swallowClick = false; event.preventDefault(); return; }
    toggle();
  });

  /* a lost pointer (tab switched, pointer left the window) must not leave
     the chain stuck stretched */
  window.addEventListener('blur', cancelDrag);

  /* ---------------------------------------------------------------- boot */
  /* The page always starts with the light OFF - this only makes sure the
     hint and the switch state agree with the markup on first paint.  No
     storage is read or written: the room is dark on every visit. */
  setLight(isOn());
  setPull(0);
})();
