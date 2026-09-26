import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import { Logo } from './Icons';
import DailyRing from './progress/DailyRing';

function initials(displayName) {
  const source = (displayName || '').trim();
  if (!source) return '?';
  const parts = source.split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 1).toUpperCase();
  return (parts[0].slice(0, 1) + parts[parts.length - 1].slice(0, 1)).toUpperCase();
}

export default function Nav() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [progress, setProgress] = useState(null);
  const [judging, setJudging] = useState(false);

  // Anyone asked to judge a hackathon gets a Judging link.
  useEffect(() => {
    if (!user) {
      setJudging(false);
      return;
    }
    api.getJudgedEvents().then((events) => setJudging(events.length > 0)).catch(() => setJudging(false));
  }, [user, location.pathname]);

  // Refreshed as the learner moves around, so the ring fills right after they finish a lesson.
  useEffect(() => {
    if (!user || user.admin) {
      setProgress(null);
      return;
    }
    api.getGamificationSummary().then(setProgress).catch(() => {});
  }, [user, location.pathname]);

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
              <NavLink to="/admin/hackathons">Hackathons</NavLink>
              {judging && <NavLink to="/judging">Judging</NavLink>}
              <NavLink to="/admin/users">Users</NavLink>
              <NavLink to="/admin/audit">Audit Log</NavLink>
            </>
          ) : (
            <>
              <NavLink to="/courses">Courses</NavLink>
              <NavLink to="/feed">Feed</NavLink>
              <NavLink to="/my-learning">My Learning</NavLink>
              <NavLink to="/interview">Interview</NavLink>
              <NavLink to="/hackathons">Hackathons</NavLink>
              <NavLink to="/leaderboard">Leaderboard</NavLink>
              {judging && <NavLink to="/judging">Judging</NavLink>}
            </>
          )}
        </div>
      )}

      <div className="nav-right">
        {user ? (
          <div className="nav-user">
            {progress && (
              <Link to="/leaderboard" className="nav-progress" aria-label="Your progress">
                <DailyRing done={progress.activitiesToday} goal={progress.dailyGoal} streak={progress.currentStreak} safe={progress.checkedInToday} />
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
