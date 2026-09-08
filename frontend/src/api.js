// ?? (not ||) so an intentionally empty VITE_API_URL (production — see
// .env.production) is preserved as "same origin", rather than falling back
// to the localhost default meant only for unset/local dev.
export const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

function readCookie(name) {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * The backend forces its CSRF cookie to be written on every response (see
 * CsrfCookieFilter), so by the time a state-changing request is made,
 * GET /api/me on load has already set it.
 */
async function request(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = { ...(options.headers || {}) };

  if (method !== 'GET' && method !== 'HEAD') {
    const csrfToken = readCookie('XSRF-TOKEN');
    if (csrfToken) {
      headers['X-XSRF-TOKEN'] = csrfToken;
    }
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    method,
    headers,
    credentials: 'include',
  });

  if (path === '/api/me') {
    // eslint-disable-next-line no-console
    console.log('[api] /api/me raw response', {
      status: response.status,
      ok: response.ok,
      redirected: response.redirected,
      url: response.url,
      contentType: response.headers.get('content-type'),
    });
  }

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      if (body && body.error) {
        message = body.error;
      }
    } catch {
      // No JSON body — keep the generic message.
    }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  if (response.status === 204) {
    return null;
  }
  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('application/json') ? response.json() : null;
}

export const api = {
  get: (path) => request(path),
  post: (path, body) =>
    request(path, {
      method: 'POST',
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
    }),
  put: (path, body) =>
    request(path, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),
  del: (path) => request(path, { method: 'DELETE' }),
  upload: (path, formData) => request(path, { method: 'POST', body: formData }),
};
