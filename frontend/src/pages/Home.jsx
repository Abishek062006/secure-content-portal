import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Home() {
  const { user } = useAuth();

  if (!user) {
    return (
      <div className="container hero">
        <p className="hero-eyebrow">Secure Content Portal</p>
        <h1 className="hero-title">Training &amp; reference,<br />shared securely.</h1>
        <p className="hero-sub">
          Video, PDF and HTML content shared with your organization — sign in with Google to
          browse the library.
        </p>
        <Link className="btn btn-primary btn-lg" to="/login">Sign in with Google</Link>
      </div>
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
