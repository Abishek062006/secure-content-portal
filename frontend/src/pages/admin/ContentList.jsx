import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';
import ConfirmDialog from '../../components/ConfirmDialog';

function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

export default function ContentList() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const location = useLocation();
  const navigate = useNavigate();
  // Copied into local state so it stays on screen after the replace-nav
  // below clears location.state (which only exists to stop the message
  // from reappearing on a back-navigation or refresh).
  const [successMessage] = useState(location.state?.success || null);

  function load() {
    setLoading(true);
    api.get('/api/admin/content')
      .then(setItems)
      .catch((err) => setErrorMessage(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  useEffect(() => {
    if (location.state?.success) {
      navigate(location.pathname, { replace: true, state: {} });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function confirmDelete() {
    const item = pendingDelete;
    setPendingDelete(null);
    try {
      await api.del(`/api/admin/content/${item.id}`);
      setItems((prev) => prev.filter((i) => i.id !== item.id));
    } catch (err) {
      setErrorMessage(err.message);
    }
  }

  return (
    <div className="container-wide">
      <Alert success={successMessage} error={errorMessage} />

      <div className="page-head">
        <h1>Content</h1>
        <div className="row-actions">
          <Link className="btn" to="/admin/audit">Audit log</Link>
          <Link className="btn btn-primary" to="/admin/content/new">Upload content</Link>
        </div>
      </div>

      {!loading && items.length === 0 && (
        <div className="empty-state">
          <p>No content uploaded yet.</p>
          <Link className="btn btn-primary" to="/admin/content/new">Upload your first item</Link>
        </div>
      )}

      {items.length > 0 && (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Title</th>
                <th>Type</th>
                <th>Category</th>
                <th>Size</th>
                <th>Views</th>
                <th>Last viewed</th>
                <th>Uploaded</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id}>
                  <td>{item.title}</td>
                  <td><span className="badge">{item.contentType}</span></td>
                  <td>{item.category || '—'}</td>
                  <td>{item.sizeLabel}</td>
                  <td>{item.viewCount}</td>
                  <td>{formatDateTime(item.lastViewedAt)}</td>
                  <td>{formatDateTime(item.createdAt)}</td>
                  <td className="row-actions">
                    <Link className="btn" to={`/content/${item.id}`}>View</Link>
                    <Link className="btn" to={`/admin/content/${item.id}/edit`}>Edit</Link>
                    <button type="button" className="btn btn-danger-outline" onClick={() => setPendingDelete(item)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title={pendingDelete?.title}
        onCancel={() => setPendingDelete(null)}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
