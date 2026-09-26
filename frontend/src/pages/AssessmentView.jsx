import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import Alert from '../components/Alert';

function formatDate(iso) {
  return new Date(iso).toLocaleString(undefined, { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

/** The page before an attempt: the rules, the learner's history, and Start / Resume / Retake. */
export default function AssessmentView() {
  const { courseId, assessmentId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [outline, setOutline] = useState(null);
  const [history, setHistory] = useState([]);
  const [error, setError] = useState(null);
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    Promise.all([api.get(`/api/courses/${courseId}`), api.get(`/api/assessments/${assessmentId}/attempts`)])
      .then(([course, attempts]) => {
        setOutline(course);
        setHistory(attempts);
      })
      .catch((err) => setError(err.message));
  }, [courseId, assessmentId]);

  async function start() {
    setStarting(true);
    setError(null);
    try {
      const attempt = await api.post(`/api/assessments/${assessmentId}/attempts`);
      navigate(`/attempts/${attempt.id}`);
    } catch (err) {
      setError(err.message);
      setStarting(false);
    }
  }

  if (error && !outline) {
    return <div className="container"><p className="field-error">{error}</p></div>;
  }
  if (!outline) {
    return <div className="container"><p className="pdf-loading">Loading…</p></div>;
  }

  const assessment = outline.finalAssessment?.id === assessmentId
    ? outline.finalAssessment
    : outline.modules.map((m) => m.assessment).find((a) => a?.id === assessmentId);
  if (!assessment) {
    return <div className="container"><p className="field-error">This assessment doesn't exist.</p></div>;
  }

  const canStart = outline.enrolled && ['AVAILABLE', 'IN_PROGRESS', 'PASSED'].includes(assessment.status)
    && !(assessment.attemptsLeft === 0 && assessment.status !== 'IN_PROGRESS');
  const graded = assessment.type === 'ASSESSMENT';

  return (
    <div className="container">
      <Alert error={error} />
      <p className="breadcrumb"><Link to={`/courses/${courseId}`}>← {outline.course.title}</Link></p>
      <h1>{assessment.title}</h1>

      <div className="panel">
        <ul className="rules">
          <li>{assessment.questionCount} question{assessment.questionCount === 1 ? '' : 's'}, drawn at random from the course's question bank</li>
          {graded ? <li>You need <strong>{assessment.passPercent}%</strong> to pass</li> : <li>Practice quiz: you see whether each answer is right straight away</li>}
          {graded && (assessment.timeLimitMinutes
            ? <li>Time limit: <strong>{assessment.timeLimitMinutes} minutes</strong>. It is submitted automatically when time runs out.</li>
            : <li>No time limit</li>)}
          {graded && <li>{assessment.maxAttempts ? `${assessment.attemptsLeft} of ${assessment.maxAttempts} attempts left` : 'Unlimited attempts'}</li>}
          {assessment.gatesNext && <li>You must pass this to unlock the next module</li>}
        </ul>

        {assessment.status === 'LOCKED' && <p className="field-hint">{assessment.lockedReason}</p>}
        {assessment.status === 'EXHAUSTED' && <p className="field-hint">You've used all your attempts.</p>}
        {assessment.status === 'PASSED' && <p className="field-hint">You passed this with a best score of {assessment.bestScore}%.</p>}

        {canStart && (
          <button type="button" className="btn btn-primary btn-lg" onClick={start} disabled={starting}>
            {starting ? 'Starting…' : assessment.status === 'IN_PROGRESS' ? 'Resume attempt' : assessment.attemptsUsed > 0 ? 'Retake' : 'Start'}
          </button>
        )}
      </div>

      {history.length > 0 && (
        <>
          <h2 className="editor-heading">Your attempts</h2>
          <div className="table-wrap">
            <table className="data-table">
              <thead><tr><th>Started</th><th>Result</th><th></th></tr></thead>
              <tbody>
                {history.map((h) => (
                  <tr key={h.id}>
                    <td>{formatDate(h.startedAt)}</td>
                    <td>
                      {h.status === 'IN_PROGRESS' ? 'In progress'
                        : `${h.scorePercent}%${h.passed === true ? ' · passed' : h.passed === false ? ' · not passed' : ''}${h.timedOut ? ' · timed out' : ''}`}
                    </td>
                    <td className="row-actions"><Link className="btn" to={`/attempts/${h.id}`}>{h.status === 'IN_PROGRESS' ? 'Resume' : 'Review'}</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
