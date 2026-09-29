import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import Icon from './Icon';
import NotificationDropdown from './NotificationDropdown';
import { api } from '../api';

/** Polls the unread count, and re-syncs on a 'portal-notification-change' event fired by anything
 *  (this bell, the dropdown, or the full Notifications page) that changes read/unread state. */
export default function NotificationBell() {
  const [unreadCount, setUnreadCount] = useState(0);
  const [recent, setRecent] = useState([]);
  const [isOpen, setIsOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [placement, setPlacement] = useState('right');
  const bellRef = useRef(null);
  const location = useLocation();

  const updatePlacement = () => {
    if (!bellRef.current) return;
    const rect = bellRef.current.getBoundingClientRect();
    setPlacement(window.innerWidth - rect.right < 360 && rect.left >= 300 ? 'left' : 'right');
  };

  const fetchUnreadCount = () => {
    api.getUnreadNotificationCount().then((res) => setUnreadCount(res.unreadCount)).catch(() => {});
  };

  const fetchRecent = () => {
    setLoading(true);
    api.getRecentNotifications()
      .then(setRecent)
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchUnreadCount();
    const interval = setInterval(fetchUnreadCount, 30000);
    const handleSync = () => {
      fetchUnreadCount();
      if (isOpen) fetchRecent();
    };
    window.addEventListener('portal-notification-change', handleSync);
    return () => {
      clearInterval(interval);
      window.removeEventListener('portal-notification-change', handleSync);
    };
  }, [isOpen]);

  useEffect(() => {
    setIsOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!isOpen) return;
    updatePlacement();
    const handleOutside = (e) => {
      if (bellRef.current && !bellRef.current.contains(e.target)) setIsOpen(false);
    };
    window.addEventListener('resize', updatePlacement);
    document.addEventListener('mousedown', handleOutside);
    return () => {
      window.removeEventListener('resize', updatePlacement);
      document.removeEventListener('mousedown', handleOutside);
    };
  }, [isOpen]);

  const handleToggle = () => {
    if (!isOpen) {
      updatePlacement();
      fetchRecent();
    }
    setIsOpen((v) => !v);
  };

  const sync = () => window.dispatchEvent(new CustomEvent('portal-notification-change'));

  const handleMarkRead = (id) => {
    api.markNotificationRead(id).then(() => {
      setRecent((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
      setUnreadCount((c) => Math.max(0, c - 1));
      sync();
    }).catch(() => {});
  };

  const handleDelete = (id) => {
    const item = recent.find((n) => n.id === id);
    api.deleteNotification(id).then(() => {
      setRecent((prev) => prev.filter((n) => n.id !== id));
      if (item && !item.isRead) setUnreadCount((c) => Math.max(0, c - 1));
      sync();
    }).catch(() => {});
  };

  const handleMarkAllRead = () => {
    api.markAllNotificationsRead().then(() => {
      setRecent((prev) => prev.map((n) => ({ ...n, isRead: true })));
      setUnreadCount(0);
      sync();
    }).catch(() => {});
  };

  return (
    <div className="notification-bell-container" ref={bellRef}>
      <button type="button" className={`notification-bell-btn${isOpen ? ' active' : ''}`} onClick={handleToggle}
              title={unreadCount > 0 ? `${unreadCount} unread notification${unreadCount === 1 ? '' : 's'}` : 'Notifications'}
              aria-label="Notifications" aria-expanded={isOpen}>
        <Icon name={unreadCount > 0 ? 'bell-ring' : 'bell'} size={19} />
        {unreadCount > 0 && <span className="notification-badge-count">{unreadCount > 9 ? '9+' : unreadCount}</span>}
      </button>

      {isOpen && (
        <>
          <div className="notification-dropdown-backdrop" onClick={() => setIsOpen(false)} aria-hidden="true" />
          <NotificationDropdown notifications={recent} unreadCount={unreadCount} onMarkRead={handleMarkRead}
                                onDelete={handleDelete} onMarkAllRead={handleMarkAllRead} onClose={() => setIsOpen(false)}
                                loading={loading} placement={placement} />
        </>
      )}
    </div>
  );
}
