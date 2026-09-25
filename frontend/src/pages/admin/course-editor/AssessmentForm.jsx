import { useState } from 'react';

const DEFAULT_TITLE = { QUIZ: 'Module quiz', ASSESSMENT: 'Module assessment' };

function toNumber(value) {
  return value === '' || value == null ? null : Number(value);
}

/** Set up a quiz (practice) or an assessment (graded) and how many questions of each difficulty it draws. */
export default function AssessmentForm({ initial, available, allowGate, isFinal, onSubmit, onCancel }) {
  const [type, setType] = useState(initial?.type || 'QUIZ');
  const [title, setTitle] = useState(initial?.title || (isFinal ? 'Final assessment' : DEFAULT_TITLE.QUIZ));
  const [counts, setCounts] = useState({
    easy: initial?.easyCount ?? 3, medium: initial?.mediumCount ?? 3, hard: initial?.hardCount ?? 1,
  });
  const [passPercent, setPassPercent] = useState(initial?.passPercent ?? 70);
  const [timeLimit, setTimeLimit] = useState(initial?.timeLimitMinutes ?? '');
  const [maxAttempts, setMaxAttempts] = useState(initial?.maxAttempts ?? '');
  const [gatesNext, setGatesNext] = useState(initial?.gatesNext ?? false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const graded = type === 'ASSESSMENT';

  function changeType(next) {
    setType(next);
    if (!isFinal && !initial && Object.values(DEFAULT_TITLE).includes(title)) setTitle(DEFAULT_TITLE[next]);
  }

  async function submit(e) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await onSubmit({
        type, title,
        easyCount: Number(counts.easy) || 0, mediumCount: Number(counts.medium) || 0, hardCount: Number(counts.hard) || 0,
        passPercent: graded ? toNumber(passPercent) : null,
        timeLimitMinutes: graded ? toNumber(timeLimit) : null,
        maxAttempts: graded ? toNumber(maxAttempts) : null,
        gatesNext: graded && allowGate && gatesNext,
      });
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  const row = (key, label) => {
    const have = available?.[key.toUpperCase()] ?? 0;
    return (
      <label className="count-field">
        {label}
        <input type="number" min={0} max={50} value={counts[key]} onChange={(e) => setCounts({ ...counts, [key]: e.target.value })} />
        <span className={`field-hint${Number(counts[key]) > have ? ' warn' : ''}`}>
          {have} approved{Number(counts[key]) > have ? ' — attempts will use fewer' : ''}
        </span>
      </label>
    );
  };

  return (
    <form className="inline-form assessment-form" onSubmit={submit}>
      {error && <p className="field-error">{error}</p>}
      <div className="field">
        <label>Type</label>
        <div className="type-choice">
          <label><input type="radio" name="type" checked={type === 'QUIZ'} onChange={() => changeType('QUIZ')} />
            Quiz <span className="field-hint">practice, instant feedback, unlimited tries</span></label>
          <label><input type="radio" name="type" checked={graded} onChange={() => changeType('ASSESSMENT')} />
            Assessment <span className="field-hint">graded, with a pass mark</span></label>
        </div>
      </div>
      <div className="field">
        <label>Title</label>
        <input type="text" maxLength={200} required value={title} onChange={(e) => setTitle(e.target.value)} />
      </div>
      <div className="field">
        <label>Questions drawn at random from the approved bank</label>
        <div className="counts">
          {row('easy', 'Easy')}
          {row('medium', 'Medium')}
          {row('hard', 'Hard')}
        </div>
      </div>
      {graded && (
        <>
          <div className="counts">
            <label className="count-field">Pass mark (%)
              <input type="number" min={1} max={100} required value={passPercent} onChange={(e) => setPassPercent(e.target.value)} />
            </label>
            <label className="count-field">Time limit (minutes)
              <input type="number" min={1} max={300} placeholder="none" value={timeLimit} onChange={(e) => setTimeLimit(e.target.value)} />
            </label>
            <label className="count-field">Attempts allowed
              <input type="number" min={1} max={20} placeholder="unlimited" value={maxAttempts} onChange={(e) => setMaxAttempts(e.target.value)} />
            </label>
          </div>
          {allowGate && (
            <label className="check-field">
              <input type="checkbox" checked={gatesNext} onChange={(e) => setGatesNext(e.target.checked)} />
              Learners must pass this before the next module opens
            </label>
          )}
        </>
      )}
      <div className="form-actions">
        <button type="button" className="btn" onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  );
}
