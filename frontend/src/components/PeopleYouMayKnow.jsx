import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import Avatar from './Avatar';
import Icon from './Icon';

/** A few other learners to connect with, on the feed. Shows nothing when there is no one to suggest. */
export default function PeopleYouMayKnow({ onNetworkChanged }) {
  const [suggestions, setSuggestions] = useState(null);
  const [pendingId, setPendingId] = useState(null);
  const [error, setError] = useState(null);

  const loadSuggestions = useCallback(async () => {
    try {
      const res = await api.getNetworkSuggestions('', 0, 5);
      setSuggestions(res?.content || []);
    } catch {
      setSuggestions([]);
    }
  }, []);

  useEffect(() => {
    loadSuggestions();
  }, [loadSuggestions]);

  async function handleConnect(user) {
    setPendingId(user.id);
    setError(null);
    try {
      if (user.relationshipStatus === 'PENDING_SENT' && user.connectionId) {
        await api.withdrawConnectionRequest(user.connectionId);
      } else {
        await api.sendConnectionRequest(user.id);
      }
      await loadSuggestions();
      onNetworkChanged?.();
    } catch (e) {
      setError(e.message);
    } finally {
      setPendingId(null);
    }
  }

  if (suggestions === null) {
    return (
      <aside className="side-card skeleton-card">
        <h2 className="side-title">People you may know</h2>
      </aside>
    );
  }
  if (suggestions.length === 0) return null;

  return (
    <aside className="side-card network-side-card">
      <div className="side-card-header">
        <h2 className="side-title">People you may know</h2>
      </div>
      {error && <p className="field-error" role="alert">{error}</p>}
      <ul className="people-list">
        {suggestions.map((u) => {
          const isSent = u.relationshipStatus === 'PENDING_SENT';
          const isConnected = u.relationshipStatus === 'ACCEPTED';
          const isReceived = u.relationshipStatus === 'PENDING_RECEIVED';

          return (
            <li key={u.id} className="people-item">
              <div className="people-item-user">
                <Avatar name={u.displayName} url={u.pictureUrl} userId={u.id} size={42} />
                <div className="people-info">
                  <Link to={`/profile/${u.id}`} className="people-name">{u.displayName}</Link>
                  {u.mutualConnectionsCount > 0 && (
                    <span className="people-mutual">
                      <Icon name="users" size={12} /> {u.mutualConnectionsCount} mutual connection{u.mutualConnectionsCount > 1 ? 's' : ''}
                    </span>
                  )}
                </div>
              </div>
              <div className="people-action">
                {isConnected ? (
                  <span className="badge badge-connected"><Icon name="check" size={12} /> Connected</span>
                ) : isReceived ? (
                  <Link to="/network?tab=invitations" className="btn btn-xs btn-outline">Respond</Link>
                ) : (
                  <button type="button" className={`btn btn-xs ${isSent ? 'btn-subtle' : 'btn-primary'}`} disabled={pendingId === u.id}
                          onClick={() => handleConnect(u)} title={isSent ? 'Click to withdraw the request' : 'Send a connection request'}>
                    {pendingId === u.id ? '...' : isSent ? 'Pending' : <><Icon name="user-plus" size={13} /> Connect</>}
                  </button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
      <Link to="/network?tab=discover" className="side-more">See all suggestions →</Link>
    </aside>
  );
}
