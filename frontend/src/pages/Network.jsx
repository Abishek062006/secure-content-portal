import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import Alert from '../components/Alert';
import Avatar from '../components/Avatar';
import ConfirmDialog from '../components/ConfirmDialog';
import Icon from '../components/Icon';

/** How long to wait after the last keystroke before searching, so typing doesn't ask the server on every letter. */
const SEARCH_DELAY_MS = 300;

function MutualTag({ count }) {
  if (!count) return null;
  return (
    <span className="mutual-tag">
      <Icon name="users" size={12} /> {count} mutual connection{count > 1 ? 's' : ''}
    </span>
  );
}

/** Your connections, the invitations you've received and sent, and other learners to connect with. */
export default function Network() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') || 'connections';

  const [connections, setConnections] = useState([]);
  const [receivedRequests, setReceivedRequests] = useState([]);
  const [sentRequests, setSentRequests] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [searchInput, setSearchInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(null);
  const [processingId, setProcessingId] = useState(null);
  const [pendingRemove, setPendingRemove] = useState(null);

  useEffect(() => {
    const timer = setTimeout(() => setSearchQuery(searchInput.trim()), SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const loadData = useCallback(async () => {
    try {
      const [connRes, receivedRes, sentRes, suggRes] = await Promise.all([
        api.getConnections(),
        api.getReceivedRequests(),
        api.getSentRequests(),
        api.getNetworkSuggestions(searchQuery),
      ]);
      setConnections(connRes || []);
      setReceivedRequests(receivedRes || []);
      setSentRequests(sentRes || []);
      setSuggestions(suggRes?.content || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoaded(true);
    }
  }, [searchQuery]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const setTab = (tab) => setSearchParams({ tab });

  /** Runs one action on the server, then reloads everything so the lists and counts agree. */
  async function act(id, action) {
    setProcessingId(id);
    try {
      await action();
      await loadData();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcessingId(null);
    }
  }

  const handleAccept = (id) => act(id, () => api.acceptConnectionRequest(id));
  const handleReject = (id) => act(id, () => api.rejectConnectionRequest(id));
  const handleWithdraw = (id) => act(id, () => api.withdrawConnectionRequest(id));

  function confirmRemove() {
    const person = pendingRemove;
    setPendingRemove(null);
    act(person.id, () => api.removeConnection(person.id));
  }

  const handleConnectSuggestion = (user) => act(user.id, () => (
    user.relationshipStatus === 'PENDING_SENT' && user.connectionId
      ? api.withdrawConnectionRequest(user.connectionId)
      : api.sendConnectionRequest(user.id)
  ));

  const needle = searchInput.trim().toLowerCase();
  const filteredConnections = connections.filter((c) => !needle || c.displayName.toLowerCase().includes(needle));

  return (
    <div className="network-container">
      <header className="network-header-card">
        <div className="network-title-group">
          <h1>
            <Icon name="users" size={26} /> My Network
          </h1>
          <p className="muted">Manage your connections and invitations, and find other learners to connect with.</p>
        </div>

        <div className="network-stats-row">
          <button type="button" className="network-stat-box" onClick={() => setTab('connections')}>
            <span className="stat-value">{connections.length}</span>
            <span className="stat-label">Connections</span>
          </button>
          <button type="button" className="network-stat-box" onClick={() => setTab('invitations')}>
            <span className="stat-value">{receivedRequests.length}</span>
            <span className="stat-label">Invitations</span>
          </button>
          <button type="button" className="network-stat-box" onClick={() => setTab('sent')}>
            <span className="stat-value">{sentRequests.length}</span>
            <span className="stat-label">Pending sent</span>
          </button>
          <button type="button" className="network-stat-box" onClick={() => setTab('discover')}>
            <span className="stat-value">{suggestions.length}</span>
            <span className="stat-label">Suggestions</span>
          </button>
        </div>
      </header>

      <Alert error={error} />

      <div className="network-nav-tabs">
        <button type="button" className={`tab-btn ${activeTab === 'connections' ? 'active' : ''}`} onClick={() => setTab('connections')}>
          <Icon name="user-check" size={16} /> Connections ({connections.length})
        </button>
        <button type="button" className={`tab-btn ${activeTab === 'invitations' ? 'active' : ''}`} onClick={() => setTab('invitations')}>
          <Icon name="mail" size={16} /> Invitations ({receivedRequests.length})
        </button>
        <button type="button" className={`tab-btn ${activeTab === 'sent' ? 'active' : ''}`} onClick={() => setTab('sent')}>
          <Icon name="send" size={16} /> Sent ({sentRequests.length})
        </button>
        <button type="button" className={`tab-btn ${activeTab === 'discover' ? 'active' : ''}`} onClick={() => setTab('discover')}>
          <Icon name="compass" size={16} /> Discover
        </button>
      </div>

      <div className="network-search-bar">
        <Icon name="search" size={18} className="search-icon" />
        <input
          type="text"
          placeholder="Search by name..."
          aria-label="Search by name"
          maxLength={50}
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
        />
        {searchInput && (
          <button type="button" className="search-clear-btn" aria-label="Clear the search" onClick={() => setSearchInput('')}>
            <Icon name="x" size={14} />
          </button>
        )}
      </div>

      {!loaded ? (
        <div className="network-loading-grid">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="network-card skeleton-card" />
          ))}
        </div>
      ) : (
        <div className="network-content-area">
          {activeTab === 'connections' && (
            <div>
              {filteredConnections.length === 0 ? (
                <div className="empty-state-card">
                  <Icon name="users" size={48} className="empty-icon" />
                  <h3>No connections found</h3>
                  <p className="muted">
                    {searchInput ? 'No connections match your search.' : "You haven't connected with anyone yet. Find learners to connect with."}
                  </p>
                  <button type="button" className="btn btn-primary mt-3" onClick={() => setTab('discover')}>
                    Discover people
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
                        <Link to={`/profile/${user.id}`} className="network-user-name">{user.displayName}</Link>
                        <MutualTag count={user.mutualConnectionsCount} />
                      </div>
                      <div className="network-card-actions">
                        <Link to={`/profile/${user.id}`} className="btn btn-sm btn-outline">Profile</Link>
                        <button type="button" className="btn btn-sm btn-subtle text-danger" disabled={processingId === user.id}
                                onClick={() => setPendingRemove(user)}>
                          Remove
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

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
                          <Link to={`/profile/${req.requesterId}`} className="request-user-name">{req.requesterName}</Link>
                          <p className="muted text-sm">Wants to connect with you</p>
                        </div>
                      </div>
                      <div className="request-row-actions">
                        <button type="button" className="btn btn-sm btn-outline" disabled={processingId === req.id} onClick={() => handleReject(req.id)}>
                          Ignore
                        </button>
                        <button type="button" className="btn btn-sm btn-primary" disabled={processingId === req.id} onClick={() => handleAccept(req.id)}>
                          Accept
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {activeTab === 'sent' && (
            <div>
              {sentRequests.length === 0 ? (
                <div className="empty-state-card">
                  <Icon name="send" size={48} className="empty-icon" />
                  <h3>No pending sent requests</h3>
                  <p className="muted">Connection requests you have sent will be tracked here until they are answered.</p>
                </div>
              ) : (
                <div className="requests-list">
                  {sentRequests.map((req) => (
                    <div key={req.id} className="request-row-card">
                      <div className="request-row-user">
                        <Avatar name={req.receiverName} url={req.receiverPictureUrl} userId={req.receiverId} size={52} />
                        <div>
                          <Link to={`/profile/${req.receiverId}`} className="request-user-name">{req.receiverName}</Link>
                          <p className="muted text-sm">Waiting for a reply</p>
                        </div>
                      </div>
                      <div className="request-row-actions">
                        <button type="button" className="btn btn-sm btn-subtle text-danger" disabled={processingId === req.id}
                                onClick={() => handleWithdraw(req.id)}>
                          Withdraw request
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {activeTab === 'discover' && (
            <div>
              {suggestions.length === 0 ? (
                <div className="empty-state-card">
                  <Icon name="compass" size={48} className="empty-icon" />
                  <h3>No one found</h3>
                  <p className="muted">{searchInput ? 'Try a different name.' : 'Check back later, or search for someone by name.'}</p>
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
                          <Link to={`/profile/${user.id}`} className="network-user-name">{user.displayName}</Link>
                          <MutualTag count={user.mutualConnectionsCount} />
                        </div>
                        <div className="network-card-actions">
                          {isConnected ? (
                            <span className="badge badge-connected w-full justify-center">
                              <Icon name="check" size={14} /> Connected
                            </span>
                          ) : isReceived ? (
                            <button type="button" className="btn btn-sm btn-primary w-full" onClick={() => setTab('invitations')}>
                              Respond to request
                            </button>
                          ) : (
                            <button type="button" className={`btn btn-sm w-full ${isSent ? 'btn-subtle' : 'btn-primary'}`}
                                    disabled={processingId === user.id} onClick={() => handleConnectSuggestion(user)}>
                              {processingId === user.id ? '...' : isSent ? 'Pending (withdraw)' : (
                                <><Icon name="user-plus" size={14} /> Connect</>
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

      <ConfirmDialog
        open={Boolean(pendingRemove)}
        title={pendingRemove?.displayName || ''}
        detail="This ends your connection. You can send a new request later."
        onCancel={() => setPendingRemove(null)}
        onConfirm={confirmRemove}
      />
    </div>
  );
}
