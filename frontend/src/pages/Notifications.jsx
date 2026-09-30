import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import Alert from '../components/Alert';
import AnnouncementModal from '../components/AnnouncementModal';
import { api } from '../api';
import { formatRelativeTime, getCategoryMeta, getActionLabel } from '../lib/notificationUtils';

const ACTIVE_CATEGORIES = [
  { id: 'COURSE', label: 'Courses', icon: 'book-open' },
  { id: 'HACKATHON', label: 'Hackathons', icon: 'award' },
  { id: 'QUIZ', label: 'Quizzes', icon: 'clipboard-check' },
  { id: 'ANNOUNCEMENT', label: 'Announcements', icon: 'megaphone' },
  { id: 'COMMUNITY', label: 'Community', icon: 'message-circle' },
  { id: 'ACHIEVEMENT', label: 'Achievements', icon: 'award' },
];

const DEV_SAMPLE_NOTIFICATIONS = [
  {
    id: 101,
    category: 'ANNOUNCEMENT',
    priority: 'IMPORTANT',
    title: 'Platform Maintenance & System Update',
    message: 'GradientNova AI will undergo scheduled maintenance tonight at 11:00 PM UTC. New features will be deployed.',
    actionUrl: '/courses',
    isRead: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 15).toISOString(),
  },
  {
    id: 102,
    category: 'COURSE',
    priority: 'NORMAL',
    title: 'New Lesson: Prompt Engineering Fundamentals',
    message: 'A brand new module on Advanced Chain-of-Thought prompting has been added to your enrolled course.',
    actionUrl: '/courses',
    isRead: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
  },
  {
    id: 103,
    category: 'QUIZ',
    priority: 'NORMAL',
    title: 'Quiz Result Available: Neural Networks 101',
    message: 'You scored 92% on Neural Networks 101! Your certificate of completion has been updated.',
    actionUrl: '/progress',
    isRead: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
  },
  {
    id: 104,
    category: 'ACHIEVEMENT',
    priority: 'NORMAL',
    title: 'Badge Unlocked: 7-Day Code Streak!',
    message: 'Congratulations! You have completed lessons 7 days in a row. Keep the momentum going.',
    actionUrl: '/profile',
    isRead: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 36).toISOString(),
  },
  {
    id: 105,
    category: 'COMMUNITY',
    priority: 'NORMAL',
    title: 'New Reply on Discussion Thread',
    message: 'Alex Carter replied to your question about Transformer self-attention weights.',
    actionUrl: '/feed',
    isRead: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 48).toISOString(),
  },
  {
    id: 106,
    category: 'SECURITY',
    priority: 'CRITICAL',
    title: 'Security Alert: Role Updated to Administrator',
    message: 'Your account permissions have been upgraded to Administrator by the system admin.',
    actionUrl: '/admin/dashboard',
    isRead: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 72).toISOString(),
  },
];

const sync = () => window.dispatchEvent(new CustomEvent('portal-notification-change'));

