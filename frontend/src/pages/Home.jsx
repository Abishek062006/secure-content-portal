import { Link } from 'react-router-dom';
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
  const { user } = useAuth();

  if (!user) {
    return (
      <>
        <div className="container hero">
          <p className="hero-eyebrow">Secure Content Portal</p>
          <h1 className="hero-title">Training &amp; reference,<br />shared securely.</h1>
          <p className="hero-sub">
            Video, PDF and HTML content shared with your organization — sign in with Google to
            browse the library.
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
        <p>You're signed in as <strong>{user.role}</strong>.</p>
        {user.admin ? (
          <div className="home-actions">
            <Link className="btn btn-primary" to="/library">Browse library</Link>
            <Link className="btn" to="/admin/content">Manage content</Link>
          </div>
        ) : (
          <div className="home-actions">
            <p>Browse the training and reference videos, PDFs and HTML pages shared with you.</p>
            <Link className="btn btn-primary" to="/library">Browse library</Link>
          </div>
        )}
      </div>
    </div>
  );
}
