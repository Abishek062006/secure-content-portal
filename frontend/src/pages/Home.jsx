import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Home() {
  const { user } = useAuth();

  if (!user) {
    return (
      <div className="container">
        <h1>Secure Content Portal</h1>
        <p>
          Training and reference videos, PDFs and HTML pages, shared with your organization. Sign in
          with Google to browse the library.
        </p>
        <Link className="btn btn-primary" to="/login">Sign in with Google</Link>
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
