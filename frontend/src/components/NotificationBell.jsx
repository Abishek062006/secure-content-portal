import { useState, useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import Icon from './Icon';
import NotificationDropdown from './NotificationDropdown';
import { api } from '../api';

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
    const spaceOnRight = window.innerWidth - rect.right;
    const spaceOnLeft = rect.left;

    // If closer to the left edge than 360px and there's more room on the right, align left (expand rightwards)
    if (spaceOnLeft < 360 && spaceOnRight >= 300) {
      setPlacement('left');
    } else if (spaceOnRight < 360 && spaceOnLeft >= 300) {
      setPlacement('right');
    } else {
      setPlacement(rect.left < window.innerWidth / 2 ? 'left' : 'right');
    }
  };

  const fetchUnreadCount = async () => {
    try {
      const res = await api.getUnreadNotificationCount();
      if (res && typeof res.unreadCount === 'number') {
        setUnreadCount(res.unreadCount);
      }
    } catch {
      // Non-blocking fail
    }
  };

  const fetchRecent = async () => {
    setLoading(true);
    try {
      const data = await api.getRecentNotifications();
      if (Array.isArray(data)) {
        setRecent(data);
      }
    } catch {
      // Non-blocking
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUnreadCount();
    const interval = setInterval(fetchUnreadCount, 30000);

    const handleSync = () => {
      fetchUnreadCount();
      if (isOpen) {
        fetchRecent();
      }
    };
    window.addEventListener('portal-notification-change', handleSync);

    return () => {
      clearInterval(interval);
      window.removeEventListener('portal-notification-change', handleSync);
    };
  }, [isOpen]);

  // Close dropdown on route change
  useEffect(() => {
    setIsOpen(false);
    fetchUnreadCount();
  }, [location.pathname]);

  // Click outside listener and viewport resize listener
  useEffect(() => {
    function handleClickOutside(event) {
      if (bellRef.current && !bellRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    }

    if (isOpen) {
      updatePlacement();
      const handleResizeOrScroll = () => updatePlacement();
      window.addEventListener('resize', handleResizeOrScroll);
      window.addEventListener('scroll', handleResizeOrScroll, true);
      document.addEventListener('mousedown', handleClickOutside);

      return () => {
        window.removeEventListener('resize', handleResizeOrScroll);
        window.removeEventListener('scroll', handleResizeOrScroll, true);
        document.removeEventListener('mousedown', handleClickOutside);
      };
    }
  }, [isOpen]);

  const handleToggle = () => {
    const nextState = !isOpen;
    if (nextState) {
      updatePlacement();
      fetchRecent();
      fetchUnreadCount();
    }
    setIsOpen(nextState);
  };

  const handleMarkRead = async (id) => {
    try {
      await api.markNotificationRead(id);
      setRecent((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n))
      );
      setUnreadCount((c) => Math.max(0, c - 1));
      window.dispatchEvent(new CustomEvent('portal-notification-change'));
    } catch {
      // Ignore
    }
  };

  const handleMarkUnread = async (id) => {
    try {
      await api.markNotificationUnread(id);
      setRecent((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: false } : n))
      );
      setUnreadCount((c) => c + 1);
      window.dispatchEvent(new CustomEvent('portal-notification-change'));
    } catch {
      // Ignore
    }
  };

  const handleDelete = async (id) => {
    try {
      const item = recent.find((n) => n.id === id);
      await api.deleteNotification(id);
      setRecent((prev) => prev.filter((n) => n.id !== id));
      if (item && !item.isRead) {
        setUnreadCount((c) => Math.max(0, c - 1));
      }
      window.dispatchEvent(new CustomEvent('portal-notification-change'));
    } catch {
      // Ignore
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await api.markAllNotificationsRead();
      setRecent((prev) => prev.map((n) => ({ ...n, isRead: true })));
      setUnreadCount(0);
      window.dispatchEvent(new CustomEvent('portal-notification-change'));
    } catch {
      // Ignore
    }
  };

  return (
    <div className="notification-bell-container" ref={bellRef}>
      <button
        type="button"
        className={`notification-bell-btn ${isOpen ? 'active' : ''}`}
        onClick={handleToggle}
        title={unreadCount > 0 ? `${unreadCount} unread notification${unreadCount === 1 ? '' : 's'}` : 'Notifications'}
        aria-label="Notifications"
        aria-expanded={isOpen}
      >
        <Icon name={unreadCount > 0 ? 'bell-ring' : 'bell'} size={19} />
        {unreadCount > 0 && (
          <span className="notification-badge-count">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <>
          <div
            className="notification-dropdown-backdrop"
            onClick={() => setIsOpen(false)}
            aria-hidden="true"
          />
          <NotificationDropdown
            notifications={recent}
            unreadCount={unreadCount}
            onMarkRead={handleMarkRead}
            onMarkUnread={handleMarkUnread}
            onDelete={handleDelete}
            onMarkAllRead={handleMarkAllRead}
            onClose={() => setIsOpen(false)}
            loading={loading}
            placement={placement}
          />
        </>
      )}
    </div>
  );
}
