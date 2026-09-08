export default function Alert({ success, error }) {
  if (!success && !error) return null;
  return (
    <div className="alerts-wrap">
      {success && <div className="alert alert-success">{success}</div>}
      {error && <div className="alert alert-error">{error}</div>}
    </div>
  );
}
