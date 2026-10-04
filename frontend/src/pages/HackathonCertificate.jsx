import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import Icon from '../components/Icon';
import Alert from '../components/Alert';
import '../styles/hackathons.css';

export default function HackathonCertificate() {
  const { id } = useParams();
  const [cert, setCert] = useState(null);
  const [hackathon, setHackathon] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    Promise.all([
      api.getHackathon(id),
      api.getHackathonCertificate(id).catch(() => null),
    ])
      .then(([event, certData]) => {
        if (!active) return;
        setHackathon(event);
        if (!certData) {
          setError('No certificate was found for your account in this hackathon. Certificates are generated for team members who submit valid projects once final results are published.');
        } else {
          setCert(certData);
        }
      })
      .catch((err) => {
        if (active) setError(err.message || 'Failed to load certificate.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [id]);

  const handleDownloadPdf = () => {
    setDownloading(true);
    try {
      api.downloadHackathonCertificatePdf(id);
    } finally {
      setTimeout(() => setDownloading(false), 1500);
    }
  };

  if (loading) {
    return (
      <div className="container" style={{ padding: '60px 0', textAlign: 'center' }}>
        <p className="muted">Fetching certificate…</p>
      </div>
    );
  }

  if (error || !cert) {
    return (
      <div className="container" style={{ padding: '40px 0', maxWidth: '640px' }}>
        <div style={{ marginBottom: '16px' }}>
          <Link to={`/hackathons/${id}`} className="interview-exit" style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
            <Icon name="arrow-left" size={16} /> Back to Hackathon
          </Link>
        </div>
        <div className="card" style={{ padding: '32px', textAlign: 'center' }}>
          <Icon name="award" size={48} style={{ color: 'var(--ink-soft)', marginBottom: '16px' }} />
          <h2 style={{ fontSize: '1.3rem', fontWeight: 700, margin: '0 0 10px' }}>Certificate Unavailable</h2>
          <p style={{ color: 'var(--ink-mid)', fontSize: '0.95rem', lineHeight: 1.5, marginBottom: '24px' }}>
            {error || 'No certificate was found for this hackathon.'}
          </p>
          <div style={{ display: 'flex', gap: '12px', justifyContent: 'center' }}>
            <Link to={`/hackathons/${id}/leaderboard`} className="btn">
              View Leaderboard
            </Link>
            <Link to={`/hackathons/${id}/workspace`} className="btn btn-primary">
              Hackathon Workspace
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const typeConfig = {
    WINNER: {
      badge: 'Winner · 1st Place',
      badgeColor: '#eab308',
      paperClass: 'type-winner',
      ribbonText: '🏆 1st Place Winner',
      description: 'for exceptional innovation, outstanding technical execution, and securing 1st Place in',
    },
    RUNNER_UP: {
      badge: `Runner-Up · Rank #${cert.rank || 2}`,
      badgeColor: '#94a3b8',
      paperClass: 'type-runner_up',
      ribbonText: `🥈 Runner-Up (Rank #${cert.rank || 2})`,
      description: `for distinguished problem solving, technical excellence, and finishing in ${cert.rank === 2 ? '2nd' : '3rd'} Place in`,
    },
    PARTICIPATION: {
      badge: 'Certificate of Participation',
      badgeColor: '#38bdf8',
      paperClass: 'type-participation',
      ribbonText: '📜 Certificate of Participation',
      description: 'for successfully building, collaborating, and submitting an innovative solution in',
    },
  };

  const currentType = typeConfig[cert.type] || typeConfig.PARTICIPATION;
  const formattedDate = cert.issuedAt
    ? new Date(cert.issuedAt).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })
    : new Date().toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' });

  return (
    <div className="container hackathon-certificate-page" style={{ padding: '24px 0 60px' }}>
      {/* Top Nav */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <Link to={`/hackathons/${id}/leaderboard`} className="interview-exit" style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
          <Icon name="arrow-left" size={16} /> Back to Leaderboard
        </Link>
        <div style={{ display: 'flex', gap: '12px' }}>
          <Link to={`/verify/${cert.code}`} target="_blank" rel="noreferrer" className="btn btn-sm">
            <Icon name="check-circle" size={14} /> Verify Online
          </Link>
          <button onClick={handleDownloadPdf} className="btn btn-sm btn-primary" disabled={downloading}>
            <Icon name="download" size={14} /> {downloading ? 'Downloading…' : 'Download PDF'}
          </button>
        </div>
      </div>

      <div className="certificate-preview-container">
        {/* Certificate Display Paper */}
        <div className={`certificate-paper ${currentType.paperClass}`}>
          <div className="cert-brand-mark">
            GradientNova AI · Verified Credential
          </div>

          <div style={{ display: 'inline-block', padding: '4px 16px', borderRadius: '999px', background: 'rgba(0,0,0,0.05)', fontSize: '0.85rem', fontWeight: 700, color: currentType.badgeColor, marginBottom: '16px' }}>
            {currentType.ribbonText}
          </div>

          <h1 className="cert-main-title">
            Certificate of {cert.type === 'WINNER' ? 'Achievement' : (cert.type === 'RUNNER_UP' ? 'Distinction' : 'Participation')}
          </h1>

          <p className="cert-sub-text">
            This certifies that
          </p>

          <div className="cert-recipient">
            {cert.recipientName}
          </div>

          <p className="cert-reason">
            {currentType.description}
            <br />
            <strong style={{ fontSize: '1.2rem', color: '#0f172a' }}>{cert.hackathonTitle}</strong>
            {cert.teamName && (
              <>
                <br />
                <span style={{ fontSize: '0.95rem', color: '#64748b' }}>Team: <strong>{cert.teamName}</strong></span>
              </>
            )}
          </p>

          <div className="cert-meta-grid">
            <div className="cert-meta-item">
              <label>Certificate ID</label>
              <code style={{ fontSize: '0.92rem', color: '#1e3a8a', fontWeight: 700 }}>
                {cert.code}
              </code>
            </div>
            <div className="cert-meta-item">
              <label>Issued Date</label>
              <span>{formattedDate}</span>
            </div>
            <div className="cert-meta-item">
              <label>Recognition</label>
              <span>{cert.type} {cert.rank ? `(#${cert.rank})` : ''}</span>
            </div>
            <div className="cert-meta-item">
              <label>Authenticity</label>
              <span style={{ color: '#059669', display: 'flex', alignItems: 'center', gap: '4px' }}>
                <Icon name="shield-check" size={14} /> Digitally Signed
              </span>
            </div>
          </div>
        </div>

        {/* Certificate Actions Bottom */}
        <div className="certificate-actions">
          <button onClick={handleDownloadPdf} className="btn btn-primary" style={{ minWidth: '180px' }} disabled={downloading}>
            <Icon name="download" size={16} /> {downloading ? 'Generating PDF…' : 'Download Certificate PDF'}
          </button>
          <Link to={`/verify/${cert.code}`} target="_blank" rel="noreferrer" className="btn" style={{ minWidth: '160px' }}>
            <Icon name="external-link" size={16} /> Verify Credential
          </Link>
          <Link to={`/hackathons/${id}/leaderboard`} className="btn">
            View Leaderboard
          </Link>
        </div>
      </div>
    </div>
  );
}
