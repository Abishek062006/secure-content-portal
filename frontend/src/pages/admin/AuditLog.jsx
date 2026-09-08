import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';

function formatDateTime(iso) {
  return new Date(iso).toLocaleString(undefined, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

export default function AuditLog() {
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    api.get('/api/admin/audit')
      .then(setEntries)
      .catch((err) => setErrorMessage(err.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="container-wide">
      <Alert error={errorMessage} />

      <div className="page-head">
        <h1>Audit log</h1>
        <Link className="btn" to="/admin/content">Back to content</Link>
      </div>

      {!loading && entries.length === 0 && (
        <div className="empty-state">
          <p>No admin actions recorded yet.</p>
        </div>
      )}

      {entries.length > 0 && (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Admin</th>
                <th>Action</th>
                <th>Detail</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.id}>
                  <td>{formatDateTime(entry.createdAt)}</td>
                  <td>{entry.actorEmail}</td>
                  <td><span className="badge">{entry.action}</span></td>
                  <td>{entry.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
