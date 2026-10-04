import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import Alert from '../components/Alert';
import CodeBlock from '../components/CodeBlock';

const LETTERS = ['A', 'B', 'C', 'D'];

const answered = (q) => q.selectedIndex != null || Boolean(q.typedAnswer);

/** The text box for a question the learner answers by typing. A quiz checks it on request; an assessment saves it when they click away. */
function TypedAnswer({ question, practice, onSave, onDraft }) {
  const [text, setText] = useState(question.typedAnswer || '');
  const locked = practice && question.correct != null;
  const value = text.trim();
  const saved = value !== '' && value === (question.typedAnswer || '');
  const fill = question.type === 'FILL_CODE';

  function change(e) {
    setText(e.target.value);
    onDraft(question.id, e.target.value);
  }

  return (
    <div className="typed-answer">
      <label className="typed-answer-label" htmlFor={`answer-${question.id}`}>
        {fill ? 'Type the code that goes in the blank' : 'Type the output'}
      </label>
      <textarea id={`answer-${question.id}`} className="typed-answer-input" rows={fill ? 2 : 3} maxLength={1000}
                spellCheck={false} autoCapitalize="off" autoCorrect="off" value={text} disabled={locked}
                onChange={change}
                onBlur={practice ? undefined : () => { if (value && !saved) onSave(question, text); }} />
      <div className="typed-answer-actions">
        {practice ? (
          <button type="button" className="btn btn-sm" disabled={locked || !value} onClick={() => onSave(question, text)}>
            Check answer
          </button>
        ) : (
          <span className="field-hint" aria-live="polite">{saved ? 'Saved' : value ? 'Saves when you click away' : ''}</span>
        )}
      </div>
    </div>
  );
}

function clock(totalSeconds) {
  const m = Math.floor(totalSeconds / 60);
  return `${m}:${String(totalSeconds % 60).padStart(2, '0')}`;
}

