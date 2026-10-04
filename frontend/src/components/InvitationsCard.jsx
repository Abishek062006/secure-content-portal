import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import Avatar from './Avatar';
import Icon from './Icon';

/** The connection requests waiting for an answer, on the feed. Shows nothing when there are none. */
export default function InvitationsCard({ onNetworkChanged }) {
  const [requests, setRequests] = useState(null);
  const [processingId, setProcessingId] = useState(null);
  const [error, setError] = useState(null);

  const loadRequests = useCallback(async () => {
    try {
      setRequests((await api.getReceivedRequests()) || []);
    } catch {
      setRequests([]);
    }
  }, []);

  useEffect(() => {
    loadRequests();
  }, [loadRequests]);

  async function answer(id, action) {
    setProcessingId(id);
    setError(null);
    try {
      await action();
      await loadRequests();
      onNetworkChanged?.();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcessingId(null);
    }
  }

  if (!requests || requests.length === 0) return null;

  return (
    <section className="invitations-feed-card">
      <div className="invitations-header">
        <h3 className="invitations-title">
          <Icon name="user-check" size={18} /> Invitations ({requests.length})
        </h3>
        <Link to="/network?tab=invitations" className="invitations-link">Manage network →</Link>
      </div>
      {error && <p className="field-error" role="alert">{error}</p>}
      <div className="invitations-list">
        {requests.slice(0, 3).map((req) => (
          <div key={req.id} className="invitation-item">
            <div className="invitation-user">
              <Avatar name={req.requesterName} url={req.requesterPictureUrl} userId={req.requesterId} size={44} />
              <div className="invitation-info">
                <Link to={`/profile/${req.requesterId}`} className="invitation-name">{req.requesterName}</Link>
                <p className="muted invitation-sub">Wants to connect</p>
              </div>
            </div>
            <div className="invitation-actions">
              <button type="button" className="btn btn-xs btn-outline" disabled={processingId === req.id}
                      onClick={() => answer(req.id, () => api.rejectConnectionRequest(req.id))}>
                Ignore
              </button>
              <button type="button" className="btn btn-xs btn-primary" disabled={processingId === req.id}
                      onClick={() => answer(req.id, () => api.acceptConnectionRequest(req.id))}>
                Accept
              </button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
