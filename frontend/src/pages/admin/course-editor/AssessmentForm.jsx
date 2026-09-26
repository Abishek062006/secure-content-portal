import { useState } from 'react';
import { Link } from 'react-router-dom';

const DEFAULT_TITLE = { QUIZ: 'Module quiz', ASSESSMENT: 'Module assessment' };

function toNumber(value) {
  return value === '' || value == null ? null : Number(value);
}

/** Set up a quiz (practice) or an assessment (graded) and how many questions of each difficulty it draws. */
export default function AssessmentForm({ initial, available, availableNew, questionsPath, allowGate, isFinal, onSubmit, onCancel }) {
  const [type, setType] = useState(initial?.type || 'QUIZ');
  const [title, setTitle] = useState(initial?.title || (isFinal ? 'Final assessment' : DEFAULT_TITLE.QUIZ));
  const [counts, setCounts] = useState({
    easy: initial?.easyCount ?? 3, medium: initial?.mediumCount ?? 3, hard: initial?.hardCount ?? 1,
  });
  const [passPercent, setPassPercent] = useState(initial?.passPercent ?? 70);
  const [timeLimit, setTimeLimit] = useState(initial?.timeLimitMinutes ?? '');
  const [maxAttempts, setMaxAttempts] = useState(initial?.maxAttempts ?? '');
  const [reuse, setReuse] = useState(initial?.reusePercent ?? 50);
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
        reusePercent: isFinal ? Number(reuse) : null,
      });
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  const row = (key, label) => {
    const have = available?.[key.toUpperCase()] ?? 0;
    const fresh = availableNew?.[key.toUpperCase()] ?? 0;
    const wanted = Number(counts[key]) || 0;
    const fromOld = Math.round((wanted * reuse) / 100);
    const fromNew = wanted - fromOld;
    if (isFinal) {
      const short = fromOld > have || fromNew > fresh;
      return (
        <label className="count-field">
          {label}
          <input type="number" min={0} max={100} value={counts[key]} onChange={(e) => setCounts({ ...counts, [key]: e.target.value })} />
          <span className={`field-hint${short ? ' warn' : ''}`}>
            {fromOld} earlier ({have} available) · {fromNew} new ({fresh} available)
          </span>
        </label>
      );
    }
    return (
      <label className="count-field">
        {label}
        <input type="number" min={0} max={100} value={counts[key]} onChange={(e) => setCounts({ ...counts, [key]: e.target.value })} />
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
      {isFinal && (
        <div className="field">
          <label htmlFor="reuse">
            Mix: <strong>{reuse}%</strong> from earlier questions, <strong>{100 - reuse}%</strong> new questions
          </label>
          <input id="reuse" className="level-slider" type="range" min={0} max={100} step={5} value={reuse}
                 onChange={(e) => setReuse(Number(e.target.value))} />
          <div className="level-ticks reuse-ticks" aria-hidden="true"><span>All new</span><span>Half and half</span><span>All earlier</span></div>
          <p className="field-hint">
            "Earlier" means the course's regular questions, the ones module quizzes draw from. "New" means questions
            you saved as <em>final assessment only</em>; add them in the{' '}
            {questionsPath ? <Link to={questionsPath}>question bank</Link> : 'question bank'} with AI, by hand or a CSV.
            If there aren't enough of one kind, the other fills the gap.
          </p>
        </div>
      )}
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
