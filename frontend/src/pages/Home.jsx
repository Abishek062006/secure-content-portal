import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LockIcon, ShieldPlayIcon, UsersGearIcon } from '../components/Icons';

const FEATURES = [
  {
    Icon: LockIcon,
    title: 'Google sign-in only',
    body: "No passwords to manage or leak — access is tied to your organization's Google account, with roles decided server-side, never by the client.",
  },
  {
    Icon: ShieldPlayIcon,
    title: "Stream, don't download",
    body: 'Video, PDF and HTML content is served through short-lived, session-bound tickets — never a public or saveable file link.',
  },
  {
    Icon: UsersGearIcon,
    title: 'Built for teams',
    body: 'Admins upload and manage content, track views, and audit every change. Viewers just browse and watch — no clutter.',
  },
];

export default function Home() {
  const { user, loading } = useAuth();

  // Signed-in members land on the feed; the welcome page is only for visitors.
  if (loading) return null;
  if (user) return <Navigate to="/feed" replace />;

  if (!user) {
    return (
      <>
        <div className="container hero">
          <p className="hero-eyebrow">GradientNovaAI</p>
          <h1 className="hero-title">Learn, practise,<br />and share your progress.</h1>
          <p className="hero-sub">
            Courses, quizzes, certificates and a community feed — sign in with Google to start learning.
          </p>
          <Link className="btn btn-primary btn-lg" to="/login">Sign in with Google</Link>
        </div>

        <div className="container-wide features-section">
          <div className="feature-grid">
            {FEATURES.map(({ Icon, title, body }) => (
              <div className="feature-card" key={title}>
                <div className="feature-icon"><Icon /></div>
                <h3>{title}</h3>
                <p>{body}</p>
              </div>
            ))}
          </div>
        </div>
      </>
    );
  }

  return (
    <div className="container">
      <div className="panel">
        <h1 style={{ marginTop: 0 }}>Welcome, {user.displayName}</h1>
        {user.admin ? (
          <div className="home-actions">
            <Link className="btn btn-primary" to="/courses">Browse courses</Link>
            <Link className="btn" to="/admin/courses">Manage courses</Link>
            <Link className="btn" to="/feed">Feed</Link>
          </div>
        ) : (
          <div className="home-actions">
            <p>Pick up where you left off, or find something new to learn.</p>
            <Link className="btn btn-primary" to="/courses">Browse courses</Link>
            <Link className="btn" to="/my-learning">My learning</Link>
          </div>
        )}
      </div>
    </div>
  );
}
