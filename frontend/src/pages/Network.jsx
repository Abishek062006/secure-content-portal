import { useEffect, useState, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import Avatar from '../components/Avatar';
import Icon from '../components/Icon';
import Alert from '../components/Alert';

export default function Network() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') || 'connections';

  const [connections, setConnections] = useState([]);
  const [receivedRequests, setReceivedRequests] = useState([]);
  const [sentRequests, setSentRequests] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [processingId, setProcessingId] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [connRes, receivedRes, sentRes, suggRes] = await Promise.all([
        api.getConnections().catch(() => []),
        api.getReceivedRequests().catch(() => []),
        api.getSentRequests().catch(() => []),
        api.getNetworkSuggestions(searchQuery).catch(() => ({ content: [] })),
      ]);
      setConnections(connRes || []);
      setReceivedRequests(receivedRes || []);
      setSentRequests(sentRes || []);
      setSuggestions(suggRes?.content || []);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [searchQuery]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const setTab = (tab) => {
    setSearchParams({ tab });
  };

  const handleAccept = async (id) => {
    setProcessingId(id);
    try {
      await api.acceptConnectionRequest(id);
      await loadData();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcessingId(null);
    }
  };

  const handleReject = async (id) => {
    setProcessingId(id);
    try {
      await api.rejectConnectionRequest(id);
      await loadData();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcessingId(null);
    }
  };

  const handleWithdraw = async (id) => {
    setProcessingId(id);
    try {
      await api.withdrawConnectionRequest(id);
      await loadData();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcessingId(null);
    }
  };

  const handleRemoveConnection = async (userId) => {
    if (!window.confirm('Are you sure you want to remove this connection?')) return;
    setProcessingId(userId);
    try {
      await api.removeConnection(userId);
      await loadData();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcessingId(null);
    }
  };

  const handleConnectSuggestion = async (user) => {
    setProcessingId(user.id);
    try {
      if (user.relationshipStatus === 'PENDING_SENT' && user.connectionId) {
        await api.withdrawConnectionRequest(user.connectionId);
      } else {
        await api.sendConnectionRequest(user.id);
      }
      await loadData();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcessingId(null);
    }
  };

  const filteredConnections = connections.filter(
    (c) =>
      !searchQuery ||
      c.displayName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      c.email.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="network-container">
      <header className="network-header-card">
        <div className="network-title-group">
          <h1>
            <Icon name="users" size={26} /> My Network
          </h1>
          <p className="muted">Manage your connections, invitations, and discover learners & colleagues.</p>
        </div>

        <div className="network-stats-row">
          <div className="network-stat-box" onClick={() => setTab('connections')}>
            <span className="stat-value">{connections.length}</span>
            <span className="stat-label">Connections</span>
          </div>
          <div className="network-stat-box" onClick={() => setTab('invitations')}>
            <span className="stat-value">{receivedRequests.length}</span>
            <span className="stat-label">Invitations</span>
          </div>
          <div className="network-stat-box" onClick={() => setTab('sent')}>
            <span className="stat-value">{sentRequests.length}</span>
            <span className="stat-label">Pending Sent</span>
          </div>
          <div className="network-stat-box" onClick={() => setTab('discover')}>
            <span className="stat-value">{suggestions.length}</span>
            <span className="stat-label">Suggestions</span>
          </div>
        </div>
      </header>

      <Alert error={error} />

      <div className="network-nav-tabs">
        <button
          type="button"
          className={`tab-btn ${activeTab === 'connections' ? 'active' : ''}`}
          onClick={() => setTab('connections')}
        >
          <Icon name="user-check" size={16} /> Connections ({connections.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'invitations' ? 'active' : ''}`}
          onClick={() => setTab('invitations')}
        >
          <Icon name="mail" size={16} /> Invitations ({receivedRequests.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'sent' ? 'active' : ''}`}
          onClick={() => setTab('sent')}
        >
          <Icon name="send" size={16} /> Sent ({sentRequests.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'discover' ? 'active' : ''}`}
          onClick={() => setTab('discover')}
        >
          <Icon name="compass" size={16} /> Discover
        </button>
      </div>

      <div className="network-search-bar">
        <Icon name="search" size={18} className="search-icon" />
        <input
          type="text"
          placeholder="Search by name or email..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />
        {searchQuery && (
          <button type="button" className="search-clear-btn" onClick={() => setSearchQuery('')}>
            ✕
          </button>
        )}
      </div>

      {loading ? (
        <div className="network-loading-grid">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="network-card skeleton-card" />
          ))}
        </div>
      ) : (
        <div className="network-content-area">
          {/* TAB 1: CONNECTIONS */}
          {activeTab === 'connections' && (
            <div>
              {filteredConnections.length === 0 ? (
                <div className="empty-state-card">
                  <Icon name="users" size={48} className="empty-icon" />
                  <h3>No connections found</h3>
                  <p className="muted">
                    {searchQuery ? 'No connections match your search query.' : "You haven't connected with anyone yet. Explore suggested profiles to expand your network."}
                  </p>
                  <button type="button" className="btn btn-primary mt-3" onClick={() => setTab('discover')}>
                    Discover People
                  </button>
                </div>
              ) : (
                <div className="network-grid">
                  {filteredConnections.map((user) => (
                    <div key={user.id} className="network-card">
                      <div className="network-card-header">
                        <Avatar name={user.displayName} url={user.pictureUrl} userId={user.id} size={64} />
                      </div>
                      <div className="network-card-body">
                        <Link to={`/profile/${user.id}`} className="network-user-name">
                          {user.displayName}
                        </Link>
                        <span className="network-user-role">
                          {user.role === 'ADMIN' ? 'Platform Admin' : 'Software Learner'}
                        </span>
                        {user.mutualConnectionsCount > 0 && (
                          <span className="mutual-tag">
                            <Icon name="users" size={12} /> {user.mutualConnectionsCount} mutual connection{user.mutualConnectionsCount > 1 ? 's' : ''}
                          </span>
                        )}
                      </div>
                      <div className="network-card-actions">
                        <Link to="/feed" className="btn btn-sm btn-outline">
                          Profile
                        </Link>
                        <button
                          type="button"
                          className="btn btn-sm btn-subtle text-danger"
                          disabled={processingId === user.id}
                          onClick={() => handleRemoveConnection(user.id)}
                        >
                          Remove
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 2: INVITATIONS */}
          {activeTab === 'invitations' && (
            <div>
              {receivedRequests.length === 0 ? (
                <div className="empty-state-card">
                  <Icon name="mail" size={48} className="empty-icon" />
                  <h3>No pending invitations</h3>
                  <p className="muted">When someone sends you a connection request, it will show up here.</p>
                </div>
              ) : (
                <div className="requests-list">
                  {receivedRequests.map((req) => (
                    <div key={req.id} className="request-row-card">
                      <div className="request-row-user">
                        <Avatar name={req.requesterName} url={req.requesterPictureUrl} userId={req.requesterId} size={52} />
                        <div>
                          <Link to={`/profile/${req.requesterId}`} className="request-user-name">
                            {req.requesterName}
                          </Link>
                          <p className="muted text-sm">{req.requesterEmail}</p>
                        </div>
                      </div>
                      <div className="request-row-actions">
                        <button
                          type="button"
                          className="btn btn-sm btn-outline"
                          disabled={processingId === req.id}
                          onClick={() => handleReject(req.id)}
                        >
                          Ignore
                        </button>
                        <button
                          type="button"
                          className="btn btn-sm btn-primary"
                          disabled={processingId === req.id}
                          onClick={() => handleAccept(req.id)}
                        >
                          Accept
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 3: SENT REQUESTS */}
          {activeTab === 'sent' && (
            <div>
              {sentRequests.length === 0 ? (
                <div className="empty-state-card">
                  <Icon name="send" size={48} className="empty-icon" />
                  <h3>No pending sent requests</h3>
                  <p className="muted">Connection requests you have sent to others will be tracked here.</p>
                </div>
              ) : (
                <div className="requests-list">
                  {sentRequests.map((req) => (
                    <div key={req.id} className="request-row-card">
                      <div className="request-row-user">
                        <Avatar name={req.receiverName} url={req.receiverPictureUrl} userId={req.receiverId} size={52} />
                        <div>
                          <Link to={`/profile/${req.receiverId}`} className="request-user-name">
                            {req.receiverName}
                          </Link>
                          <p className="muted text-sm">{req.receiverEmail}</p>
                        </div>
                      </div>
                      <div className="request-row-actions">
                        <button
                          type="button"
                          className="btn btn-sm btn-subtle text-danger"
                          disabled={processingId === req.id}
                          onClick={() => handleWithdraw(req.id)}
                        >
                          Withdraw Request
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 4: DISCOVER / SUGGESTIONS */}
          {activeTab === 'discover' && (
            <div>
              {suggestions.length === 0 ? (
                <div className="empty-state-card">
                  <Icon name="compass" size={48} className="empty-icon" />
                  <h3>No suggested profiles</h3>
                  <p className="muted">Check back later or try searching with another keyword.</p>
                </div>
              ) : (
                <div className="network-grid">
                  {suggestions.map((user) => {
                    const isSent = user.relationshipStatus === 'PENDING_SENT';
                    const isConnected = user.relationshipStatus === 'ACCEPTED';
                    const isReceived = user.relationshipStatus === 'PENDING_RECEIVED';

                    return (
                      <div key={user.id} className="network-card">
                        <div className="network-card-header">
                          <Avatar name={user.displayName} url={user.pictureUrl} userId={user.id} size={64} />
                        </div>
                        <div className="network-card-body">
                          <Link to={`/profile/${user.id}`} className="network-user-name">
                            {user.displayName}
                          </Link>
                          <span className="network-user-role">
                            {user.role === 'ADMIN' ? 'Platform Admin' : 'Software Learner'}
                          </span>
                          {user.mutualConnectionsCount > 0 && (
                            <span className="mutual-tag">
                              <Icon name="users" size={12} /> {user.mutualConnectionsCount} mutual connection{user.mutualConnectionsCount > 1 ? 's' : ''}
                            </span>
                          )}
                        </div>
                        <div className="network-card-actions">
                          {isConnected ? (
                            <span className="badge badge-connected w-full justify-center">
                              <Icon name="check" size={14} /> Connected
                            </span>
                          ) : isReceived ? (
                            <button type="button" className="btn btn-sm btn-primary w-full" onClick={() => setTab('invitations')}>
                              Respond to Request
                            </button>
                          ) : (
                            <button
                              type="button"
                              className={`btn btn-sm w-full ${isSent ? 'btn-subtle' : 'btn-primary'}`}
                              disabled={processingId === user.id}
                              onClick={() => handleConnectSuggestion(user)}
                            >
                              {processingId === user.id ? (
                                '...'
                              ) : isSent ? (
                                <>Pending (Withdraw)</>
                              ) : (
                                <>
                                  <Icon name="user-plus" size={14} /> Connect
                                </>
                              )}
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
