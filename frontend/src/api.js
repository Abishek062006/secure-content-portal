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
  getLeaderboard: (timeframe = 'weekly', courseId = '') =>
    request(`/api/gamification/leaderboard?timeframe=${encodeURIComponent(timeframe)}${courseId ? `&courseId=${encodeURIComponent(courseId)}` : ''}`),
  setLeaderboardHidden: (hidden) => api.put('/api/gamification/me/leaderboard-visibility', { hidden }),
  getBadges: () => request('/api/gamification/badges'),
  getUserBadges: (userId) => request(`/api/gamification/users/${userId}/badges`),
  getMyPointHistory: () => request('/api/points/history'),

  // Hackathons
  getHackathons: (mode = 'all', saved = false) =>
    request(`/api/hackathons?mode=${encodeURIComponent(mode)}&saved=${saved}`),
  saveHackathon: (id) => api.put(`/api/hackathons/${id}/save`, {}),
  unsaveHackathon: (id) => api.del(`/api/hackathons/${id}/save`),
  getHackathon: (id) => request(`/api/hackathons/${id}`),
  getMyTeam: (id) => request(`/api/hackathons/${id}/team`),
  createTeam: (id, name, track) => api.post(`/api/hackathons/${id}/team`, { name, track }),
  joinTeam: (inviteCode) => api.post('/api/hackathons/join', { inviteCode }),
  leaveTeam: (id) => api.del(`/api/hackathons/${id}/team`),
  submitProject: (id, body) => api.put(`/api/hackathons/${id}/submission`, body),
  getHackathonResults: (id) => request(`/api/hackathons/${id}/results`),
  getHackathonProblems: (id) => request(`/api/hackathons/${id}/problems`),
  selectTeamProblem: (id, problemStatementId) => api.put(`/api/hackathons/${id}/team/problem`, { problemStatementId }),
  /** The learner's certificate for this event, or null when they have none (the API answers 204). */
  getHackathonCertificate: (id) => request(`/api/hackathons/${id}/certificate`),
  downloadHackathonCertificatePdf: (id) => { window.open(`${API_BASE}/api/hackathons/${id}/certificate/pdf`, '_blank'); },
  getJudgedEvents: () => request('/api/judging/events'),
  getProjectsToJudge: (id) => request(`/api/judging/hackathons/${id}/submissions`),
  scoreProject: (id, submissionId, body) => api.put(`/api/judging/hackathons/${id}/submissions/${submissionId}/score`, body),
  getHackathonJudges: (id) => request(`/api/admin/hackathons/${id}/judges`),
  addHackathonJudge: (id, email) => api.post(`/api/admin/hackathons/${id}/judges`, { email }),
  removeHackathonJudge: (id, userId) => api.del(`/api/admin/hackathons/${id}/judges/${userId}`),
  getHackathonProjects: (id) => request(`/api/admin/hackathons/${id}/submissions`),
  getHackathonStandings: (id) => request(`/api/admin/hackathons/${id}/standings`),
  getAdminHackathonProblems: (id) => request(`/api/admin/hackathons/${id}/problems`),
  createAdminHackathonProblem: (id, data) => api.post(`/api/admin/hackathons/${id}/problems`, data),
  updateAdminHackathonProblem: (id, problemId, data) => api.put(`/api/admin/hackathons/${id}/problems/${problemId}`, data),
  deleteAdminHackathonProblem: (id, problemId) => api.del(`/api/admin/hackathons/${id}/problems/${problemId}`),
  publishHackathon: (id) => api.post(`/api/admin/hackathons/${id}/publish`),
  uploadHackathonBanner: (id, file) => {
    const form = new FormData();
    form.append('file', file);
    return api.upload(`/api/admin/hackathons/${id}/banner`, form);
  },
  getAdminHackathons: () => request('/api/admin/hackathons'),
  createAdminHackathon: (data) => api.post('/api/admin/hackathons', data),
  updateAdminHackathon: (id, data) => api.put(`/api/admin/hackathons/${id}`, data),
  deleteAdminHackathon: (id) => api.del(`/api/admin/hackathons/${id}`),
  /** Fetches the .ics file with the session cookie, then hands it to the browser as a download. */
  async downloadHackathonCalendar(id) {
    const response = await fetch(`${API_BASE}/api/hackathons/${id}/calendar.ics`, { credentials: 'include' });
    if (!response.ok) {
      let message = 'Could not create the calendar file.';
      try { message = (await response.json()).error || message; } catch { /* keep the generic message */ }
      throw new Error(message);
    }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = `hackathon-${id}.ics`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  },

  // AI assistant
  sendAiChatMessage: (message, history, attachmentName, attachmentText) =>
    api.post('/api/ai/chat', { message, history, attachmentName, attachmentText }),

  // Network and connections
  getNetworkSuggestions: (q = '', page = 0, size = 20) => {
    const params = new URLSearchParams({ page, size });
    if (q) params.set('q', q);
    return request(`/api/network/suggestions?${params}`);
  },
  sendConnectionRequest: (userId) => api.post(`/api/network/requests/${userId}`),
  getReceivedRequests: () => request('/api/network/requests/received'),
  getSentRequests: () => request('/api/network/requests/sent'),
  acceptConnectionRequest: (id) => api.post(`/api/network/requests/${id}/accept`),
  rejectConnectionRequest: (id) => api.post(`/api/network/requests/${id}/reject`),
  withdrawConnectionRequest: (id) => api.del(`/api/network/requests/${id}`),
  getConnections: () => request('/api/network/connections'),
  removeConnection: (userId) => api.del(`/api/network/connections/${userId}`),
  getConnectionsCount: () => request('/api/network/connections/count'),

  // Interview practice
  startInterview: (body) => api.post('/api/interviews/start', body),
  retryInterview: (id) => api.post(`/api/interviews/sessions/${id}/retry`),
  getInterview: (id) => request(`/api/interviews/sessions/${id}`),
  answerInterview: (id, questionId, learnerAnswer, delivery) => api.post(`/api/interviews/sessions/${id}/answer`, { questionId, learnerAnswer, delivery }),
  completeInterview: (id, body) => api.post(`/api/interviews/sessions/${id}/complete`, body),
  getInterviewHistory: () => request('/api/interviews/history'),
  getDeliveryTrend: () => request('/api/interviews/delivery-trend'),
  getInterviewQuota: () => request('/api/interviews/quota'),
  transcribeAnswer: (blob, durationSeconds, sessionId) => {
    const form = new FormData();
    if (sessionId) form.append('sessionId', String(sessionId));
    form.append('audio', blob, 'answer');
    form.append('consent', 'true');
    form.append('durationSeconds', String(durationSeconds));
    return api.upload('/api/interviews/transcribe', form);
  },
  getResume: () => request('/api/interviews/resume'),
  uploadResume: (file, consent) => {
    const form = new FormData();
    form.append('file', file);
    form.append('consent', String(consent));
    return api.upload('/api/interviews/resume', form);
  },
  deleteResume: () => api.del('/api/interviews/resume'),

  // Notifications
  getNotifications: ({ category, unreadOnly, page = 0, size = 20 } = {}) => {
    const params = new URLSearchParams({ page, size });
    if (category) params.set('category', category);
    if (unreadOnly) params.set('unreadOnly', 'true');
    return request(`/api/notifications?${params}`);
  },
  getRecentNotifications: () => request('/api/notifications/recent'),
  getUnreadNotificationCount: () => request('/api/notifications/unread-count'),
  markNotificationRead: (id) => api.put(`/api/notifications/${id}/read`, {}),
  markNotificationUnread: (id) => api.put(`/api/notifications/${id}/unread`, {}),
  markAllNotificationsRead: () => api.put('/api/notifications/read-all', {}),
  deleteNotification: (id) => api.del(`/api/notifications/${id}`),
  clearReadNotifications: () => api.del('/api/notifications/clear-read'),
  sendAdminAnnouncement: (data) => api.post('/api/admin/notifications/announcement', data),
};
