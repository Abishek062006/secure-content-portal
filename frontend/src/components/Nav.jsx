import { useState, useEffect, useRef } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Logo } from './Icons';
import { api } from '../api';

function initials(displayName) {
  const source = (displayName || '').trim();
  if (!source) return '?';
  const parts = source.split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 1).toUpperCase();
  return (parts[0].slice(0, 1) + parts[parts.length - 1].slice(0, 1)).toUpperCase();
}

export default function Nav() {
  const { user, logout } = useAuth();
  const [points, setPoints] = useState(null);
  const [moreOpen, setMoreOpen] = useState(false);
  const dropdownRef = useRef(null);
  const location = useLocation();

  useEffect(() => {
    if (user) {
      api.getGamificationSummary()
        .then((res) => setPoints(res.totalPoints))
        .catch(() => {});
    }
  }, [user]);

  // Close dropdown on route change
  useEffect(() => {
    setMoreOpen(false);
  }, [location]);

  // Click outside to close dropdown
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setMoreOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const isMoreActive = location.pathname.startsWith('/leaderboard') || location.pathname.startsWith('/hackathons') || location.pathname.startsWith('/interviews');

  return (
    <nav className="nav">
      <Link className="nav-brand" to="/">
        <Logo />
        GradientNovaAI
      </Link>

      {user && (
        <div className="nav-links">
          {user.admin ? (
            <>
              <NavLink to="/admin/dashboard">Dashboard</NavLink>
              <NavLink to="/admin/courses">Manage Courses</NavLink>
              <NavLink to="/admin/leaderboard">Gamification</NavLink>
              <NavLink to="/admin/hackathons">Hackathons</NavLink>
              <NavLink to="/admin/interviews">Mock Interviews</NavLink>
              <NavLink to="/admin/users">Users</NavLink>
              <NavLink to="/admin/audit">Audit Log</NavLink>
            </>
          ) : (
            <>
              <NavLink to="/courses">Courses</NavLink>
              <NavLink to="/feed">Feed</NavLink>
              <NavLink to="/my-learning">My Learning</NavLink>
              <div className="nav-dropdown" ref={dropdownRef} style={{ position: 'relative' }}>
                <button
                  type="button"
                  className={`nav-dropdown-btn ${isMoreActive ? 'active' : ''}`}
                  onClick={() => setMoreOpen(!moreOpen)}
                  style={{
                    background: isMoreActive ? '#eff6ff' : 'transparent',
                    color: isMoreActive ? '#2563eb' : 'inherit',
                    border: 'none',
                    padding: '8px 14px',
                    borderRadius: '980px',
                    fontWeight: 500,
                    fontSize: '0.87rem',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px'
                  }}
                >
                  More <span style={{ fontSize: '0.75rem', opacity: 0.7 }}>▾</span>
                </button>

                {moreOpen && (
                  <div
                    style={{
                      position: 'absolute',
                      top: '100%',
                      left: 0,
                      marginTop: '6px',
                      background: '#ffffff',
                      border: '1px solid #e2e8f0',
                      borderRadius: '12px',
                      boxShadow: '0 10px 25px rgba(0,0,0,0.08)',
                      minWidth: '165px',
                      padding: '6px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '2px',
                      zIndex: 1000
                    }}
                  >
                    <NavLink
                      to="/leaderboard"
                      style={({ isActive }) => ({
                        padding: '8px 12px',
                        borderRadius: '8px',
                        fontSize: '0.85rem',
                        color: isActive ? '#2563eb' : '#334155',
                        background: isActive ? '#eff6ff' : 'transparent',
                        textDecoration: 'none',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        fontWeight: isActive ? 700 : 500
                      })}
                    >
                      🏆 Leaderboard
                    </NavLink>
                    <NavLink
                      to="/hackathons"
                      style={({ isActive }) => ({
                        padding: '8px 12px',
                        borderRadius: '8px',
                        fontSize: '0.85rem',
                        color: isActive ? '#2563eb' : '#334155',
                        background: isActive ? '#eff6ff' : 'transparent',
                        textDecoration: 'none',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        fontWeight: isActive ? 700 : 500
                      })}
                    >
                      💡 Hackathons
                    </NavLink>
                    <NavLink
                      to="/interviews"
                      style={({ isActive }) => ({
                        padding: '8px 12px',
                        borderRadius: '8px',
                        fontSize: '0.85rem',
                        color: isActive ? '#2563eb' : '#334155',
                        background: isActive ? '#eff6ff' : 'transparent',
                        textDecoration: 'none',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        fontWeight: isActive ? 700 : 500
                      })}
                    >
                      🤖 Mock Interviews
                    </NavLink>
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      )}

      <div className="nav-right">
        {user ? (
          <div className="nav-user">
            {!user.admin && points !== null && (
              <Link to="/leaderboard" className="nav-points-badge" title="Your Total Points">
                ⚡ {points} XP
              </Link>
            )}
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