/** Taking an attempt (answers save as you go, the server keeps the clock) and, once submitted, reviewing it. */
export default function AttemptView() {
  const { attemptId } = useParams();
  const [attempt, setAttempt] = useState(null);
  const [error, setError] = useState(null);
  const [remaining, setRemaining] = useState(null);
  const [confirming, setConfirming] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const deadline = useRef(null);
  const autoSubmitted = useRef(false);
  // What's been typed into answer boxes but not saved yet, so submitting (or the clock running out) doesn't lose it.
  const drafts = useRef({});

  const apply = useCallback((res) => {
    setAttempt(res);
    deadline.current = res.secondsLeft != null ? Date.now() + res.secondsLeft * 1000 : null;
  }, []);

  useEffect(() => {
    api.get(`/api/attempts/${attemptId}`).then(apply).catch((err) => setError(err.message));
  }, [attemptId, apply]);

  const submit = useCallback(async () => {
    setSubmitting(true);
    setConfirming(null);
    try {
      const pending = Object.entries(drafts.current).filter(([, text]) => text.trim());
      drafts.current = {};
      // A save can fail when time is already up; the submit below still grades what was saved.
      await Promise.all(pending.map(([questionId, typedAnswer]) =>
        api.put(`/api/attempts/${attemptId}/answers`, { questionId, typedAnswer: typedAnswer.trim() }).catch(() => {})));
      apply(await api.post(`/api/attempts/${attemptId}/submit`));
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }, [attemptId, apply]);

  const running = attempt?.status === 'IN_PROGRESS';
  useEffect(() => {
    if (!running || deadline.current == null) return undefined;
    const tick = () => {
      const left = Math.max(0, Math.ceil((deadline.current - Date.now()) / 1000));
      setRemaining(left);
      if (left === 0 && !autoSubmitted.current) {
        autoSubmitted.current = true;
        submit();
      }
    };
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [running, attempt?.expiresAt, submit]);

  async function choose(question, optionIndex) {
    setAttempt((prev) => ({
      ...prev,
      questions: prev.questions.map((q) => (q.id === question.id ? { ...q, selectedIndex: optionIndex } : q)),
    }));
    try {
      const res = await api.put(`/api/attempts/${attemptId}/answers`, { questionId: question.id, optionIndex });
      setAttempt(res);
    } catch (err) {
      setError(err.message);
      api.get(`/api/attempts/${attemptId}`).then(apply).catch(() => {});
    }
  }

  async function saveTyped(question, text) {
    try {
      const res = await api.put(`/api/attempts/${attemptId}/answers`, { questionId: question.id, typedAnswer: text.trim() });
      delete drafts.current[question.id];
      setAttempt(res);
    } catch (err) {
      setError(err.message);
    }
  }

  function draft(questionId, text) {
    drafts.current[questionId] = text;
  }

  if (error && !attempt) {
    return <div className="container"><p className="field-error">{error}</p></div>;
  }
  if (!attempt) {
    return <div className="container"><p className="pdf-loading">Loading…</p></div>;
  }

  const practice = attempt.type === 'QUIZ';
  const answeredCount = attempt.questions.filter(answered).length;
  // Typed answers not saved yet still count: submitting saves them first.
  const countUnanswered = () => attempt.questions.filter((q) => !answered(q) && !(drafts.current[q.id] || '').trim()).length;

  if (!running) {
    return (
      <div className="container">
        <p className="breadcrumb"><Link to={`/courses/${attempt.courseId}/assessments/${attempt.assessmentId}`}>← {attempt.title}</Link></p>
        <div className={`result-card${attempt.passed === true ? ' passed' : attempt.passed === false ? ' failed' : ''}`}>
          <div className="result-score">{attempt.scorePercent}%</div>
          <div>
            <h1>{attempt.passed === true ? 'Passed' : attempt.passed === false ? 'Not passed' : 'Quiz complete'}</h1>
            <p className="field-hint">
              {attempt.correctCount} of {attempt.totalCount} correct
              {attempt.passPercent != null && ` · pass mark ${attempt.passPercent}%`}
              {attempt.timedOut && ' · submitted automatically when time ran out'}
            </p>
            <div className="row-actions">
              <Link className="btn btn-primary" to={`/courses/${attempt.courseId}/assessments/${attempt.assessmentId}`}>
                {attempt.passed === false ? 'Try again' : 'Back to the assessment'}
              </Link>
              <Link className="btn" to={`/courses/${attempt.courseId}`}>Back to the course</Link>
            </div>
          </div>
        </div>

        <h2 className="editor-heading">Review</h2>
        {attempt.questions.map((q, index) => (
          <article className="question-card" key={q.id}>
            <p className="question-text">{index + 1}. {q.text}</p>
            <CodeBlock code={q.codeSnippet} />
            {q.typed ? (
              <div className="typed-review">
                <p className={`typed-review-line ${q.correct ? 'correct' : 'wrong'}`}>
                  <span className="typed-review-label">Your answer</span>
                  <code>{q.typedAnswer || 'No answer'}</code>
                </p>
                {!q.correct && (
                  <p className="typed-review-line correct">
                    <span className="typed-review-label">Correct answer</span>
                    <code>{q.correctAnswer}</code>
                  </p>
                )}
              </div>
            ) : (
              <ol className="question-options">
                {q.options.map((option, i) => {
                  const isCorrect = i === q.correctIndex;
                  const isMine = i === q.selectedIndex;
                  return (
                    <li key={LETTERS[i]} className={isCorrect ? 'correct' : isMine ? 'wrong' : ''}>
                      <span className="option-letter">{LETTERS[i]}</span>
                      {option}
                      {isCorrect && <span className="option-tick">Correct answer</span>}
                      {isMine && !isCorrect && <span className="option-tick">Your answer</span>}
                    </li>
                  );
                })}
              </ol>
            )}
            {!answered(q) && <p className="field-hint">You didn't answer this one.</p>}
            {q.explanation && <p className="field-hint">Why: {q.explanation}</p>}
            {q.lessonId && (
              <Link className="btn" to={`/courses/${attempt.courseId}/lessons/${q.lessonId}${q.sourceSeconds != null ? `?t=${q.sourceSeconds}` : ''}`}>
                Review this in the lesson
              </Link>
            )}
          </article>
        ))}
      </div>
    );
  }

  return (
    <div className="container">
      <Alert error={error} />
      <div className="attempt-head">
        <div>
          <p className="breadcrumb"><Link to={`/courses/${attempt.courseId}/assessments/${attempt.assessmentId}`}>← {attempt.title}</Link></p>
          <h1>{attempt.title}</h1>
        </div>
        {remaining != null && (
          <div className={`timer${remaining <= 60 ? ' low' : ''}`} role="timer" aria-label="Time left">{clock(remaining)}</div>
        )}
      </div>
      <p className="field-hint">{answeredCount} of {attempt.questions.length} answered. Your answers are saved as you go.</p>

      {attempt.questions.map((q, index) => {
        const revealed = q.correct != null;
        return (
          <article className="question-card" key={q.id}>
            <p className="question-text">{index + 1}. {q.text}</p>
            <CodeBlock code={q.codeSnippet} />
            {q.typed ? (
              <TypedAnswer question={q} practice={practice} onSave={saveTyped} onDraft={draft} />
            ) : (
              <ol className="question-options selectable">
                {q.options.map((option, i) => {
                  const chosen = q.selectedIndex === i;
                  const cls = revealed ? (i === q.correctIndex ? 'correct' : chosen ? 'wrong' : '') : chosen ? 'chosen' : '';
                  return (
                    <li key={LETTERS[i]} className={cls}>
                      <button type="button" disabled={practice && revealed} onClick={() => choose(q, i)}>
                        <span className="option-letter">{LETTERS[i]}</span>
                        {option}
                      </button>
                    </li>
                  );
                })}
              </ol>
            )}
            {revealed && (
              <p className={`feedback ${q.correct ? 'right' : 'wrong'}`}>
                {q.correct ? 'Correct.' : 'Not quite.'}
                {q.typed && !q.correct && <> The answer is <code>{q.correctAnswer}</code>.</>} {q.explanation}
              </p>
            )}
          </article>
        );
      })}

      {confirming != null ? (
        <div className="alert alert-error">
          You have {confirming} unanswered question{confirming === 1 ? '' : 's'}; they'll count as wrong.
          <div className="row-actions">
            <button type="button" className="btn" onClick={() => setConfirming(null)}>Keep working</button>
            <button type="button" className="btn btn-primary" onClick={submit} disabled={submitting}>Submit anyway</button>
          </div>
        </div>
      ) : (
        <div className="form-actions">
          <button type="button" className="btn btn-primary btn-lg" disabled={submitting}
                  onClick={() => {
                    const left = countUnanswered();
                    if (left > 0) setConfirming(left);
                    else submit();
                  }}>
            {submitting ? 'Submitting…' : 'Submit answers'}
          </button>
        </div>
      )}
    </div>
  );
}
