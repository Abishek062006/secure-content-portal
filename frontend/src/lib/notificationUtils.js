export function formatRelativeTime(dateString) {
  if (!dateString) return '';
  const date = new Date(dateString);
  const now = new Date();
  const diffSeconds = Math.floor((now - date) / 1000);

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

export function getCategoryMeta(category) {
  switch (category) {
    case 'COURSE':
      return { label: 'Course', icon: 'book-open', color: '#2563eb', bg: '#eff6ff' };
    case 'QUIZ':
      return { label: 'Quiz', icon: 'clipboard-check', color: '#d97706', bg: '#fef3c7' };
    case 'COMMUNITY':
      return { label: 'Community', icon: 'message-circle', color: '#7c3aed', bg: '#f5f3ff' };
    case 'ANNOUNCEMENT':
      return { label: 'Announcement', icon: 'megaphone', color: '#059669', bg: '#ecfdf5' };
    case 'CONTENT':
      return { label: 'Content', icon: 'video', color: '#0891b2', bg: '#ecfeff' };
    case 'MODERATION':
      return { label: 'Moderation', icon: 'shield-check', color: '#ea580c', bg: '#fff7ed' };
    case 'SECURITY':
      return { label: 'Security', icon: 'lock', color: '#dc2626', bg: '#fef2f2' };
    case 'SYSTEM':
    default:
      return { label: 'System', icon: 'info', color: '#475569', bg: '#f1f5f9' };
  }
}

export function getActionLabel(notification) {
  const cat = notification.category;
  const url = notification.actionUrl || '';
  if (url.includes('/admin/users')) return 'Manage Users';
  if (url.includes('/admin/content')) return 'Manage Content';
  if (url.includes('/lessons/')) return 'View Lesson';
  if (url.includes('/assessments/') || url.includes('/attempts/')) return url.includes('/attempts/') ? 'View Result' : 'Take Quiz';
  if (url.includes('/courses/')) return 'Open Course';
  if (url.includes('/feed')) return 'View Reply';
  if (cat === 'ANNOUNCEMENT') return 'View Announcement';
  if (cat === 'CONTENT') return 'View Content';
  if (cat === 'MODERATION') return 'Review';
  if (cat === 'SECURITY') return 'Security Alert';
  return 'View Details';
}
