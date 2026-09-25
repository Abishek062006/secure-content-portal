import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { API_BASE, api } from '../api';
import { VideoIcon } from '../components/Icons';

export default function MyLearning() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get('/api/me/learning')
      .then(setItems)
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, []);

  const certificates = items.filter((i) => i.certificate);

  return (
    <div className="container-wide">
      <div className="page-head">
        <h1>My learning</h1>
      </div>

      {!loading && items.length === 0 && (
        <div className="empty-state">
          <p>You haven&apos;t enrolled in any courses yet.</p>
          <Link className="btn btn-primary" to="/courses">Browse courses</Link>
        </div>
      )}

      {items.length > 0 && (
        <div className="content-grid">
          {items.map(({ course, resumeLessonId, certificate }) => (
            <div className="content-card" key={course.id}>
              <Link to={`/courses/${course.id}`}>
                <div className={`content-card-thumb${course.thumbnailUrl ? ' has-image' : ''}`}>
                  {course.thumbnailUrl ? (
                    <img src={`${API_BASE}${course.thumbnailUrl}`} alt="" loading="lazy" />
                  ) : (
                    <>
                      <VideoIcon />
                      <span>Course</span>
                    </>
                  )}
                </div>
              </Link>
              <div className="content-card-body">
                <h2><Link to={`/courses/${course.id}`}>{course.title}</Link></h2>
                <div className="card-progress" title={`${course.progressPercent}% complete`}>
                  <div className="card-progress-bar" style={{ width: `${course.progressPercent}%` }} />
                </div>
                <div className="content-card-meta">
                  <span>{course.progressPercent}% complete</span>
                  {certificate && <span className="badge">Certified</span>}
                </div>
                <div className="learning-actions">
                  {resumeLessonId ? (
                    <Link className="btn btn-primary" to={`/courses/${course.id}/lessons/${resumeLessonId}`}>
                      {course.progressPercent === 0 ? 'Start' : course.progressPercent === 100 ? 'Review' : 'Continue'}
                    </Link>
                  ) : (
                    <Link className="btn" to={`/courses/${course.id}`}>View course</Link>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {certificates.length > 0 && (
        <>
          <h2 className="section-title">Certificates</h2>
          <div className="certificate-list">
            {certificates.map(({ certificate }) => (
              <div className="certificate-item" key={certificate.id}>
                <div>
                  <strong>{certificate.courseTitle}</strong>
                  <div className="muted">
                    Issued {new Date(certificate.issuedAt).toLocaleDateString()} · ID <code>{certificate.code}</code>
                  </div>
                </div>
                <a className="btn" href={`${API_BASE}/api/certificates/${certificate.id}/pdf`}>Download PDF</a>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
