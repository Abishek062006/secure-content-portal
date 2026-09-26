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

/**
 * fetch() can't report upload progress, and a lecture recording is big enough
 * that a bare "Uploading…" reads as a hang — hence XMLHttpRequest here.
 */
function uploadWithProgress(path, formData, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_BASE}${path}`);
    xhr.withCredentials = true;
    const csrfToken = readCookie('XSRF-TOKEN');
    if (csrfToken) {
      xhr.setRequestHeader('X-XSRF-TOKEN', csrfToken);
    }
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(e.loaded / e.total);
    };
    xhr.onload = () => {
      let body = null;
      try {
        body = JSON.parse(xhr.responseText);
      } catch {
        // Non-JSON body — fall through to the status check.
      }
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(body);
        return;
      }
      const error = new Error((body && body.error) || `Request failed (${xhr.status})`);
      error.status = xhr.status;
      reject(error);
    };
    xhr.onerror = () => reject(new Error('Network error — the upload did not complete.'));
    xhr.send(formData);
  });
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
  uploadWithProgress,

  // Gamification APIs
  getGamificationSummary: () => request('/api/gamification/me'),
  checkInDaily: () => api.post('/api/gamification/check-in'),
  getLeaderboard: (timeframe = 'all_time', stream = 'all') =>
    request(`/api/gamification/leaderboard?timeframe=${encodeURIComponent(timeframe)}&stream=${encodeURIComponent(stream)}`),
  getBadges: () => request('/api/gamification/badges'),
  getUserBadges: (userId) => request(`/api/gamification/users/${userId}/badges`),
  getStreams: () => request('/api/gamification/streams'),
  getMyPointHistory: () => request('/api/points/history'),

  // Admin Gamification APIs
  getAdminLeaderboard: (timeframe = 'all_time', stream = 'all') =>
    request(`/api/admin/gamification/leaderboard?timeframe=${encodeURIComponent(timeframe)}&stream=${encodeURIComponent(stream)}`),
  getAdminPointRules: () => request('/api/admin/gamification/rules'),
  updateAdminPointRule: (actionType, points) => api.put(`/api/admin/gamification/rules/${encodeURIComponent(actionType)}`, { points }),
  adjustAdminUserXp: (userId, amount, reason) => api.post('/api/admin/gamification/adjust', { userId, amount, reason }),
  getAdminPointHistory: (userId = '', actionType = '', stream = '') => {
    const params = new URLSearchParams();
    if (userId) params.append('userId', userId);
    if (actionType) params.append('actionType', actionType);
    if (stream) params.append('stream', stream);
    return request(`/api/admin/gamification/history?${params.toString()}`);
  },

  // Hackathon APIs
  getHackathons: (stream = 'all', mode = 'all') =>
    request(`/api/hackathons?stream=${encodeURIComponent(stream)}&mode=${encodeURIComponent(mode)}`),
  registerHackathon: (id) => api.post(`/api/hackathons/${id}/register`),
  getAdminHackathons: (stream = 'all', mode = 'all') =>
    request(`/api/admin/hackathons?stream=${encodeURIComponent(stream)}&mode=${encodeURIComponent(mode)}`),
  createAdminHackathon: (data) => api.post('/api/admin/hackathons', data),
  updateAdminHackathon: (id, data) => api.put(`/api/admin/hackathons/${id}`, data),
  deleteAdminHackathon: (id) => api.del(`/api/admin/hackathons/${id}`),

  // Mock Interview APIs
  startMockInterview: (track, stream, difficulty) =>
    api.post('/api/interviews/start', { track, stream, difficulty }),
  getMockInterviewSession: (sessionId) => request(`/api/interviews/sessions/${sessionId}`),
  submitMockInterviewAnswer: (sessionId, questionId, learnerAnswer) =>
    api.post(`/api/interviews/sessions/${sessionId}/answer`, { questionId, learnerAnswer }),
  completeMockInterviewSession: (sessionId) => api.post(`/api/interviews/sessions/${sessionId}/complete`),
  getMockInterviewHistory: () => request('/api/interviews/history'),
  getAdminMockInterviewAnalytics: () => request('/api/admin/interviews/analytics'),
  getAdminMockInterviewSession: (sessionId) => request(`/api/admin/interviews/sessions/${sessionId}`),
};
