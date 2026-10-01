import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import Avatar from './Avatar';
import Icon from './Icon';
import { api } from '../api';

export default function PeopleYouMayKnow({ onNetworkChanged }) {
  const [suggestions, setSuggestions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pendingIds, setPendingIds] = useState(new Set());

  const loadSuggestions = useCallback(async () => {
    try {
      setLoading(true);
      const res = await api.getNetworkSuggestions('', 0, 5);
      setSuggestions(res?.content || []);
    } catch {
      setSuggestions([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSuggestions();
  }, [loadSuggestions]);

  const handleConnect = async (user) => {
    const userId = user.id;
    setPendingIds((prev) => new Set(prev).add(userId));
    try {
      if (user.relationshipStatus === 'PENDING_SENT' && user.connectionId) {
        await api.withdrawConnectionRequest(user.connectionId);
      } else {
        await api.sendConnectionRequest(userId);
      }
      await loadSuggestions();
      if (onNetworkChanged) onNetworkChanged();
    } catch (e) {
      console.error('Connection request error:', e);
    } finally {
      setPendingIds((prev) => {
        const next = new Set(prev);
        next.delete(userId);
        return next;
      });
    }
  };

  if (loading) {
    return (
      <aside className="side-card skeleton-card">
        <h2 className="side-title">People you may know</h2>
      </aside>
    );
  }

  if (!suggestions.length) return null;

  return (
    <aside className="side-card network-side-card">
      <div className="side-card-header">
        <h2 className="side-title">People you may know</h2>
      </div>
      <ul className="people-list">
        {suggestions.map((u) => {
          const isPendingOp = pendingIds.has(u.id);
          const isSent = u.relationshipStatus === 'PENDING_SENT';
          const isConnected = u.relationshipStatus === 'ACCEPTED';
          const isReceived = u.relationshipStatus === 'PENDING_RECEIVED';

          return (
            <li key={u.id} className="people-item">
              <div className="people-item-user">
                <Avatar name={u.displayName} url={u.pictureUrl} userId={u.id} size={42} />
                <div className="people-info">
                  <Link to={`/profile/${u.id}`} className="people-name">{u.displayName}</Link>
                  <p className="people-headline muted">
                    {u.role === 'ADMIN' ? 'Platform Administrator' : 'Software Learner'}
                  </p>
                  {u.mutualConnectionsCount > 0 && (
                    <span className="people-mutual">
                      <Icon name="users" size={12} /> {u.mutualConnectionsCount} mutual connection{u.mutualConnectionsCount > 1 ? 's' : ''}
                    </span>
                  )}
                </div>
              </div>
              <div className="people-action">
                {isConnected ? (
                  <span className="badge badge-connected">
                    <Icon name="check" size={12} /> Connected
                  </span>
                ) : isReceived ? (
                  <Link to="/network" className="btn btn-xs btn-outline">
                    Respond
                  </Link>
                ) : (
                  <button
                    type="button"
                    className={`btn btn-xs ${isSent ? 'btn-subtle' : 'btn-primary'}`}
                    disabled={isPendingOp}
                    onClick={() => handleConnect(u)}
                    title={isSent ? 'Click to withdraw request' : 'Send connection request'}
                  >
                    {isPendingOp ? (
                      '...'
                    ) : isSent ? (
                      <>Pending</>
                    ) : (
                      <>
                        <Icon name="user-plus" size={13} /> Connect
                      </>
                    )}
                  </button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
      <Link to="/network" className="side-more">
        See all suggestions →
      </Link>
    </aside>
  );
}
