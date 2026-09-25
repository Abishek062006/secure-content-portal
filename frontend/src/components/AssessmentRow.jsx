import { Link } from 'react-router-dom';

function describe(a) {
  const parts = [`${a.questionCount} question${a.questionCount === 1 ? '' : 's'}`];
  if (a.type === 'ASSESSMENT') {
    parts.push(`pass mark ${a.passPercent}%`);
    if (a.timeLimitMinutes) parts.push(`${a.timeLimitMinutes} min`);
    if (a.maxAttempts) parts.push(a.attemptsLeft != null ? `${a.attemptsLeft} of ${a.maxAttempts} attempts left` : `${a.maxAttempts} attempts`);
  } else {
    parts.push('practice, unlimited tries');
  }
  return parts.join(' · ');
}

const ACTION = { AVAILABLE: 'Start', IN_PROGRESS: 'Resume', PASSED: 'View', EXHAUSTED: 'View' };

/** A module's (or the course's final) quiz or assessment, with the learner's standing on it. */
export default function AssessmentRow({ courseId, assessment, canOpen }) {
  const locked = assessment.status === 'LOCKED';
  const label = assessment.type === 'QUIZ' ? 'Quiz' : 'Assessment';
  const action = assessment.status === 'AVAILABLE' && assessment.attemptsUsed > 0 ? 'Retake' : ACTION[assessment.status];

  return (
    <div className={`assessment-row${locked ? ' locked' : ''}`}>
      <div>
        <div className="assessment-row-title">
          <span className={`badge ${assessment.type === 'QUIZ' ? '' : 'admin'}`}>{label}</span>
          <strong>{assessment.title}</strong>
          {assessment.passed && <span className="badge status-published">Passed</span>}
          {assessment.gatesNext && <span className="badge">Required to continue</span>}
        </div>
        <p className="field-hint">
          {describe(assessment)}
          {assessment.bestScore != null && ` · best score ${assessment.bestScore}%`}
        </p>
        {locked && assessment.lockedReason && <p className="field-hint">{assessment.lockedReason}</p>}
      </div>
      {canOpen && !locked && action && (
        <Link className="btn btn-primary" to={`/courses/${courseId}/assessments/${assessment.id}`}>{action}</Link>
      )}
    </div>
  );
}
