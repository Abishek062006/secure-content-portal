import { API_BASE } from '../api';

/** Our own images are relative to the API; a Google profile picture is already a full address. */
export function mediaUrl(url) {
  if (!url) return null;
  return url.startsWith('/') ? `${API_BASE}${url}` : url;
}
