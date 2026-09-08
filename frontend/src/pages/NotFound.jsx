import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="center-screen">
      <div className="card error-card">
        <p className="error-code">404</p>
        <h1>Page not found</h1>
        <p>The page you're looking for doesn't exist or may have been moved.</p>
        <Link className="btn btn-primary" to="/">Back to home</Link>
      </div>
    </div>
  );
}
