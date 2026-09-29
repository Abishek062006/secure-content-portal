import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';

function formatDateTime(iso) {
  return new Date(iso).toLocaleString(undefined, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

/** A short, readable device label from the raw User-Agent string — good enough to scan, not a full parser. */
function deviceLabel(ua) {
  if (!ua) return '—';
  if (/iphone/i.test(ua)) return 'iPhone';
  if (/ipad/i.test(ua)) return 'iPad';
  if (/android/i.test(ua)) return 'Android';
  if (/macintosh/i.test(ua)) return 'Mac';
  if (/windows/i.test(ua)) return 'Windows';
  if (/linux/i.test(ua)) return 'Linux';
  return 'Unknown device';
}

const ACTION_LABEL = { LOGIN: 'Login', LOGIN_FAILED: 'Login failed', LOGOUT: 'Logout' };

export default function AuditLog() {
  const [entries, setEntries] = useState([]);
  const [actions, setActions] = useState([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);

  const [filters, setFilters] = useState({ action: '', actor: '', from: '', to: '' });
  const [applied, setApplied] = useState(filters);

  const load = useCallback((f, p) => {
    setLoading(true);
    const params = new URLSearchParams();
    if (f.action) params.set('action', f.action);
    if (f.actor) params.set('actor', f.actor);
    if (f.from) params.set('from', new Date(f.from).toISOString());
    if (f.to) params.set('to', new Date(f.to).toISOString());
    params.set('page', p);
    api.get(`/api/admin/audit?${params}`)
      .then((res) => {
        setEntries((prev) => (p === 0 ? res.entries : [...prev, ...res.entries]));
        setActions(res.actions);
        setHasMore(res.hasMore);
        setPage(p);
      })
      .catch((err) => setErrorMessage(err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(applied, 0); }, [applied, load]);

  function submitFilters(e) {
    e.preventDefault();
    setApplied(filters);
  }

  function clearFilters() {
    const empty = { action: '', actor: '', from: '', to: '' };
    setFilters(empty);
    setApplied(empty);
  }

  const filtersActive = applied.action || applied.actor || applied.from || applied.to;

  return (
    <div className="container-wide">
      <Alert error={errorMessage} />

      <div className="page-head">
        <h1>Audit log</h1>
        <Link className="btn" to="/admin/courses">Back to courses</Link>
      </div>

      <form className="form-panel qb-toolbar" onSubmit={submitFilters}>
        <div className="qb-config-grid">
          <div className="field">
            <label htmlFor="filter-action">Action</label>
            <select id="filter-action" value={filters.action} onChange={(e) => setFilters({ ...filters, action: e.target.value })}>
              <option value="">Any action</option>
              {actions.map((a) => <option key={a} value={a}>{ACTION_LABEL[a] || a}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="filter-actor">Who</label>
            <input id="filter-actor" type="text" placeholder="Search by email" value={filters.actor}
                   onChange={(e) => setFilters({ ...filters, actor: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="filter-from">From</label>
            <input id="filter-from" type="datetime-local" value={filters.from}
                   onChange={(e) => setFilters({ ...filters, from: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="filter-to">To</label>
            <input id="filter-to" type="datetime-local" value={filters.to}
                   onChange={(e) => setFilters({ ...filters, to: e.target.value })} />
          </div>
        </div>
        <div className="qb-actions">
          <button type="submit" className="btn btn-primary">Filter</button>
          {filtersActive && <button type="button" className="btn" onClick={clearFilters}>Clear</button>}
        </div>
      </form>

      {!loading && entries.length === 0 && (
        <div className="empty-state">
          <p>{filtersActive ? 'No entries match these filters.' : 'No activity recorded yet.'}</p>
        </div>
      )}

      {entries.length > 0 && (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Who</th>
                <th>Action</th>
                <th>Detail</th>
                <th>IP address</th>
                <th>Device</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.id}>
                  <td>{formatDateTime(entry.createdAt)}</td>
                  <td>{entry.actorEmail}</td>
                  <td><span className={`badge${entry.action === 'LOGIN_FAILED' ? ' status-error' : ''}`}>{ACTION_LABEL[entry.action] || entry.action}</span></td>
                  <td>{entry.detail || '—'}</td>
                  <td>{entry.ipAddress || '—'}</td>
                  <td title={entry.userAgent || ''}>{deviceLabel(entry.userAgent)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {hasMore && (
        <div className="form-actions">
          <button type="button" className="btn" disabled={loading} onClick={() => load(applied, page + 1)}>
            {loading ? 'Loading…' : 'Load more'}
          </button>
        </div>
      )}
    </div>
  );
}
