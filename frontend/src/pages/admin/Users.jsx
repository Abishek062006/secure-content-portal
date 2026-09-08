import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';
import { useAuth } from '../../context/AuthContext';

function formatDate(iso) {
  return new Date(iso).toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
}

export default function Users() {
  const { user: currentUser } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const search = searchParams.get('search') || '';

  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [busyId, setBusyId] = useState(null);

  function load() {
    setLoading(true);
    const query = new URLSearchParams();
    if (search) query.set('search', search);
    api.get(`/api/admin/users?${query.toString()}`)
      .then(setUsers)
      .catch((err) => setErrorMessage(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(load, [search]);

  function onSubmit(e) {
    e.preventDefault();
    const value = e.currentTarget.search.value.trim();
    setSearchParams(value ? { search: value } : {});
  }

  async function promote(target) {
    setBusyId(target.id);
    setErrorMessage(null);
    try {
      await api.post(`/api/admin/users/${target.id}/promote`);
      setSuccessMessage('Admin access granted.');
      load();
    } catch (err) {
      setErrorMessage(err.message);
    } finally {
      setBusyId(null);
    }
  }

  async function demote(target) {
    setBusyId(target.id);
    setErrorMessage(null);
    try {
      await api.post(`/api/admin/users/${target.id}/demote`);
      setSuccessMessage('Admin access revoked.');
      load();
    } catch (err) {
      setErrorMessage(err.message);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="container-wide">
      <Alert success={successMessage} error={errorMessage} />

      <div className="page-head">
        <h1>Users</h1>
      </div>

      <form className="search-bar" onSubmit={onSubmit}>
        <input type="search" name="search" placeholder="Search by name or email…" defaultValue={search} key={search} />
        <button type="submit" className="btn btn-primary">Search</button>
        {search && <Link className="btn" to="/admin/users">Clear</Link>}
      </form>

      {!loading && users.length === 0 && (
        <div className="empty-state">
          <p>No users match your search.</p>
        </div>
      )}

      {users.length > 0 && (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Email</th>
                <th>Name</th>
                <th>Role</th>
                <th>Joined</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>{u.email}</td>
                  <td>{u.displayName || '—'}</td>
                  <td><span className={`badge${u.admin ? ' admin' : ''}`}>{u.role}</span></td>
                  <td>{formatDate(u.createdAt)}</td>
                  <td className="row-actions">
                    {u.id === currentUser.id ? (
                      <span className="field-hint">You</span>
                    ) : u.admin ? (
                      <button type="button" className="btn btn-danger-outline" disabled={busyId === u.id}
                              onClick={() => demote(u)}>
                        Revoke admin
                      </button>
                    ) : (
                      <button type="button" className="btn btn-primary" disabled={busyId === u.id}
                              onClick={() => promote(u)}>
                        Make admin
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
