import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Logo } from './Icons';

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
      <Link className="nav-brand" to="/">
        <Logo />
        GradientNovaAI
      </Link>

      {user && (
        <div className="nav-links">
          <NavLink to="/courses">Courses</NavLink>
          {!user.admin && <NavLink to="/my-learning">My learning</NavLink>}
          <NavLink to="/feed">Feed</NavLink>
          {user.admin && <NavLink to="/admin/courses">Manage courses</NavLink>}
          {user.admin && <NavLink to="/admin/users">Users</NavLink>}
          {user.admin && <NavLink to="/admin/audit">Audit log</NavLink>}
        </div>
      )}

      <div className="nav-right">
        {user ? (
          <div className="nav-user">
            {user.admin && <span className="badge admin">Admin</span>}
            <Link to="/profile" className="nav-profile-link" title="Your profile">
              {user.pictureUrl ? (
                <img className="avatar" src={user.pictureUrl} alt="" referrerPolicy="no-referrer" />
              ) : (
                <span className="avatar">{initials(user.displayName)}</span>
              )}
              <span className="nav-user-name">{user.displayName}</span>
            </Link>
            <button type="button" className="btn" onClick={logout}>Sign out</button>
          </div>
        ) : (
          <Link className="btn btn-primary" to="/login">Sign in</Link>
        )}
      </div>
    </nav>
  );
}
