/* ============================================================================
 * smart-campus :: api.js
 * ----------------------------------------------------------------------------
 * The ONE place where the front end talks to the Spring Boot backend.
 *
 * Every module (students, faculty, courses, ...) uses these helpers, so fetch
 * logic, authentication headers and error handling are written once.
 *
 *   api.get(path)            GET    -> returns payload (data)
 *   api.post(path, body)     POST   -> returns payload
 *   api.put(path, body)      PUT    -> returns payload
 *   api.patch(path, body)    PATCH  -> returns payload
 *   api.del(path)            DELETE -> returns payload
 *   api.download(path)       GET    -> returns a Response (CSV export)
 *
 * On failure every helper throws an ApiError carrying:
 *   .message   - friendly text from the backend
 *   .status    - HTTP status code (400, 401, 403, 404, 409, 500...)
 *   .errors    - { field: message } map produced by server-side validation
 * ==========================================================================*/

/** Base URL of the REST API.
 *
 *  Production (Vercel) and the bundled Spring Boot server both serve the UI
 *  and the API from the same origin, so "/api" is all that is needed.
 *  When the HTML is opened through VS Code Live Server (or directly from
 *  disk), the backend is on localhost:8080 instead.
 *
 *  A host can optionally override this before api.js loads:
 *      window.SMART_CAMPUS_API_BASE = 'https://api.example.com/api';
 */
function resolveApiBase() {
  const configured = (window.SMART_CAMPUS_API_BASE || '').trim();
  if (configured) return configured.replace(/\/+$/, '');

  const localHost = location.hostname === 'localhost'
    || location.hostname === '127.0.0.1'
    || location.protocol === 'file:';
  if (localHost && location.port !== '8080') {
    return 'http://localhost:8080/api';
  }
  return '/api';
}

const API_BASE = resolveApiBase();

const TOKEN_KEY = 'smartcampus.token';
const USER_KEY = 'smartcampus.user';
const PERMS_KEY = 'smartcampus.permissions';

/** Error type thrown by every api helper. */
class ApiError extends Error {
  constructor(message, status, errors) {
    super(message);
    this.name = 'ApiError';
    this.status = status || 0;
    this.errors = errors || null;
  }
}

/* ------------------------------------------------------------------ token */

function getToken() {
  return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || '';
}

function saveSession(token, user, permissions, remember) {
  const store = remember ? localStorage : sessionStorage;
  const other = remember ? sessionStorage : localStorage;
  other.removeItem(TOKEN_KEY);
  other.removeItem(USER_KEY);
  other.removeItem(PERMS_KEY);

  store.setItem(TOKEN_KEY, token);
  store.setItem(USER_KEY, JSON.stringify(user || null));
  store.setItem(PERMS_KEY, JSON.stringify(permissions || []));
}

function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem(PERMS_KEY);
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
  sessionStorage.removeItem(PERMS_KEY);
}

function getCachedUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY) || 'null');
  } catch (e) {
    return null;
  }
}

function getCachedPermissions() {
  try {
    return JSON.parse(localStorage.getItem(PERMS_KEY) || sessionStorage.getItem(PERMS_KEY) || '[]');
  } catch (e) {
    return [];
  }
}

/* ------------------------------------------------------------ core request */

/**
 * Runs one HTTP request against the API.
 * @param {string} path    e.g. "/students?page=0&size=10"
 * @param {object} options fetch options (method, body, headers...)
 * @returns {Promise<any>} the `data` member of the JSON envelope
 * @throws {ApiError} for every non-2xx response or network failure
 */
async function apiRequest(path, options = {}) {
  const url = /^https?:/i.test(path) ? path : API_BASE + path;

  const headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const token = getToken();
  if (token) headers.Authorization = 'Bearer ' + token;

  let response;
  try {
    response = await fetch(url, Object.assign({}, options, { headers }));
  } catch (networkError) {
    throw new ApiError(
      'Cannot reach the server. Check the backend/API URL and try again.',
      0
    );
  }

  /* ---- session no longer valid -------------------------------------- */
  if (response.status === 401) {
    clearSession();
    const isAuthCall = path.indexOf('/auth/') !== -1;
    if (!isAuthCall && !location.pathname.endsWith('login.html')) {
      setTimeout(() => { location.href = 'login.html?expired=1'; }, 250);
    }
    throw new ApiError('Your session has expired. Please sign in again.', 401);
  }

  /* ---- non JSON payloads (CSV download, file stream) ---------------- */
  const contentType = response.headers.get('content-type') || '';
  if (contentType.indexOf('application/json') === -1) {
    if (!response.ok) {
      throw new ApiError('Request failed (HTTP ' + response.status + ').', response.status);
    }
    return response;
  }

  let payload;
  try {
    payload = await response.json();
  } catch (parseError) {
    throw new ApiError('The server returned an unreadable response.', response.status);
  }

  if (!response.ok || payload.success === false) {
    const message = payload && payload.message
      ? payload.message
      : 'Request failed (HTTP ' + response.status + ').';
    throw new ApiError(message, response.status, payload ? payload.errors : null);
  }

  return payload.data;
}

/** Returns the whole envelope (success/message/data) instead of just data. */
async function apiEnvelope(path, options = {}) {
  const url = /^https?:/i.test(path) ? path : API_BASE + path;
  const headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  const token = getToken();
  if (token) headers.Authorization = 'Bearer ' + token;

  const response = await fetch(url, Object.assign({}, options, { headers }));
  const payload = await response.json().catch(() => ({ success: false, message: 'Unreadable response.' }));
  if (response.status === 401) clearSession();
  if (!response.ok || payload.success === false) {
    throw new ApiError(payload.message || 'Request failed.', response.status, payload.errors);
  }
  return payload;
}

/** Builds "?key=value" from a plain object, dropping empty values. */
function queryString(params) {
  if (!params) return '';
  const parts = [];
  Object.keys(params).forEach((key) => {
    const value = params[key];
    if (value === undefined || value === null || value === '') return;
    parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value));
  });
  return parts.length ? '?' + parts.join('&') : '';
}

/**
 * Safer than `path + queryString(...)`: it picks the right separator when the
 * path already carries a query string.
 *   withQuery('/bookings/1', {a:1})  -> '/bookings/1?a=1'
 *   withQuery('/x?a=1',    {b:2})    -> '/x?a=1&b=2'
 */
function withQuery(path, params) {
  const qs = queryString(params);
  if (!qs) return path;
  const sep = path.indexOf('?') === -1 ? '?' : '&';
  return path + sep + qs.substring(1);
}

/** Public wrapper object used by every module script. */
const api = {
  get: (path, params) => apiRequest(withQuery(path, params)),
  post: (path, body) => apiRequest(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: (path, body) => apiRequest(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  patch: (path, body) => apiRequest(path, { method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) }),
  del: (path) => apiRequest(path, { method: 'DELETE' }),
  envelope: apiEnvelope,
  download: (path) => apiRequest(path, { headers: { Accept: 'text/csv' } }),
  raw: apiRequest
};

/**
 * Convenience GET with query parameters.
 *   await apiGet('/students', { page: 0, size: 10, keyword: 'rahul' })
 */
async function apiGet(path, params) {
  return apiRequest(withQuery(path, params));
}
