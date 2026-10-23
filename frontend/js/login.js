/* ============================================================================
 * smart-campus :: login.js
 * ----------------------------------------------------------------------------
 * Front-end half of the authentication flow.
 *
 *   1. read messages left in the URL (expired session, signed out)
 *   2. if a session is already active, jump straight to the dashboard
 *   3. on submit -> POST /api/auth/login
 *   4. the server validates the credentials, hashes the password with BCrypt
 *      and returns a token + the role taken from the database
 *   5. store the session and go to index.html
 *
 * The browser never decides whether the credentials are correct - if they are
 * wrong the server answers 401 and we simply display its message.
 * ========================================================================== */

(function () {
  'use strict';

  const form = document.getElementById('loginForm');
  const emailInput = document.getElementById('email');
  const passwordInput = document.getElementById('password');
  const rememberInput = document.getElementById('rememberMe');
  const submitBtn = document.getElementById('loginBtn');
  const alertBox = document.getElementById('loginAlert');
  const alertText = document.getElementById('loginAlertText');

  document.getElementById('year').textContent = new Date().getFullYear();

  /* ------------------------------------------------ 3D scene parallax */
  /* Tilts the CSS 3D campus a couple of degrees with the pointer so the
     scene reacts to the visitor. Purely decorative, and skipped when the
     system asks for reduced motion. */

  (function parallax() {
    const scene = document.getElementById('campus3d');
    if (!scene) return;

    const tilt = scene.querySelector('.c3-tilt');
    if (!tilt) return;

    const calm = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (calm || !window.matchMedia('(hover: hover)').matches) {
      tilt.style.setProperty('--tilt-x', '0deg');
      tilt.style.setProperty('--tilt-y', '0deg');
      return;
    }

    const MAX_X = 7;   /* degrees */
    const MAX_Y = 9;

    let frame = null;

    window.addEventListener('pointermove', (event) => {
      if (frame) return;
      frame = requestAnimationFrame(() => {
        frame = null;
        const rect = scene.getBoundingClientRect();
        if (!rect.width || !rect.height) return;

        const nx = ((event.clientX - rect.left) / rect.width - 0.5) * 2;
        const ny = ((event.clientY - rect.top) / rect.height - 0.5) * 2;

        tilt.style.setProperty('--tilt-x', (-ny * MAX_X).toFixed(2) + 'deg');
        tilt.style.setProperty('--tilt-y', (nx * MAX_Y).toFixed(2) + 'deg');
      });
    }, { passive: true });

    window.addEventListener('pointerleave', () => {
      tilt.style.setProperty('--tilt-x', '0deg');
      tilt.style.setProperty('--tilt-y', '0deg');
    }, { passive: true });
  })();

  /* --------------------------------------------------- friendly messages */

  const reason = Auth.urlParam('reason') || Auth.urlParam('expired') || Auth.urlParam('signedout');
  if (reason === 'expired') {
    showAlert('Your session has expired. Please sign in again.', 'warning');
  } else if (reason === 'signedout') {
    showAlert('You have been signed out successfully.', 'success');
  } else if (Auth.urlParam('error')) {
    showAlert(Auth.urlParam('error'), 'danger');
  }

  /* --------------------------------------------- already signed in? leave */

  Auth.status().then((status) => {
    if (status.authenticated) location.replace('index.html');
  });

  /* ----------------------------------------------------- show / hide pass */

  document.getElementById('togglePassword').addEventListener('click', function () {
    const showing = passwordInput.type === 'text';
    passwordInput.type = showing ? 'password' : 'text';
    this.innerHTML = showing
      ? '<i class="bi bi-eye"></i> Show'
      : '<i class="bi bi-eye-slash"></i> Hide';
  });

  /* ------------------------------------------------------ demo credentials */

  document.querySelectorAll('[data-demo]').forEach((button) => {
    button.addEventListener('click', () => {
      const [email, password] = button.dataset.demo.split('|');
      emailInput.value = email;
      passwordInput.value = password;
      emailInput.focus();
      clearAlert();
      form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));
      toastInfo('Credentials filled in. Press “Sign in” to continue.', 'Demo account');
    });
  });

  /* ------------------------------------------------------------- validate */

  function validate() {
    let valid = true;
    [emailInput, passwordInput].forEach((input) => input.classList.remove('is-invalid'));

    if (!emailInput.value.trim()) {
      emailInput.classList.add('is-invalid');
      valid = false;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailInput.value.trim())) {
      emailInput.classList.add('is-invalid');
      valid = false;
    }

    if (!passwordInput.value) {
      passwordInput.classList.add('is-invalid');
      valid = false;
    }
    return valid;
  }

  [emailInput, passwordInput].forEach((input) => {
    input.addEventListener('input', () => input.classList.remove('is-invalid'));
  });

  /* ------------------------------------------------------------- messages */

  function showAlert(message, kind = 'danger') {
    alertBox.className = 'auth-alert show ' + kind;
    alertText.textContent = message;
    alertBox.querySelector('i').className =
      kind === 'success' ? 'bi bi-check-circle-fill mt-1' : 'bi bi-exclamation-circle-fill mt-1';
  }

  function clearAlert() {
    alertBox.className = 'auth-alert';
    alertText.textContent = '';
  }

  /* --------------------------------------------------------------- submit */

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearAlert();

    if (!validate()) return;

    const original = submitBtn.innerHTML;
    submitBtn.disabled = true;
    submitBtn.innerHTML =
      '<span class="spinner-border spinner-border-sm me-2" role="status"></span>Signing in...';

    try {
      const result = await Auth.login(
        emailInput.value.trim(),
        passwordInput.value,
        rememberInput.checked
      );

      showAlert('Signed in as ' + result.user.fullName + ' (' + result.user.role + '). Redirecting...', 'success');
      toastSuccess('Welcome back, ' + result.user.fullName + '!', 'Signed in');

      /* ADMIN/FACULTY land on the dashboard, students land on their profile. */
      const target = result.user.role === 'STUDENT' ? 'index.html' : 'index.html';
      setTimeout(() => { location.href = target; }, 450);
    } catch (error) {
      const message = errorMessage(error, 'Unable to sign in.');
      showAlert(message, 'danger');
      toastError(message, 'Sign in failed');

      if (error.status === 401) {
        passwordInput.value = '';
        passwordInput.focus();
      }
      submitBtn.disabled = false;
      submitBtn.innerHTML = original;
    }
  });
})();