export default function Notifications() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const isAdmin = Boolean(user?.admin);

  const [category, setCategory] = useState('');
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [announceOpen, setAnnounceOpen] = useState(false);

  const load = useCallback((p) => {
    setLoading(true);
    api.getNotifications({ category: category || undefined, unreadOnly, page: p, size: 20 })
      .then((res) => {
        setNotifications((prev) => (p === 0 ? res.content : [...prev, ...res.content]));
        setHasMore(!res.last);
        setPage(p);
      })
      .catch((err) => {
        if (import.meta.env.DEV) {
          let samples = DEV_SAMPLE_NOTIFICATIONS;
          if (category) samples = samples.filter((s) => s.category === category);
          if (unreadOnly) samples = samples.filter((s) => !s.isRead);
          setNotifications(samples);
          setHasMore(false);
          setPage(0);
        } else {
          setErrorMessage(err.message);
        }
      })
      .finally(() => setLoading(false));
  }, [category, unreadOnly]);

  useEffect(() => { load(0); }, [load]);

  useEffect(() => {
    const handleSync = () => load(0);
    window.addEventListener('portal-notification-change', handleSync);
    return () => window.removeEventListener('portal-notification-change', handleSync);
  }, [load]);

  const markRead = (id) => {
    api.markNotificationRead(id).then(() => {
      setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
      sync();
    }).catch((err) => {
      if (import.meta.env.DEV) {
        setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
      } else {
        setErrorMessage(err.message);
      }
    });
  };

  const markUnread = (id) => {
    api.markNotificationUnread(id).then(() => {
      setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: false } : n)));
      sync();
    }).catch((err) => {
      if (import.meta.env.DEV) {
        setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: false } : n)));
      } else {
        setErrorMessage(err.message);
      }
    });
  };

  const remove = (id) => {
    api.deleteNotification(id).then(() => {
      setNotifications((prev) => prev.filter((n) => n.id !== id));
      sync();
    }).catch((err) => {
      if (import.meta.env.DEV) {
        setNotifications((prev) => prev.filter((n) => n.id !== id));
      } else {
        setErrorMessage(err.message);
      }
    });
  };

  const markAllRead = () => {
    api.markAllNotificationsRead().then(() => {
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      sync();
    }).catch((err) => {
      if (import.meta.env.DEV) {
        setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      } else {
        setErrorMessage(err.message);
      }
    });
  };

  const clearRead = () => {
    api.clearReadNotifications().then(() => {
      setNotifications((prev) => prev.filter((n) => !n.isRead));
      sync();
    }).catch((err) => {
      if (import.meta.env.DEV) {
        setNotifications((prev) => prev.filter((n) => !n.isRead));
      } else {
        setErrorMessage(err.message);
      }
    });
  };

  const openItem = (n) => {
    if (!n.isRead) markRead(n.id);
    if (n.actionUrl) navigate(n.actionUrl);
  };

  const handleSelectFilter = (catId) => {
    setCategory((prev) => (prev === catId ? '' : catId));
    setUnreadOnly(false);
  };

  const handleSelectUnread = () => {
    setCategory('');
    setUnreadOnly(true);
  };

  const handleSelectAll = () => {
    setCategory('');
    setUnreadOnly(false);
  };

  const unreadCount = notifications.filter((n) => !n.isRead).length;
  const readCount = notifications.filter((n) => n.isRead).length;

  return (
    <div className="container-wide">
      <div className="notification-page-wrapper">
        <Alert error={errorMessage} />

        <div className="notification-page-head">
          <div className="notification-page-title-group">
            <h1>Notifications</h1>
            {unreadCount > 0 ? (
              <span className="notification-badge-pill">{unreadCount} unread</span>
            ) : (
              <span className="notification-badge-pill caught-up">All caught up</span>
            )}
          </div>

          <div className="notification-page-actions">
            {isAdmin && (
              <button type="button" className="btn btn-sm" onClick={() => setAnnounceOpen(true)} title="Broadcast announcement">
                <Icon name="megaphone" size={15} /> Broadcast
              </button>
            )}
            {unreadCount > 0 && (
              <button type="button" className="btn btn-sm" onClick={markAllRead} title="Mark all notifications as read">
                <Icon name="check-check" size={15} /> Mark all read
              </button>
            )}
            {readCount > 0 && (
              <button type="button" className="btn btn-sm" onClick={clearRead} title="Clear read notifications">
                <Icon name="trash-2" size={14} /> Clear read
              </button>
            )}
          </div>
        </div>

        {/* Responsive Filter Bar */}
        <div className="notification-filter-bar" role="tablist" aria-label="Notification filters">
          <button
            type="button"
            className={`notification-filter-chip ${!category && !unreadOnly ? 'active' : ''}`}
            onClick={handleSelectAll}
            title="Show all notifications"
          >
            All
          </button>
          <button
            type="button"
            className={`notification-filter-chip chip-unread ${unreadOnly ? 'active' : ''}`}
            onClick={handleSelectUnread}
            title="Show unread notifications only"
          >
            <Icon name="bell" size={14} />
            Unread
            {unreadCount > 0 && <span className="chip-count">{unreadCount}</span>}
          </button>
          {ACTIVE_CATEGORIES.map((cat) => {
            const isCatActive = category === cat.id && !unreadOnly;
            return (
              <button
                key={cat.id}
                type="button"
                className={`notification-filter-chip ${isCatActive ? 'active cat-active' : ''}`}
                onClick={() => handleSelectFilter(cat.id)}
                title={`Filter by ${cat.label}`}
              >
                <Icon name={cat.icon} size={14} />
                {cat.label}
              </button>
            );
          })}
        </div>

        {/* Empty State */}
        {!loading && notifications.length === 0 && (
          <div className="notification-empty-state">
            <div className="notification-empty-icon">
              <Icon name="bell" size={26} />
            </div>
            <h3>
              {unreadOnly
                ? "You're all caught up!"
                : category
                ? `No ${getCategoryMeta(category).label} notifications`
                : 'No notifications yet'}
            </h3>
            <p>
              {unreadOnly
                ? 'There are no unread notifications right now.'
                : category
                ? `You don't have any updates under this category.`
                : 'Important course updates, quiz results, and announcements will appear here.'}
            </p>
            {(category || unreadOnly) && (
              <button type="button" className="btn btn-sm" onClick={handleSelectAll}>
                View all notifications
              </button>
            )}
          </div>
        )}

        {/* Notification Cards */}
        {notifications.length > 0 && (
          <div className="notifications-list">
            {notifications.map((n) => {
              const meta = getCategoryMeta(n.category);
              return (
                <div
                  key={n.id}
                  className={`notification-card ${n.isRead ? 'read' : 'unread'}`}
                  onClick={() => openItem(n)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => { if (e.key === 'Enter') openItem(n); }}
                >
                  <div className={`notification-icon-wrap ${meta.cls}`}>
                    <Icon name={meta.icon} size={18} />
                  </div>

                  <div className="notification-card-main">
                    <div className="notification-meta-row">
                      <span className={`notification-category-tag ${meta.cls}`}>{meta.label}</span>
                      {n.priority && n.priority !== 'NORMAL' && n.priority !== 'LOW' && (
                        <span className={`notification-priority-tag ${n.priority.toLowerCase()}`}>
                          {n.priority}
                        </span>
                      )}
                      <span className="notification-time">{formatRelativeTime(n.createdAt)}</span>
                    </div>

                    <h4 className="notification-card-title">{n.title}</h4>
                    <p className="notification-card-body">{n.message}</p>
                    {n.actionUrl && (
                      <span className="notification-action-hint">{getActionLabel(n)} →</span>
                    )}
                  </div>

                  <div className="notification-card-actions" onClick={(e) => e.stopPropagation()}>
                    {n.isRead ? (
                      <button
                        type="button"
                        className="notification-action-btn"
                        title="Mark as unread"
                        aria-label="Mark as unread"
                        onClick={() => markUnread(n.id)}
                      >
                        <Icon name="bell" size={15} />
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="notification-action-btn"
                        title="Mark as read"
                        aria-label="Mark as read"
                        onClick={() => markRead(n.id)}
                      >
                        <Icon name="check" size={15} />
                      </button>
                    )}
                    <button
                      type="button"
                      className="notification-action-btn delete"
                      title="Dismiss notification"
                      aria-label="Dismiss notification"
                      onClick={() => remove(n.id)}
                    >
                      <Icon name="trash-2" size={15} />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Load More */}
        {hasMore && !loading && (
          <div className="notifications-load-more">
            <button type="button" className="btn" onClick={() => load(page + 1)}>
              Load more
            </button>
          </div>
        )}

        <AnnouncementModal open={announceOpen} onClose={() => setAnnounceOpen(false)} onSent={() => load(0)} />
      </div>
    </div>
  );
}
