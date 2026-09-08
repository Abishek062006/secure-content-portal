import { Link } from 'react-router-dom';

export default function Forbidden() {
  return (
    <div className="center-screen">
      <div className="card error-card">
        <p className="error-code">403</p>
        <h1>You don't have access to this page</h1>
        <p>This area is restricted to admins. If you think that's wrong, contact your portal admin.</p>
        <Link className="btn btn-primary" to="/">Back to home</Link>
      </div>
    </div>
  );
}
