import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import { api } from '../api';
import { formatRelativeTime, getCategoryMeta, getActionLabel } from '../lib/notificationUtils';

const ADMIN_FILTERS = [
  { id: 'ALL', label: 'All' },
  { id: 'UNREAD', label: 'Unread' },
  { id: 'SYSTEM', label: 'System & Users' },
  { id: 'CONTENT', label: 'Content Issues' },
  { id: 'MODERATION', label: 'Moderation' },
  { id: 'SECURITY', label: 'Security' },
];

const LEARNER_FILTERS = [
  { id: 'ALL', label: 'All' },
  { id: 'UNREAD', label: 'Unread' },
  { id: 'COURSE', label: 'Courses' },
  { id: 'QUIZ', label: 'Quiz' },
  { id: 'COMMUNITY', label: 'Community' },
  { id: 'ANNOUNCEMENT', label: 'Announcements' },
];

export default function Notifications() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const isAdmin = Boolean(user?.admin || user?.role === 'ADMIN');
  const filters = isAdmin ? ADMIN_FILTERS : LEARNER_FILTERS;

  const [activeFilter, setActiveFilter] = useState('ALL');
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [totalElements, setTotalElements] = useState(0);

  // Announcement Modal for Admins
  const [announcementOpen, setAnnouncementOpen] = useState(false);
  const [announcementTitle, setAnnouncementTitle] = useState('');
  const [announcementMessage, setAnnouncementMessage] = useState('');
  const [announcementPriority, setAnnouncementPriority] = useState('NORMAL');
  const [announcementActionUrl, setAnnouncementActionUrl] = useState('');
  const [announcementSubmitting, setAnnouncementSubmitting] = useState(false);
  const [announcementSuccess, setAnnouncementSuccess] = useState('');
  const [announcementError, setAnnouncementError] = useState('');

  const loadNotifications = useCallback(async (filterId, pageNum, append = false) => {
    setLoading(true);
    try {
      const params = { page: pageNum, size: 15 };
      if (filterId === 'UNREAD') {
        params.unreadOnly = true;
      } else if (filterId !== 'ALL') {
        params.category = filterId;
      }

      const res = await api.getNotifications(params);
      if (res && res.content) {
        setNotifications((prev) => (append ? [...prev, ...res.content] : res.content));
        setHasMore(!res.last);
        setTotalElements(res.totalElements || 0);
      }
    } catch {
      // Non-blocking
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    setPage(0);
    loadNotifications(activeFilter, 0, false);
  }, [activeFilter, loadNotifications]);

  const handleLoadMore = () => {
    const nextPage = page + 1;
    setPage(nextPage);
    loadNotifications(activeFilter, nextPage, true);
  };

  const handleMarkRead = async (id, e) => {
    if (e) e.stopPropagation();
    try {
      await api.markNotificationRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n))
      );
    } catch {
      // Ignore
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await api.markAllNotificationsRead();
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
    } catch {
      // Ignore
    }
  };

  const handleItemClick = (n) => {
    if (!n.isRead) {
      handleMarkRead(n.id);
    }
    if (n.actionUrl) {
      navigate(n.actionUrl);
    }
  };

  const handleSendAnnouncement = async (e) => {
    e.preventDefault();
    if (!announcementTitle.trim() || !announcementMessage.trim()) return;

    setAnnouncementSubmitting(true);
    setAnnouncementSuccess('');
    setAnnouncementError('');
    try {
      await api.sendAdminAnnouncement({
        title: announcementTitle.trim(),
        message: announcementMessage.trim(),
        priority: announcementPriority,
        actionUrl: announcementActionUrl.trim() || null,
      });
      setAnnouncementSuccess('Announcement successfully broadcast to all users!');
      setAnnouncementTitle('');
      setAnnouncementMessage('');
      setAnnouncementActionUrl('');
      // Reload notifications to display the new announcement
      loadNotifications(activeFilter, 0, false);
      setTimeout(() => {
        setAnnouncementOpen(false);
        setAnnouncementSuccess('');
      }, 1500);
    } catch (err) {
      setAnnouncementError(err.message || 'Failed to send announcement. Please verify you are logged in as Admin.');
    } finally {
      setAnnouncementSubmitting(false);
    }
  };

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <div className="container-wide notifications-page">
      <div className="page-head notifications-head">
        <div>
          <h1 className="page-title">Notifications</h1>
          <p className="page-subtitle" style={{ margin: '6px 0 0', color: 'var(--ink-mid)', fontSize: '1rem' }}>
            {isAdmin
              ? 'Monitor system events, user registrations, content issues, and administrative alerts.'
              : 'Stay updated with your courses, quizzes, community activity, and announcements.'}
          </p>
        </div>

        <div className="row-actions notifications-actions">
          {isAdmin && (
            <button
              type="button"
              id="btn-broadcast-announcement"
              className="btn btn-secondary"
              onClick={() => {
                setAnnouncementError('');
                setAnnouncementSuccess('');
                setAnnouncementOpen(true);
              }}
            >
              <Icon name="megaphone" size={16} />
              Broadcast Announcement
            </button>
          )}

          {unreadCount > 0 && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleMarkAllRead}
            >
              <Icon name="check-check" size={16} />
              Mark all as read
            </button>
          )}
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="notifications-filter-bar">
        {filters.map((f) => (
          <button
            key={f.id}
            type="button"
            className={`notifications-filter-chip ${activeFilter === f.id ? 'active' : ''}`}
            onClick={() => setActiveFilter(f.id)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* Notification List */}
      <div className="notifications-list-container">
        {loading && notifications.length === 0 ? (
          <div className="notifications-loading">
            <div className="spinner" />
            <p>Loading your notifications...</p>
          </div>
        ) : notifications.length === 0 ? (
          <div className="notifications-empty-state">
            <div className="notifications-empty-icon">
              <Icon name="bell" size={36} />
            </div>
            <h3>No notifications yet</h3>
            <p>
              {activeFilter === 'ALL'
                ? isAdmin
                  ? "You're all caught up! System events, user registrations, and administrative alerts will appear here."
                  : "You're all caught up! Updates about your courses and community will appear here."
                : `No notifications found under "${filters.find((f) => f.id === activeFilter)?.label}".`}
            </p>
          </div>
        ) : (
          <div className="notifications-grid">
            {notifications.map((n) => {
              const meta = getCategoryMeta(n.category);
              return (
                <div
                  key={n.id}
                  className={`notification-card ${n.isRead ? 'read' : 'unread'}`}
                  onClick={() => handleItemClick(n)}
                >
                  <div
                    className="notification-card-icon"
                    style={{ backgroundColor: meta.bg, color: meta.color }}
                  >
                    <Icon name={meta.icon} size={20} />
                  </div>

                  <div className="notification-card-main">
                    <div className="notification-card-meta">
                      <span
                        className="notification-category-pill"
                        style={{ color: meta.color, borderColor: meta.border }}
                      >
                        {meta.label}
                      </span>

                      {n.priority && n.priority !== 'NORMAL' && (
                        <span className={`notification-priority-tag ${n.priority.toLowerCase()}`}>
                          {n.priority}
                        </span>
                      )}

                      <span className="notification-card-time">
                        {formatRelativeTime(n.createdAt)}
                      </span>

                      {!n.isRead && <span className="notification-card-dot" />}
                    </div>

                    <h4 className="notification-card-title">{n.title}</h4>
                    <p className="notification-card-message">{n.message}</p>

                    <div className="notification-card-footer">
                      {n.actionUrl && (
                        <button
                          type="button"
                          className="btn btn-sm notification-action-btn"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleItemClick(n);
                          }}
                        >
                          <span>{getActionLabel(n.category, n.actionUrl)}</span>
                          <Icon name="arrow-right" size={14} />
                        </button>
                      )}

                      {!n.isRead && (
                        <button
                          type="button"
                          className="notification-mark-read-btn"
                          title="Mark as read"
                          onClick={(e) => handleMarkRead(n.id, e)}
                        >
                          <Icon name="check" size={14} />
                          <span>Mark read</span>
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Pagination Load More */}
        {hasMore && !loading && (
          <div className="notifications-load-more">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={handleLoadMore}
            >
              Load more notifications ({notifications.length} of {totalElements})
            </button>
          </div>
        )}
      </div>

      {/* Broadcast Announcement Modal for Admins */}
      {announcementOpen && (
        <div
          className="announcement-modal-overlay"
          onClick={() => setAnnouncementOpen(false)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.55)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '16px',
          }}
        >
          <div
            className="announcement-modal-card"
            onClick={(e) => e.stopPropagation()}
            style={{
              background: 'var(--surface, #ffffff)',
              border: '1px solid var(--line, rgba(0,0,0,0.1))',
              borderRadius: '16px',
              padding: '28px',
              width: '100%',
              maxWidth: '540px',
              boxShadow: '0 20px 48px rgba(0, 0, 0, 0.25)',
              color: 'var(--ink, #1d1d1f)',
              animation: 'fadeInUp 0.2s var(--ease) both',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'var(--accent-wash, #e6f5f2)', color: 'var(--accent-deep, #06584c)', display: 'grid', placeItems: 'center' }}>
                  <Icon name="megaphone" size={18} />
                </div>
                <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>Broadcast Announcement</h2>
              </div>
              <button
                type="button"
                onClick={() => setAnnouncementOpen(false)}
                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 6, borderRadius: '50%', color: 'var(--ink-mid)' }}
                aria-label="Close"
              >
                <Icon name="x" size={20} />
              </button>
            </div>

            <p style={{ margin: '0 0 16px', color: 'var(--ink-mid)', fontSize: '0.9rem' }}>
              Broadcast an announcement notification to all registered learners across the platform.
            </p>

            {announcementSuccess && (
              <div className="alert alert-success" style={{ marginBottom: 16 }}>
                {announcementSuccess}
              </div>
            )}

            {announcementError && (
              <div className="alert alert-error" style={{ marginBottom: 16 }}>
                {announcementError}
              </div>
            )}

            <form onSubmit={handleSendAnnouncement}>
              <div className="form-group" style={{ marginBottom: 16 }}>
                <label htmlFor="ann-title" style={{ display: 'block', fontWeight: 600, fontSize: '0.88rem', marginBottom: 6 }}>
                  Announcement Title *
                </label>
                <input
                  id="ann-title"
                  type="text"
                  className="input"
                  style={{ width: '100%' }}
                  placeholder="e.g. New Java Course & Quizzes Available"
                  value={announcementTitle}
                  onChange={(e) => setAnnouncementTitle(e.target.value)}
                  maxLength={200}
                  required
                />
              </div>

              <div className="form-group" style={{ marginBottom: 16 }}>
                <label htmlFor="ann-msg" style={{ display: 'block', fontWeight: 600, fontSize: '0.88rem', marginBottom: 6 }}>
                  Announcement Message *
                </label>
                <textarea
                  id="ann-msg"
                  className="input textarea"
                  style={{ width: '100%', minHeight: '90px' }}
                  rows={3}
                  placeholder="Describe what learners need to know..."
                  value={announcementMessage}
                  onChange={(e) => setAnnouncementMessage(e.target.value)}
                  required
                />
              </div>

              <div className="form-group" style={{ marginBottom: 16 }}>
                <label htmlFor="ann-priority" style={{ display: 'block', fontWeight: 600, fontSize: '0.88rem', marginBottom: 6 }}>
                  Priority
                </label>
                <select
                  id="ann-priority"
                  className="input"
                  style={{ width: '100%' }}
                  value={announcementPriority}
                  onChange={(e) => setAnnouncementPriority(e.target.value)}
                >
                  <option value="NORMAL">Normal</option>
                  <option value="IMPORTANT">Important</option>
                  <option value="CRITICAL">Critical</option>
                </select>
              </div>

              <div className="form-group" style={{ marginBottom: 20 }}>
                <label htmlFor="ann-action" style={{ display: 'block', fontWeight: 600, fontSize: '0.88rem', marginBottom: 6 }}>
                  Target Action URL (optional)
                </label>
                <input
                  id="ann-action"
                  type="text"
                  className="input"
                  style={{ width: '100%' }}
                  placeholder="e.g. /courses or /feed"
                  value={announcementActionUrl}
                  onChange={(e) => setAnnouncementActionUrl(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end', marginTop: 24 }}>
                <button
                  type="button"
                  className="btn"
                  onClick={() => setAnnouncementOpen(false)}
                  disabled={announcementSubmitting}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={announcementSubmitting || !announcementTitle.trim() || !announcementMessage.trim()}
                >
                  {announcementSubmitting ? 'Sending...' : 'Broadcast to All Users'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
