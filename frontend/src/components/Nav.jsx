import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

function initials(displayName) {
  const source = (displayName || '').trim();
  if (!source) return '?';
  const parts = source.split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 1).toUpperCase();
  return (parts[0].slice(0, 1) + parts[parts.length - 1].slice(0, 1)).toUpperCase();
}

export default function Nav() {
  const { user, logout } = useAuth();

  return (
    <nav className="nav">
      <Link className="nav-brand" to="/">Secure Content Portal</Link>

      {user && (
        <div className="nav-links">
          <Link to="/library">Library</Link>
          {user.admin && <Link to="/admin/content">Manage content</Link>}
          {user.admin && <Link to="/admin/users">Users</Link>}
        </div>
      )}

      <div className="nav-right">
        {user ? (
          <div className="nav-user">
            <span className={`badge${user.admin ? ' admin' : ''}`}>{user.role}</span>
            {user.pictureUrl ? (
              <img className="avatar" src={user.pictureUrl} alt="" />
            ) : (
              <span className="avatar">{initials(user.displayName)}</span>
            )}
            <span className="nav-user-name">{user.displayName}</span>
            <button type="button" className="btn" onClick={logout}>Sign out</button>
          </div>
        ) : (
          <Link className="btn btn-primary" to="/login">Sign in</Link>
        )}
      </div>
    </nav>
  );
}
