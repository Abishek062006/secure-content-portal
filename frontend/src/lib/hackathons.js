const DAY = 24 * 60 * 60 * 1000;

const dateFormat = { day: 'numeric', month: 'short', year: 'numeric' };

export function formatDate(iso) {
  return iso ? new Date(iso).toLocaleDateString(undefined, dateFormat) : null;
}

/** "Oct 3 - Oct 5, 2026", "Oct 3, 2026", or null when the organiser hasn't given dates. */
export function dateRange(start, end) {
  const from = formatDate(start);
  const to = formatDate(end);
  if (!from) return null;
  return to && to !== from ? `${from} to ${to}` : from;
}

/** How long registration stays open: "Closes in 3 days", "Closes today", or null when there is no deadline. */
export function deadlineLabel(hackathon, now = Date.now()) {
  if (!hackathon.registrationOpen) return 'Registration closed';
  if (!hackathon.registrationDeadline) return null;
  const left = new Date(hackathon.registrationDeadline).getTime() - now;
  if (left < DAY) return 'Closes today';
  const days = Math.ceil(left / DAY);
  return days === 1 ? 'Closes tomorrow' : `Closes in ${days} days`;
}

/** Saved and open, with a deadline inside a week: what a learner should be reminded about. */
export function closingSoon(hackathon, now = Date.now()) {
  if (!hackathon.saved || !hackathon.registrationOpen || !hackathon.registrationDeadline) return false;
  const left = new Date(hackathon.registrationDeadline).getTime() - now;
  return left <= 7 * DAY;
}

export const MODE_LABEL = { ONLINE: 'Online', OFFLINE: 'In person', HYBRID: 'Hybrid' };
