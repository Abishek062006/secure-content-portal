import { useState } from 'react';
import AssessmentForm from './AssessmentForm';

function summary(a) {
  const parts = [`${a.questionCount} question${a.questionCount === 1 ? '' : 's'} (${a.easyCount} easy, ${a.mediumCount} medium, ${a.hardCount} hard)`];
  if (a.reusePercent != null) parts.push(`${a.reusePercent}% earlier / ${100 - a.reusePercent}% new`);
  if (a.type === 'ASSESSMENT') {
    parts.push(`pass mark ${a.passPercent}%`);
    parts.push(a.timeLimitMinutes ? `${a.timeLimitMinutes} min` : 'untimed');
    parts.push(a.maxAttempts ? `${a.maxAttempts} attempt${a.maxAttempts === 1 ? '' : 's'}` : 'unlimited attempts');
    if (a.gatesNext) parts.push('required to continue');
  }
  return parts.join(' · ');
}

/** A module's (or the course's final) quiz or assessment: skip it, or set it up. */
export default function AssessmentPanel({ assessment, available, availableNew, questionsPath, allowGate, isFinal, flat, onSave, onRemove }) {
  const [editing, setEditing] = useState(false);

  return (
    <div className="module-assessment">
      {editing ? (
        <AssessmentForm
          initial={assessment}
          available={available}
          availableNew={availableNew}
          questionsPath={questionsPath}
          allowGate={allowGate}
          isFinal={isFinal}
          onCancel={() => setEditing(false)}
          onSubmit={async (values) => {
            await onSave(values);
            setEditing(false);
          }}
        />
      ) : assessment ? (
        <div className="module-assessment-row">
          <div>
            <span className={`badge ${assessment.type === 'QUIZ' ? '' : 'admin'}`}>{assessment.type === 'QUIZ' ? 'Quiz' : 'Assessment'}</span>
            {' '}<strong>{assessment.title}</strong>
            <p className="field-hint">{summary(assessment)}</p>
          </div>
          <div className="row-actions">
            <button type="button" className="btn" onClick={() => setEditing(true)}>Edit</button>
            <button type="button" className="btn btn-danger-outline" onClick={onRemove}>Remove</button>
          </div>
        </div>
      ) : (
        <div className="module-assessment-row">
          <span className="field-hint">{isFinal ? 'No final assessment.' : flat ? 'No quiz yet. Learners will just watch the video(s).' : 'No quiz or assessment — this module is skipped.'}</span>
          <button type="button" className="btn" onClick={() => setEditing(true)}>{isFinal ? 'Add final assessment' : flat ? 'Add a quiz' : 'Add quiz or assessment'}</button>
        </div>
      )}
    </div>
  );
}
