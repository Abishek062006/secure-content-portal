import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';

function formatDateTime(iso) {
  return new Date(iso).toLocaleString(undefined, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

const STATUS_LABEL = { PENDING: 'Pending', APPROVED: 'Approved', DENIED: 'Denied' };

/** A request being decided: shows the note field only once the admin picks approve or deny. */
function RequestRow({ request, onDecide }) {
  const [deciding, setDeciding] = useState(null); // 'approve' | 'deny' | null
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(action) {
    setBusy(true);
    try {
      await onDecide(request.id, action, note);
      setDeciding(null);
      setNote('');
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="registration-row">
      <div className="registration-main">
        <div>
          <Link to={`/admin/courses/${request.courseId}/edit`} className="registration-course">{request.courseTitle || 'Untitled course'}</Link>
          <span className={`badge${request.status === 'DENIED' ? ' status-error' : request.status === 'APPROVED' ? ' status-published' : ''}`}>
            {STATUS_LABEL[request.status] || request.status}
          </span>
        </div>
        <div className="field-hint">
          {request.userName || 'Unknown'} ({request.userEmail || 'no email'}) · requested {formatDateTime(request.requestedAt)}
          {request.decidedAt && ` · decided ${formatDateTime(request.decidedAt)} by ${request.decidedBy}`}
        </div>
        {request.message && <p className="registration-message">"{request.message}"</p>}
        {request.decisionNote && <p className="field-hint">Note: {request.decisionNote}</p>}
      </div>

      {request.status === 'PENDING' && (
        deciding ? (
          <div className="registration-decide">
            <textarea rows={2} maxLength={1000} placeholder="Optional note (the learner doesn't see this)"
                      value={note} onChange={(e) => setNote(e.target.value)} />
            <div className="form-actions">
              <button type="button" className="btn btn-primary" disabled={busy} onClick={() => submit(deciding)}>
                {busy ? 'Saving…' : deciding === 'approve' ? 'Confirm approve' : 'Confirm deny'}
              </button>
              <button type="button" className="btn" disabled={busy} onClick={() => setDeciding(null)}>Cancel</button>
            </div>
          </div>
        ) : (
          <div className="registration-actions">
            <button type="button" className="btn btn-primary" onClick={() => setDeciding('approve')}>Approve</button>
            <button type="button" className="btn btn-danger-outline" onClick={() => setDeciding('deny')}>Deny</button>
          </div>
        )
      )}
    </li>
  );
}

export default function AdminRegistrations() {
  const [requests, setRequests] = useState([]);
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);

  function load(status) {
    setLoading(true);
    api.get(`/api/admin/registrations${status ? `?status=${status}` : ''}`)
      .then(setRequests)
      .catch((err) => setErrorMessage(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(() => { load(statusFilter === 'ALL' ? null : statusFilter); }, [statusFilter]);

  async function decide(id, action, note) {
    setErrorMessage(null);
    try {
      await api.post(`/api/admin/registrations/${id}/${action}`, { note });
      load(statusFilter === 'ALL' ? null : statusFilter);
    } catch (err) {
      setErrorMessage(err.message);
      throw err;
    }
  }

  return (
    <div className="container-wide">
      <Alert error={errorMessage} />

      <div className="page-head">
        <h1>Registration requests</h1>
        <Link className="btn" to="/admin/courses">Back to courses</Link>
      </div>

      <div className="qb-filters">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} aria-label="Filter by status">
          <option value="PENDING">Pending</option>
          <option value="ALL">All requests</option>
        </select>
      </div>

      {!loading && requests.length === 0 && (
        <div className="empty-state">
          <p>{statusFilter === 'PENDING' ? 'No requests waiting for review.' : 'No requests yet.'}</p>
        </div>
      )}

      {requests.length > 0 && (
        <ul className="registration-list">
          {requests.map((r) => <RequestRow key={r.id} request={r} onDecide={decide} />)}
        </ul>
      )}
    </div>
  );
}
