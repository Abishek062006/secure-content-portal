import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api';

export default function VerifyCertificate() {
  const { code } = useParams();
  const [result, setResult] = useState(null);

  useEffect(() => {
    api.get(`/api/certificates/verify/${encodeURIComponent(code)}`)
      .then(setResult)
      .catch(() => setResult({ valid: false }));
  }, [code]);

  return (
    <div className="container">
      <div className="page-head">
        <h1>Certificate check</h1>
      </div>
      {!result && <p className="muted">Checking…</p>}
      {result?.valid && (
        <div className="certificate-card">
          <h2>Valid certificate</h2>
          <p>
            <strong>{result.recipientName}</strong> completed <strong>{result.courseTitle}</strong> on{' '}
            {new Date(result.issuedAt).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })}.
          </p>
          <p className="muted">Certificate ID <code>{code.toUpperCase()}</code></p>
        </div>
      )}
      {result && !result.valid && (
        <div className="certificate-card">
          <h2>No certificate found</h2>
          <p>We couldn&apos;t find a certificate with the ID <code>{code}</code>. Check it for typos.</p>
        </div>
      )}
    </div>
  );
}
