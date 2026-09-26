import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { API_BASE, api } from '../api';
import { useAuth } from '../context/AuthContext';
import Alert from '../components/Alert';
import AssessmentRow from '../components/AssessmentRow';
import PriceTag from '../components/PriceTag';
import Icon from '../components/Icon';
import { MATERIAL_ICON, MATERIAL_LABEL } from '../lib/materials';
import CertificateCard from '../components/CertificateCard';

export default function CourseView() {
  const { id } = useParams();
  const { user } = useAuth();
  const [outline, setOutline] = useState(null);
  const [error, setError] = useState(null);
  const [enrolling, setEnrolling] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.get(`/api/courses/${id}`)
      .then((res) => {
        if (!cancelled) setOutline(res);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  async function enroll() {
    setEnrolling(true);
    try {
      setOutline(await api.post(`/api/courses/${id}/enroll`));
    } catch (err) {
      setError(err.message);
    } finally {
      setEnrolling(false);
    }
  }

  if (error && !outline) {
    return (
      <div className="container-wide">
        <p className="field-error">{error}</p>
      </div>
    );
  }
  if (!outline) {
    return (
      <div className="container-wide">
        <p className="pdf-loading">Loading…</p>
      </div>
    );
  }

  const { course, modules, enrolled, progressPercent, resumeLessonId, finalAssessment } = outline;
  const canOpen = enrolled || user?.admin;

  return (
    <div className="container-wide">
      <Alert error={error} />
      {course.status === 'DRAFT' && (
        <div className="alert alert-error">Draft — only admins can see this course.</div>
      )}

      <div className="course-hero">
        {course.thumbnailUrl && <img className="course-hero-cover" src={`${API_BASE}${course.thumbnailUrl}`} alt="" />}
        <div className="course-hero-body">
          {course.category && <span className="badge">{course.category}</span>}
          <h1>{course.title}</h1>
          {course.description && <p className="field-hint">{course.description}</p>}
          <PriceTag pricing={course.pricing} />
          <p className="field-hint">
            {course.moduleCount} module{course.moduleCount === 1 ? '' : 's'} · {course.lessonCount} lesson
            {course.lessonCount === 1 ? '' : 's'}
          </p>

          {enrolled && (
            <div className="course-progress-row">
              <div className="card-progress">
                <div className="card-progress-bar" style={{ width: `${progressPercent}%` }} />
              </div>
              <span>{progressPercent}% complete</span>
            </div>
          )}

          {resumeLessonId && canOpen ? (
            <Link className="btn btn-primary btn-lg" to={`/courses/${course.id}/lessons/${resumeLessonId}`}>
              {user?.admin ? 'Preview course' : progressPercent > 0 ? 'Continue' : 'Start course'}
            </Link>
          ) : user?.admin ? (
            <p className="field-hint">You're viewing this as an admin. Admins preview courses; learners enroll and earn certificates.</p>
          ) : (
            <button type="button" className="btn btn-primary btn-lg" onClick={enroll} disabled={enrolling || !resumeLessonId}>
              {enrolling ? 'Enrolling…' : course.pricing?.free === false ? 'Enroll' : 'Enroll — it’s free'}
            </button>
          )}
        </div>
      </div>

      {modules.length === 0 && (
        <div className="empty-state"><p>This course has no lessons yet.</p></div>
      )}

      {modules.map((module, mIndex) => {
        const open = canOpen && !module.locked;
        return (
          <section className={`outline-module${module.locked ? ' locked' : ''}`} key={module.id}>
            <h2>
              Module {mIndex + 1}: {module.title}
              {module.locked && <span className="badge lock-badge">Locked</span>}
            </h2>
            {module.locked && <p className="field-hint">{module.lockedReason}</p>}
            {module.description && <p className="field-hint">{module.description}</p>}
            <ol className="outline-lessons">
              {module.lessons.map((lesson) => (
                <li key={lesson.id}>
                  {open ? (
                    <Link to={`/courses/${course.id}/lessons/${lesson.id}`}>
                      <span className={`lesson-check${lesson.completed ? ' done' : ''}`} aria-hidden="true">{lesson.completed ? '✓' : ''}</span>
                      {lesson.title}
                    </Link>
                  ) : (
                    <span className="outline-locked">
                      <span className="lesson-check" aria-hidden="true" />
                      {lesson.title}
                    </span>
                  )}
                  {lesson.hasTranscript && <span className="badge">Transcript</span>}
                </li>
              ))}
            </ol>
            {module.materials?.length > 0 && (
              <div className="materials">
                <h3 className="materials-title">Materials</h3>
                <ul>
                  {module.materials.map((m) => (
                    <li key={m.id}>
                      <Icon name={MATERIAL_ICON[m.kind]} size={18} />
                      {open ? <Link to={`/courses/${course.id}/materials/${m.id}`}>{m.title}</Link> : <span className="outline-locked">{m.title}</span>}
                      <span className="badge">{MATERIAL_LABEL[m.kind]}</span>
                      {m.downloadable && m.kind !== 'LINK' && <span className="badge status-published">Download</span>}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {module.assessment && (
              <AssessmentRow courseId={course.id} assessment={module.assessment} canOpen={enrolled} />
            )}
          </section>
        );
      })}

      {finalAssessment && (
        <section className="outline-module">
          <h2>Final assessment</h2>
          <AssessmentRow courseId={course.id} assessment={finalAssessment} canOpen={enrolled} />
        </section>
      )}

      {enrolled && !user?.admin && <CertificateCard courseId={course.id} progressPercent={progressPercent} />}
    </div>
  );
}
