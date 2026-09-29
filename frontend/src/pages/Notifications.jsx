import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import Alert from '../components/Alert';
import AnnouncementModal from '../components/AnnouncementModal';
import { api } from '../api';
import { formatRelativeTime, getCategoryMeta, getActionLabel } from '../lib/notificationUtils';

const ADMIN_CATEGORIES = ['CONTENT', 'SECURITY', 'SYSTEM', 'ANNOUNCEMENT', 'COMMUNITY', 'COURSE', 'QUIZ', 'ACHIEVEMENT'];
const LEARNER_CATEGORIES = ['COURSE', 'QUIZ', 'COMMUNITY', 'ANNOUNCEMENT', 'ACHIEVEMENT', 'SYSTEM', 'SECURITY'];

const sync = () => window.dispatchEvent(new CustomEvent('portal-notification-change'));

export default function Notifications() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const isAdmin = Boolean(user?.admin);
  const categories = isAdmin ? ADMIN_CATEGORIES : LEARNER_CATEGORIES;

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
      .catch((err) => setErrorMessage(err.message))
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
    }).catch((err) => setErrorMessage(err.message));
  };

  const markUnread = (id) => {
    api.markNotificationUnread(id).then(() => {
      setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: false } : n)));
      sync();
    }).catch((err) => setErrorMessage(err.message));
  };

  const remove = (id) => {
    api.deleteNotification(id).then(() => {
      setNotifications((prev) => prev.filter((n) => n.id !== id));
      sync();
    }).catch((err) => setErrorMessage(err.message));
  };

  const markAllRead = () => {
    api.markAllNotificationsRead().then(() => {
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      sync();
    }).catch((err) => setErrorMessage(err.message));
  };

  const clearRead = () => {
    api.clearReadNotifications().then(() => {
      setNotifications((prev) => prev.filter((n) => !n.isRead));
      sync();
    }).catch((err) => setErrorMessage(err.message));
  };

  const openItem = (n) => {
    if (!n.isRead) markRead(n.id);
    if (n.actionUrl) navigate(n.actionUrl);
  };

  const unreadCount = notifications.filter((n) => !n.isRead).length;
  const readCount = notifications.filter((n) => n.isRead).length;

  return (
    <div className="container-wide">
      <Alert error={errorMessage} />

      <div className="page-head">
        <h1>Notifications</h1>
        <div className="row-actions">
          {isAdmin && (
            <button type="button" className="btn" onClick={() => setAnnounceOpen(true)}>
              <Icon name="megaphone" size={16} /> Broadcast
            </button>
          )}
          {unreadCount > 0 && (
            <button type="button" className="btn" onClick={markAllRead}>
              <Icon name="check-check" size={16} /> Mark all read
            </button>
          )}
          {readCount > 0 && (
            <button type="button" className="btn" onClick={clearRead}>
              <Icon name="trash-2" size={15} /> Clear read
            </button>
          )}
        </div>
      </div>

      <div className="qb-filters">
        <select value={category} onChange={(e) => setCategory(e.target.value)} aria-label="Filter by category">
          <option value="">All categories</option>
          {categories.map((c) => <option key={c} value={c}>{getCategoryMeta(c).label}</option>)}
        </select>
        <label className="notifications-unread-toggle">
          <input type="checkbox" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} /> Unread only
        </label>
      </div>

      {!loading && notifications.length === 0 && (
        <div className="empty-state">
          <Icon name="bell" size={28} />
          <p>{unreadOnly ? "You're all caught up." : 'No notifications yet.'}</p>
        </div>
      )}

      {notifications.length > 0 && (
        <div className="notifications-list">
          {notifications.map((n) => {
            const meta = getCategoryMeta(n.category);
            return (
              <div key={n.id} className={`notification-card ${n.isRead ? 'read' : 'unread'}`} onClick={() => openItem(n)}>
                <div className={`notification-icon-wrap ${meta.cls}`}><Icon name={meta.icon} size={18} /></div>
                <div className="notification-card-main">
                  <div className="notification-meta-row">
                    <span className={`notification-category-tag ${meta.cls}`}>{meta.label}</span>
                    {n.priority !== 'NORMAL' && (
                      <span className={`notification-priority-tag ${n.priority.toLowerCase()}`}>{n.priority}</span>
                    )}
                    <span className="notification-time">{formatRelativeTime(n.createdAt)}</span>
                  </div>
                  <h4 className="notification-card-title">{n.title}</h4>
                  <p className="notification-card-body">{n.message}</p>
                  {n.actionUrl && <span className="notification-action-hint">{getActionLabel(n)} →</span>}
                </div>
                <div className="notification-item-actions" onClick={(e) => e.stopPropagation()}>
                  {n.isRead ? (
                    <button type="button" className="notification-mini-btn" title="Mark as unread" onClick={() => markUnread(n.id)}>
                      <Icon name="bell" size={13} />
                    </button>
                  ) : (
                    <button type="button" className="notification-mini-btn" title="Mark as read" onClick={() => markRead(n.id)}>
                      <Icon name="check" size={13} />
                    </button>
                  )}
                  <button type="button" className="notification-mini-btn delete" title="Dismiss" onClick={() => remove(n.id)}>
                    <Icon name="trash-2" size={13} />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {hasMore && !loading && (
        <div className="notifications-load-more">
          <button type="button" className="btn" onClick={() => load(page + 1)}>Load more</button>
        </div>
      )}

      <AnnouncementModal open={announceOpen} onClose={() => setAnnounceOpen(false)} onSent={() => load(0)} />
    </div>
  );
}
