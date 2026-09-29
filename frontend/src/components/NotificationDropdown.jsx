import { Link, useNavigate } from 'react-router-dom';
import Icon from './Icon';
import { formatRelativeTime, getCategoryMeta, getActionLabel } from '../lib/notificationUtils';

export default function NotificationDropdown({ notifications = [], unreadCount = 0, onMarkRead, onDelete,
                                                onMarkAllRead, onClose, loading = false, style = {} }) {
  const navigate = useNavigate();

  const handleItemClick = (n) => {
    if (!n.isRead) onMarkRead(n.id);
    onClose();
    if (n.actionUrl) navigate(n.actionUrl);
  };

  return (
    <div className="notification-dropdown" style={style} role="dialog" aria-label="Notifications">
      <div className="notification-dropdown-header">
        <div className="notification-dropdown-title">
          <span>Notifications</span>
          {unreadCount > 0 && <span className="notification-pill-badge">{unreadCount} new</span>}
        </div>
        <div className="notification-dropdown-header-actions">
          {unreadCount > 0 && (
            <button type="button" className="notification-mark-all-btn" onClick={(e) => { e.stopPropagation(); onMarkAllRead(); }}
                    title="Mark all as read">
              <Icon name="check-check" size={14} />
            </button>
          )}
          <button type="button" className="notification-dropdown-close-btn" onClick={onClose} aria-label="Close notifications">
            <Icon name="x" size={16} />
          </button>
        </div>
      </div>

      <div className="notification-dropdown-list">
        {loading ? (
          <div className="notification-empty">Loading…</div>
        ) : notifications.length === 0 ? (
          <div className="notification-empty">
            <Icon name="bell" size={24} />
            <div>No notifications yet.</div>
          </div>
        ) : (
          notifications.map((n) => {
            const meta = getCategoryMeta(n.category);
            return (
              <div key={n.id} className={`notification-item ${n.isRead ? 'read' : 'unread'}`} onClick={() => handleItemClick(n)}>
                <div className={`notification-icon-wrap ${meta.cls}`}>
                  <Icon name={meta.icon} size={15} />
                </div>
                <div className="notification-content">
                  <div className="notification-top-row">
                    <span className="notification-title">{n.title}</span>
                    <span className="notification-time">{formatRelativeTime(n.createdAt)}</span>
                  </div>
                  <div className="notification-message">{n.message}</div>
                  <div className="notification-meta-row">
                    <span className={`notification-category-tag ${meta.cls}`}>{meta.label}</span>
                    {n.priority && n.priority !== 'NORMAL' && n.priority !== 'LOW' && (
                      <span className={`notification-priority-tag ${n.priority.toLowerCase()}`}>{n.priority}</span>
                    )}
                    {n.actionUrl && <span className="notification-action-hint">{getActionLabel(n)} →</span>}
                  </div>
                </div>
                <div className="notification-item-actions" onClick={(e) => e.stopPropagation()}>
                  {!n.isRead && (
                    <button type="button" className="notification-mini-btn" onClick={() => onMarkRead(n.id)} title="Mark as read">
                      <Icon name="check" size={13} />
                    </button>
                  )}
                  <button type="button" className="notification-mini-btn delete" onClick={() => onDelete(n.id)} title="Dismiss">
                    <Icon name="trash-2" size={13} />
                  </button>
                </div>
                {!n.isRead && <span className="notification-unread-dot" />}
              </div>
            );
          })
        )}
      </div>

      <div className="notification-dropdown-footer">
        <Link to="/notifications" onClick={onClose} className="notification-view-all">View all notifications →</Link>
      </div>
    </div>
  );
}
