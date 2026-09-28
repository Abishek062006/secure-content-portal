import { Link, useNavigate } from 'react-router-dom';
import Icon from './Icon';
import { formatRelativeTime, getCategoryMeta, getActionLabel } from '../lib/notificationUtils';

export default function NotificationDropdown({
  notifications = [],
  unreadCount = 0,
  onMarkRead,
  onMarkUnread,
  onDelete,
  onMarkAllRead,
  onClose,
  loading = false,
  placement = 'right',
}) {
  const navigate = useNavigate();

  const handleItemClick = (n) => {
    if (!n.isRead && onMarkRead) {
      onMarkRead(n.id);
    }
    if (onClose) onClose();
    if (n.actionUrl) {
      navigate(n.actionUrl);
    }
  };

  return (
    <div
      className={`notification-dropdown placement-${placement}`}
      role="dialog"
      aria-label="Notifications"
    >
      <div className="notification-dropdown-header">
        <div className="notification-dropdown-title">
          <span>Notifications</span>
          {unreadCount > 0 && (
            <span className="notification-pill-badge">{unreadCount} new</span>
          )}
        </div>
        <div className="notification-dropdown-header-actions">
          {unreadCount > 0 && (
            <button
              type="button"
              className="notification-mark-all-btn"
              onClick={(e) => {
                e.stopPropagation();
                onMarkAllRead();
              }}
              title="Mark all as read"
            >
              <Icon name="check-check" size={14} />
              <span className="btn-label">Mark all read</span>
            </button>
          )}
          <button
            type="button"
            className="notification-dropdown-close-btn"
            onClick={onClose}
            title="Close notifications"
            aria-label="Close notifications"
          >
            <Icon name="x" size={16} />
          </button>
        </div>
      </div>

      <div className="notification-dropdown-list">
        {loading ? (
          <div className="notification-empty">Loading notifications...</div>
        ) : notifications.length === 0 ? (
          <div className="notification-empty">
            <Icon name="bell" size={24} style={{ opacity: 0.3, marginBottom: 6 }} />
            <div>No notifications yet.</div>
          </div>
        ) : (
          notifications.map((n) => {
            const meta = getCategoryMeta(n.category);
            return (
              <div
                key={n.id}
                className={`notification-item ${n.isRead ? 'read' : 'unread'}`}
                onClick={() => handleItemClick(n)}
              >
                <div
                  className="notification-icon-wrap"
                  style={{ backgroundColor: meta.bg, color: meta.color }}
                >
                  <Icon name={meta.icon} size={15} />
                </div>
                <div className="notification-content">
                  <div className="notification-top-row">
                    <span className="notification-title">{n.title}</span>
                    <span className="notification-time">{formatRelativeTime(n.createdAt)}</span>
                  </div>
                  <div className="notification-message">{n.message}</div>
                  <div className="notification-meta-row">
                    <span className="notification-category-tag" style={{ color: meta.color }}>
                      {meta.label}
                    </span>
                    {n.priority && n.priority !== 'NORMAL' && (
                      <span className={`notification-priority-tag ${n.priority.toLowerCase()}`}>
                        {n.priority}
                      </span>
                    )}
                    {n.actionUrl && (
                      <span className="notification-action-hint">
                        {getActionLabel(n)} →
                      </span>
                    )}
                  </div>
                </div>
                <div className="notification-item-actions" onClick={(e) => e.stopPropagation()}>
                  {n.isRead ? (
                    onMarkUnread && (
                      <button
                        type="button"
                        className="notification-mini-btn"
                        onClick={() => onMarkUnread(n.id)}
                        title="Mark as unread"
                        aria-label="Mark as unread"
                      >
                        <span className="notification-unread-marker" />
                      </button>
                    )
                  ) : (
                    onMarkRead && (
                      <button
                        type="button"
                        className="notification-mini-btn"
                        onClick={() => onMarkRead(n.id)}
                        title="Mark as read"
                        aria-label="Mark as read"
                      >
                        <Icon name="check" size={13} />
                      </button>
                    )
                  )}
                  {onDelete && (
                    <button
                      type="button"
                      className="notification-mini-btn delete"
                      onClick={() => onDelete(n.id)}
                      title="Dismiss notification"
                      aria-label="Dismiss notification"
                    >
                      <Icon name="trash-2" size={13} />
                    </button>
                  )}
                </div>
                {!n.isRead && <span className="notification-unread-dot" />}
              </div>
            );
          })
        )}
      </div>

      <div className="notification-dropdown-footer">
        <Link
          to="/notifications"
          onClick={onClose}
          className="notification-view-all"
        >
          View all notifications →
        </Link>
      </div>
    </div>
  );
}
