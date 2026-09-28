import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import { api } from '../api';
import { formatRelativeTime, getCategoryMeta, getActionLabel } from '../lib/notificationUtils';

const ADMIN_FILTERS = [
  { id: 'ALL', label: 'All' },
  { id: 'UNREAD', label: 'Unread' },
  { id: 'SYSTEM', label: 'System & Users' },
  { id: 'ANNOUNCEMENT', label: 'Announcements' },
  { id: 'CONTENT', label: 'Content Issues' },
  { id: 'MODERATION', label: 'Moderation' },
  { id: 'SECURITY', label: 'Security' },
  { id: 'COMMUNITY', label: 'Community' },
];

const LEARNER_FILTERS = [
  { id: 'ALL', label: 'All' },
  { id: 'UNREAD', label: 'Unread' },
  { id: 'COURSE', label: 'Courses' },
  { id: 'QUIZ', label: 'Quizzes' },
  { id: 'COMMUNITY', label: 'Community' },
  { id: 'ANNOUNCEMENT', label: 'Announcements' },
  { id: 'ACHIEVEMENT', label: 'Achievements' },
];

export default function Notifications() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const isAdmin = Boolean(user?.admin || user?.role === 'ADMIN');
  const filters = isAdmin ? ADMIN_FILTERS : LEARNER_FILTERS;

  const [activeFilter, setActiveFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [actionFeedback, setActionFeedback] = useState('');

  // Announcement Modal for Admins
  const [announcementOpen, setAnnouncementOpen] = useState(false);
  const [announcementTitle, setAnnouncementTitle] = useState('');
  const [announcementMessage, setAnnouncementMessage] = useState('');
  const [announcementPriority, setAnnouncementPriority] = useState('IMPORTANT');
  const [announcementActionUrl, setAnnouncementActionUrl] = useState('');
  const [announcementTarget, setAnnouncementTarget] = useState('ALL');
  const [announcementSubmitting, setAnnouncementSubmitting] = useState(false);
  const [announcementSuccess, setAnnouncementSuccess] = useState('');
  const [announcementError, setAnnouncementError] = useState('');

  const showFeedback = (msg) => {
    setActionFeedback(msg);
    setTimeout(() => setActionFeedback(''), 2500);
  };

  const loadNotifications = useCallback(async (filterId, pageNum, append = false) => {
    setLoading(true);
    try {
      const params = { page: pageNum, size: 20 };
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

  // Sync across tabs/components
  useEffect(() => {
    const handleSync = () => {
      loadNotifications(activeFilter, 0, false);
    };
    window.addEventListener('portal-notification-change', handleSync);
    return () => window.removeEventListener('portal-notification-change', handleSync);
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
      window.dispatchEvent(new CustomEvent('portal-notification-change'));
      showFeedback('Marked as read');
    } catch {
      // Ignore
    }
  };

  const handleMarkUnread = async (id, e) => {
    if (e) e.stopPropagation();
    try {
      await api.markNotificationUnread(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: false } : n))
      );
      window.dispatchEvent(new CustomEvent('portal-notification-change'));
      showFeedback('Marked as unread');
    } catch {
      // Ignore
    }
  };

  const handleDelete = async (id, e) => {
    if (e) e.stopPropagation();
    try {
      await api.deleteNotification(id);
      setNotifications((prev) => prev.filter((n) => n.id !== id));
      setTotalElements((t) => Math.max(0, t - 1));
      window.dispatchEvent(new CustomEvent('portal-notification-change'));
      showFeedback('Notification dismissed');
    } catch {
      // Ignore
    }
  };

  const handleClearRead = async () => {
    if (!window.confirm('Are you sure you want to clear all read notifications?')) return;
    try {
      const res = await api.clearReadNotifications();
      setNotifications((prev) => prev.filter((n) => !n.isRead));
      window.dispatchEvent(new CustomEvent('portal-notification-change'));
      showFeedback(`Cleared ${res?.cleared ?? 'all'} read notifications`);
    } catch {
      // Ignore
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await api.markAllNotificationsRead();
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      window.dispatchEvent(new CustomEvent('portal-notification-change'));
      showFeedback('All notifications marked as read');
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
        targetAudience: announcementTarget,
      });
      setAnnouncementSuccess('Announcement successfully broadcast!');
      setAnnouncementTitle('');
      setAnnouncementMessage('');
      setAnnouncementActionUrl('');
      // Reload notifications & sync bell
      loadNotifications(activeFilter, 0, false);
      window.dispatchEvent(new CustomEvent('portal-notification-change'));
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

  // Filtered by search query if user typed anything
  const displayedNotifications = useMemo(() => {
    if (!searchQuery.trim()) return notifications;
    const q = searchQuery.toLowerCase().trim();
    return notifications.filter(
      (n) =>
        (n.title && n.title.toLowerCase().includes(q)) ||
        (n.message && n.message.toLowerCase().includes(q)) ||
        (n.category && n.category.toLowerCase().includes(q))
    );
  }, [notifications, searchQuery]);

  const unreadCount = notifications.filter((n) => !n.isRead).length;
  const readCount = notifications.filter((n) => n.isRead).length;

  return (
    <div className="container-wide notifications-page">
      <div className="page-head notifications-head">
        <div>
          <h1 className="page-title">Notifications</h1>
          <p className="page-subtitle" style={{ margin: '6px 0 0', color: 'var(--ink-mid)', fontSize: '0.96rem' }}>
            {isAdmin
              ? 'Monitor system events, user registrations, moderation, and administrative alerts.'
              : 'Stay up-to-date with your courses, quizzes, community replies, and announcements.'}
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

          {readCount > 0 && (
            <button
              type="button"
              className="btn btn-outline notifications-clear-read-btn"
              onClick={handleClearRead}
              title="Delete all read notifications"
            >
              <Icon name="trash-2" size={15} />
              Clear read ({readCount})
            </button>
          )}
        </div>
      </div>

      {actionFeedback && (
        <div className="notifications-feedback-toast" role="status">
          <Icon name="check-circle" size={15} />
          <span>{actionFeedback}</span>
        </div>
      )}

      {/* Filter Tabs and Search Bar */}
      <div className="notifications-controls-bar">
        <div className="notifications-filter-bar">
          {filters.map((f) => (
            <button
              key={f.id}
              type="button"
              className={`notifications-filter-chip ${activeFilter === f.id ? 'active' : ''}`}
              onClick={() => setActiveFilter(f.id)}
            >
              {f.label}
              {f.id === 'UNREAD' && unreadCount > 0 && (
                <span className="notifications-chip-count">{unreadCount}</span>
              )}
            </button>
          ))}
        </div>

        <div className="notifications-search-wrap">
          <Icon name="search" size={15} className="notifications-search-icon" />
          <input
            type="text"
            className="notifications-search-input"
            placeholder="Filter notifications..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          {searchQuery && (
            <button
              type="button"
              className="notifications-search-clear"
              onClick={() => setSearchQuery('')}
              title="Clear search"
            >
              <Icon name="x" size={13} />
            </button>
          )}
        </div>
      </div>

      {/* Notification List */}
      <div className="notifications-list-container">
        {loading && notifications.length === 0 ? (
          <div className="notifications-loading">
            <div className="spinner" />
            <p>Loading your notifications...</p>
          </div>
        ) : displayedNotifications.length === 0 ? (
          <div className="notifications-empty-state">
            <div className="notifications-empty-icon">
              <Icon name="bell" size={32} />
            </div>
            <h3>No notifications found</h3>
            <p>
              {searchQuery
                ? `No notifications matching "${searchQuery}".`
                : activeFilter === 'ALL'
                ? isAdmin
                  ? "You're all caught up! System events, user registrations, and platform alerts will appear here."
                  : "You're all caught up! Updates about your courses and community will appear here."
                : `No notifications found under "${filters.find((f) => f.id === activeFilter)?.label}".`}
            </p>
          </div>
        ) : (
          <div className="notifications-grid">
            {displayedNotifications.map((n) => {
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
                    <div className="notification-card-header">
                      <div className="notification-card-tags">
                        <span
                          className="notification-category-badge"
                          style={{ backgroundColor: meta.bg, color: meta.color }}
                        >
                          {meta.label}
                        </span>

                        {n.priority && n.priority !== 'NORMAL' && (
                          <span className={`notification-priority-badge ${n.priority.toLowerCase()}`}>
                            {n.priority}
                          </span>
                        )}

                        <span
                          className="notification-card-date"
                          title={n.createdAt ? new Date(n.createdAt).toLocaleString() : ''}
                        >
                          {formatRelativeTime(n.createdAt)}
                        </span>
                      </div>

                      <div className="notification-card-top-actions" onClick={(e) => e.stopPropagation()}>
                        {n.isRead ? (
                          <button
                            type="button"
                            className="notification-card-action-icon"
                            title="Mark as unread"
                            onClick={(e) => handleMarkUnread(n.id, e)}
                          >
                            <span className="notification-unread-dot-outline" />
                            <span className="action-text">Unread</span>
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="notification-card-action-icon active"
                            title="Mark as read"
                            onClick={(e) => handleMarkRead(n.id, e)}
                          >
                            <Icon name="check" size={14} />
                            <span className="action-text">Read</span>
                          </button>
                        )}

                        <button
                          type="button"
                          className="notification-card-action-icon delete"
                          title="Dismiss notification"
                          onClick={(e) => handleDelete(n.id, e)}
                        >
                          <Icon name="trash-2" size={14} />
                        </button>
                      </div>
                    </div>

                    <h4 className="notification-card-title">{n.title}</h4>
                    <p className="notification-card-body">{n.message}</p>

                    <div className="notification-card-actions">
                      {n.actionUrl && (
                        <button
                          type="button"
                          className="btn btn-sm notification-action-button"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleItemClick(n);
                          }}
                        >
                          <span>{getActionLabel(n)}</span>
                          <Icon name="arrow-right" size={14} />
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
        {hasMore && !loading && !searchQuery && (
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
        >
          <div
            className="announcement-modal-card"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="announcement-modal-title"
          >
            <div className="announcement-modal-header">
              <div className="announcement-modal-title-wrap">
                <div className="announcement-icon-badge">
                  <Icon name="megaphone" size={18} />
                </div>
                <h2 id="announcement-modal-title" className="announcement-modal-title">Broadcast Announcement</h2>
              </div>
              <button
                type="button"
                className="announcement-modal-close"
                onClick={() => setAnnouncementOpen(false)}
                aria-label="Close modal"
              >
                <Icon name="x" size={20} />
              </button>
            </div>

            <p className="announcement-modal-desc">
              Broadcast an announcement notification with optional link and priority level.
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
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                  <label htmlFor="ann-title" style={{ fontWeight: 600, fontSize: '0.88rem' }}>
                    Announcement Title *
                  </label>
                  <span style={{ fontSize: '0.75rem', color: announcementTitle.length > 180 ? '#dc2626' : 'var(--ink-soft)' }}>
                    {announcementTitle.length}/200
                  </span>
                </div>
                <input
                  id="ann-title"
                  type="text"
                  className="input"
                  style={{ width: '100%' }}
                  placeholder="e.g. New Java Masterclass & Hackathon Live"
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
                  placeholder="Provide concise details for learners..."
                  value={announcementMessage}
                  onChange={(e) => setAnnouncementMessage(e.target.value)}
                  required
                />
              </div>

              <div className="announcement-grid-cols">
                <div className="form-group">
                  <label htmlFor="ann-target" style={{ display: 'block', fontWeight: 600, fontSize: '0.88rem', marginBottom: 6 }}>
                    Target Audience
                  </label>
                  <select
                    id="ann-target"
                    className="input"
                    style={{ width: '100%' }}
                    value={announcementTarget}
                    onChange={(e) => setAnnouncementTarget(e.target.value)}
                  >
                    <option value="ALL">All Users (Learners & Admins)</option>
                    <option value="LEARNERS">Learners Only</option>
                    <option value="ADMINS">Admins Only</option>
                  </select>
                </div>

                <div className="form-group">
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
                  placeholder="e.g. /courses or /hackathons or /feed"
                  value={announcementActionUrl}
                  onChange={(e) => setAnnouncementActionUrl(e.target.value)}
                />
              </div>

              <div className="announcement-modal-actions">
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
                  {announcementSubmitting ? 'Broadcasting...' : 'Broadcast Announcement'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
