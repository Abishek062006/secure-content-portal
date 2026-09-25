import { useEffect, useState } from 'react';
import { API_BASE, api } from '../api';

function formatDate(iso) {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' });
}

/** Shown on an enrolled learner's course page: what's left for the certificate, or the certificate itself. */
export default function CertificateCard({ courseId, progressPercent }) {
  const [status, setStatus] = useState(null);
  const [claiming, setClaiming] = useState(false);
  const [error, setError] = useState(null);

  // progressPercent changes as lessons complete elsewhere, so re-check when the outline reloads.
  useEffect(() => {
    api.get(`/api/courses/${courseId}/certificate`).then(setStatus).catch(() => setStatus(null));
  }, [courseId, progressPercent]);

  async function claim() {
    setClaiming(true);
    setError(null);
    try {
      const certificate = await api.post(`/api/courses/${courseId}/certificate`);
      setStatus({ earned: true, missing: [], certificate });
    } catch (err) {
      setError(err.message);
    } finally {
      setClaiming(false);
    }
  }

  if (!status) return null;
  const { certificate, earned, missing } = status;

  return (
    <section className="certificate-card">
      <h2>Certificate</h2>
      {certificate ? (
        <>
          <p>
            Issued to <strong>{certificate.recipientName}</strong> on {formatDate(certificate.issuedAt)}.
            Certificate ID <code>{certificate.code}</code>.
          </p>
          <a className="btn btn-primary" href={`${API_BASE}/api/certificates/${certificate.id}/pdf`}>
            Download PDF
          </a>
        </>
      ) : earned ? (
        <>
          <p>You&apos;ve finished everything this course asks for.</p>
          <button type="button" className="btn btn-primary" onClick={claim} disabled={claiming}>
            {claiming ? 'Issuing…' : 'Get your certificate'}
          </button>
        </>
      ) : (
        <>
          <p>To earn a certificate:</p>
          <ul>{missing.map((m) => <li key={m}>{m}</li>)}</ul>
        </>
      )}
      {error && <p className="form-error">{error}</p>}
    </section>
  );
}
