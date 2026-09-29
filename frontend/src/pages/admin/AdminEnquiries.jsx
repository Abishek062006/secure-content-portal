import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';

function formatDateTime(iso) {
  return new Date(iso).toLocaleString(undefined, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

function EnquiryRow({ enquiry, onContacted }) {
  const [busy, setBusy] = useState(false);

  async function markContacted() {
    setBusy(true);
    try {
      await onContacted(enquiry.id);
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="registration-row">
      <div className="registration-main">
        <div>
          <Link to={`/admin/courses/${enquiry.courseId}/edit`} className="registration-course">{enquiry.courseTitle || 'Untitled course'}</Link>
          <span className={`badge${enquiry.status === 'NEW' ? ' status-published' : ''}`}>
            {enquiry.status === 'NEW' ? 'New' : 'Contacted'}
          </span>
        </div>
        <div className="field-hint">
          {enquiry.name} · {enquiry.email}{enquiry.phone ? ` · ${enquiry.phone}` : ''} · {formatDateTime(enquiry.createdAt)}
          {enquiry.contactedAt && ` · contacted ${formatDateTime(enquiry.contactedAt)} by ${enquiry.contactedBy}`}
        </div>
        {enquiry.message && <p className="registration-message">"{enquiry.message}"</p>}
      </div>

      {enquiry.status === 'NEW' && (
        <div className="registration-actions">
          <button type="button" className="btn btn-primary" disabled={busy} onClick={markContacted}>
            {busy ? 'Saving…' : 'Mark contacted'}
          </button>
        </div>
      )}
    </li>
  );
}

export default function AdminEnquiries() {
  const [enquiries, setEnquiries] = useState([]);
  const [statusFilter, setStatusFilter] = useState('NEW');
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);

  function load(status) {
    setLoading(true);
    api.get(`/api/admin/enquiries${status ? `?status=${status}` : ''}`)
      .then(setEnquiries)
      .catch((err) => setErrorMessage(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(() => { load(statusFilter === 'ALL' ? null : statusFilter); }, [statusFilter]);

  async function markContacted(id) {
    setErrorMessage(null);
    try {
      await api.post(`/api/admin/enquiries/${id}/contacted`);
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
        <h1>Enquiries</h1>
        <Link className="btn" to="/admin/courses">Back to courses</Link>
      </div>

      <div className="qb-filters">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} aria-label="Filter by status">
          <option value="NEW">New</option>
          <option value="ALL">All enquiries</option>
        </select>
      </div>

      {!loading && enquiries.length === 0 && (
        <div className="empty-state">
          <p>{statusFilter === 'NEW' ? 'No new enquiries.' : 'No enquiries yet.'}</p>
        </div>
      )}

      {enquiries.length > 0 && (
        <ul className="registration-list">
          {enquiries.map((e) => <EnquiryRow key={e.id} enquiry={e} onContacted={markContacted} />)}
        </ul>
      )}
    </div>
  );
}
