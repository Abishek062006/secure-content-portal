export function formatRelativeTime(dateString) {
  if (!dateString) return '';
  const date = new Date(dateString);
  const diffSeconds = Math.floor((Date.now() - date.getTime()) / 1000);

  if (diffSeconds < 60) return 'Just now';
  const diffMinutes = Math.floor(diffSeconds / 60);
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays === 1) return 'Yesterday';
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

/** `cls` is a `cat-*` suffix for the `.badge`/`.notification-icon` colour variants in app.css. */
const CATEGORY_META = {
  COURSE: { label: 'Course', icon: 'book-open', cls: 'cat-course' },
  QUIZ: { label: 'Quiz', icon: 'clipboard-check', cls: 'cat-quiz' },
  COMMUNITY: { label: 'Community', icon: 'message-circle', cls: 'cat-community' },
  ANNOUNCEMENT: { label: 'Announcement', icon: 'megaphone', cls: 'cat-announcement' },
  ACHIEVEMENT: { label: 'Achievement', icon: 'award', cls: 'cat-achievement' },
  SECURITY: { label: 'Security', icon: 'lock', cls: 'cat-security' },
  SYSTEM: { label: 'System', icon: 'info', cls: 'cat-system' },
  HACKATHON: { label: 'Hackathon', icon: 'award', cls: 'cat-hackathon' },
};

export function getCategoryMeta(category) {
  return CATEGORY_META[category] || CATEGORY_META.ANNOUNCEMENT || { label: 'Notification', icon: 'bell', cls: 'cat-announcement' };
}

export function getActionLabel(notification) {
  const { category, actionUrl: url = '' } = notification;
  if (url.includes('/admin/users')) return 'Manage users';
  if (url.includes('/lessons/')) return 'View lesson';
  if (url.includes('/attempts/')) return 'View result';
  if (url.includes('/assessments/')) return 'Take quiz';
  if (url.includes('/courses/')) return 'Open course';
  if (url.includes('/workspace')) return 'Open workspace';
  if (url.includes('/leaderboard')) return 'View leaderboard';
  if (url.includes('/certificate')) return 'View certificate';
  if (url.includes('/judge')) return 'Go to judging';
  if (url.includes('/profile')) return 'View badge';
  if (url.includes('/feed')) return 'View reply';
  if (category === 'HACKATHON') return 'View hackathon';
  if (category === 'ANNOUNCEMENT') return 'View announcement';
  return 'View details';
}
