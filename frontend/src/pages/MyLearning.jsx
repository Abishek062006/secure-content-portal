import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { API_BASE, api } from '../api';
import { LearningCard } from '../components/CourseCard';
import Icon from '../components/Icon';

const TABS = [
  { key: 'all', label: 'All courses' },
  { key: 'progress', label: 'In progress' },
  { key: 'done', label: 'Completed' },
  { key: 'certificates', label: 'Certifications' },
];

function Stat({ value, label }) {
  return (
    <div className="ml-stat">
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

export default function MyLearning() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState('all');

  useEffect(() => {
    api.get('/api/me/learning').then(setItems).catch(() => setItems([])).finally(() => setLoading(false));
  }, []);

  const completed = items.filter((i) => i.course.progressPercent === 100);
  const inProgress = items.filter((i) => i.course.progressPercent < 100);
  const certificates = items.filter((i) => i.certificate);
  const shown = tab === 'progress' ? inProgress : tab === 'done' ? completed : items;

  return (
    <div className="ml-page">
      <div className="ml-hero">
        <div className="ml-hero-inner">
          <h1>My learning</h1>
          <nav className="ml-tabs" role="tablist">
            {TABS.map((t) => (
              <button key={t.key} type="button" role="tab" aria-selected={tab === t.key} className={tab === t.key ? 'active' : ''} onClick={() => setTab(t.key)}>{t.label}</button>
            ))}
          </nav>
        </div>
      </div>

      <div className="container-wide ml-body">
        {!loading && items.length > 0 && tab !== 'certificates' && (
          <section className="ml-summary">
            <div className="ml-summary-text">
              <h2>{inProgress.length > 0 ? 'Keep the momentum going' : 'Nice work — everything is complete'}</h2>
              <p>{inProgress.length > 0 ? `${inProgress.length} course${inProgress.length === 1 ? '' : 's'} in progress. Pick one and carry on.` : 'Browse the catalog for your next course.'}</p>
            </div>
            <div className="ml-stats">
              <Stat value={items.length} label="Enrolled" />
              <Stat value={inProgress.length} label="In progress" />
              <Stat value={completed.length} label="Completed" />
              <Stat value={certificates.length} label="Certificates" />
            </div>
          </section>
        )}

        {!loading && items.length === 0 && (
          <div className="empty-state">
            <p>You haven't enrolled in any courses yet.</p>
            <Link className="btn btn-primary" to="/courses">Browse courses</Link>
          </div>
        )}

        {tab !== 'certificates' && items.length > 0 && (
          shown.length === 0
            ? <div className="empty-state"><p>{tab === 'done' ? 'No completed courses yet.' : 'Nothing in progress.'}</p></div>
            : <div className="cat-grid ml-grid">{shown.map((item) => <LearningCard key={item.course.id} item={item} />)}</div>
        )}

        {tab === 'certificates' && (
          certificates.length === 0
            ? <div className="empty-state"><p>Finish a course and claim its certificate to see it here.</p></div>
            : (
              <div className="certificate-list">
                {certificates.map(({ certificate }) => (
                  <div className="certificate-item" key={certificate.id}>
                    <div className="cert-row-icon"><Icon name="award" size={26} /></div>
                    <div className="cert-row-body">
                      <strong>{certificate.courseTitle}</strong>
                      <div className="muted">Issued {new Date(certificate.issuedAt).toLocaleDateString()} · Credential ID <code>{certificate.code}</code></div>
                    </div>
                    <a className="btn" href={`${API_BASE}/api/certificates/${certificate.id}/pdf`}>Download PDF</a>
                  </div>
                ))}
              </div>
            )
        )}
      </div>
    </div>
  );
}
