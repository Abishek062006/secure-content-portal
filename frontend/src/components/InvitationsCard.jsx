import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import Avatar from './Avatar';
import Icon from './Icon';
import { api } from '../api';

export default function InvitationsCard({ onNetworkChanged }) {
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [processingIds, setProcessingIds] = useState(new Set());

  const loadRequests = useCallback(async () => {
    try {
      setLoading(true);
      const res = await api.getReceivedRequests();
      setRequests(res || []);
    } catch {
      setRequests([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRequests();
  }, [loadRequests]);

  const handleAccept = async (id) => {
    setProcessingIds((prev) => new Set(prev).add(id));
    try {
      await api.acceptConnectionRequest(id);
      await loadRequests();
      if (onNetworkChanged) onNetworkChanged();
    } catch (e) {
      console.error('Failed to accept request:', e);
    } finally {
      setProcessingIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  const handleIgnore = async (id) => {
    setProcessingIds((prev) => new Set(prev).add(id));
    try {
      await api.rejectConnectionRequest(id);
      await loadRequests();
      if (onNetworkChanged) onNetworkChanged();
    } catch (e) {
      console.error('Failed to reject request:', e);
    } finally {
      setProcessingIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  if (loading || !requests.length) return null;

  return (
    <section className="invitations-feed-card">
      <div className="invitations-header">
        <h3 className="invitations-title">
          <Icon name="user-check" size={18} /> Invitations ({requests.length})
        </h3>
        <Link to="/network" className="invitations-link">Manage network →</Link>
      </div>
      <div className="invitations-list">
        {requests.slice(0, 3).map((req) => (
          <div key={req.id} className="invitation-item">
            <div className="invitation-user">
              <Avatar name={req.requesterName} url={req.requesterPictureUrl} userId={req.requesterId} size={44} />
              <div className="invitation-info">
                <Link to={`/profile/${req.requesterId}`} className="invitation-name">
                  {req.requesterName}
                </Link>
                <p className="muted invitation-sub">{req.requesterEmail}</p>
              </div>
            </div>
            <div className="invitation-actions">
              <button
                type="button"
                className="btn btn-xs btn-outline"
                disabled={processingIds.has(req.id)}
                onClick={() => handleIgnore(req.id)}
              >
                Ignore
              </button>
              <button
                type="button"
                className="btn btn-xs btn-primary"
                disabled={processingIds.has(req.id)}
                onClick={() => handleAccept(req.id)}
              >
                Accept
              </button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
