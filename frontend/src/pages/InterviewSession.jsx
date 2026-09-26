import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import Alert from '../components/Alert';
import Icon from '../components/Icon';
import { CATEGORY, READINESS, RUBRIC, goalLabel, rubricAverages } from '../lib/interview';

const MIN_ANSWER = 10;
const MAX_ANSWER = 4000;

function useElapsed(resetKey) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    setSeconds(0);
    const timer = setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => clearInterval(timer);
  }, [resetKey]);
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}

export default function InterviewSession() {
  const { id } = useParams();
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(() => api.getInterview(id).then(setDetail).catch((err) => setError(err.message)), [id]);
  useEffect(() => { load(); }, [load]);

  if (error && !detail) {
    return <div className="container"><Alert error={error} /><Link className="btn" to="/interview">Back to interview practice</Link></div>;
  }
  if (!detail) return <div className="container"><p className="pdf-loading">Loading...</p></div>;

  const { session, questions } = detail;
  if (session.status === 'COMPLETED') return <Report session={session} questions={questions} />;
  if (session.status === 'ABANDONED') {
    return (
      <div className="container">
        <div className="empty-state">
          <p>This interview was closed when you started a newer one.</p>
          <Link className="btn btn-primary" to="/interview">Back to interview practice</Link>
        </div>
      </div>
    );
  }
  return <Running session={session} questions={questions} reload={load} />;
}

/** One question at a time, in a calm full-width layout: the question, a box for the answer, then the feedback. */
function Running({ session, questions, reload }) {
  const next = questions.find((q) => !q.answeredAt);
  const [justAnswered, setJustAnswered] = useState(null);
  const [answer, setAnswer] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const boxRef = useRef(null);
  const elapsed = useElapsed(next?.id);

  useEffect(() => { boxRef.current?.focus(); }, [next?.id]);

  const position = next ? questions.indexOf(next) + 1 : questions.length;

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await api.answerInterview(session.id, next.id, answer);
      setJustAnswered({ question: res.question, followUp: res.followUp });
      setAnswer('');
      await reload();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function finish() {
    setBusy(true);
    setError(null);
    try {
      await api.completeInterview(session.id);
      await reload();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  const tooShort = answer.trim().length < MIN_ANSWER;

  return (
    <div className="interview-stage">
      <Alert error={error} />
      <header className="interview-top">
        <Link to="/interview" className="interview-exit"><Icon name="arrow-left" size={16} /> Leave</Link>
        <span className="interview-goal">{goalLabel(session)}</span>
        <span className="interview-clock"><Icon name="timer" size={15} /> {elapsed}</span>
      </header>

      <div className="interview-steps" aria-label={`Question ${position} of ${questions.length}`}>
        {questions.map((q, i) => <span key={q.id} className={q.answeredAt ? 'done' : q === next ? 'current' : ''} aria-hidden="true" data-i={i} />)}
      </div>

      {justAnswered ? (
        <Feedback result={justAnswered} last={!next} onNext={() => setJustAnswered(null)} onFinish={finish} busy={busy} />
      ) : next ? (
        <form className="interview-question" onSubmit={submit}>
          <p className="interview-count">
            Question {position} of {questions.length}
            {next.parentQuestionId && <span className="badge">Follow-up</span>}
            <span className="badge">{CATEGORY[next.category] || next.category}</span>
          </p>
          <h1>{next.questionText}</h1>
          <textarea ref={boxRef} rows={9} maxLength={MAX_ANSWER} value={answer} onChange={(e) => setAnswer(e.target.value)}
                    placeholder="Answer as you would in the room. Explain your reasoning." aria-label="Your answer" />
          <div className="interview-answer-foot">
            <span className="field-hint">{answer.length}/{MAX_ANSWER}</span>
            <button type="submit" className="btn btn-primary btn-lg" disabled={busy || tooShort}>
              {busy ? 'Assessing your answer...' : 'Submit answer'}
            </button>
          </div>
        </form>
      ) : (
        <div className="interview-question">
          <h1>You've answered every question.</h1>
          <button type="button" className="btn btn-primary btn-lg" onClick={finish} disabled={busy}>
            {busy ? 'Preparing your report...' : 'See your report'}
          </button>
        </div>
      )}
    </div>
  );
}

function Feedback({ result, last, onNext, onFinish, busy }) {
  const { question, followUp } = result;
  return (
    <div className="interview-feedback">
      <p className="interview-score"><strong>{question.score}</strong><span>/ 10</span></p>
      <p>{question.aiFeedback}</p>
      {question.keyStrengths && <p><strong>Went well.</strong> {question.keyStrengths}</p>}
      {question.areasToImprove && <p><strong>Work on.</strong> {question.areasToImprove}</p>}
      {followUp && <p className="interview-followup-note">The interviewer has a follow-up question for you.</p>}
      {last ? (
        <button type="button" className="btn btn-primary btn-lg" onClick={onFinish} disabled={busy}>
          {busy ? 'Preparing your report...' : 'See your report'}
        </button>
      ) : (
        <button type="button" className="btn btn-primary btn-lg" onClick={onNext}>Next question</button>
      )}
    </div>
  );
}

function Report({ session, questions }) {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const rubric = rubricAverages(questions);
  const scoreLabel = READINESS[session.readinessLevel];

  async function again() {
    setBusy(true);
    try {
      const next = await api.retryInterview(session.id);
      navigate(`/interview/${next.id}`);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <div className="container">
      <Alert error={error} />
      <p className="interview-goal">{goalLabel(session)}</p>
      <h1 className="page-title">Your interview report</h1>

      <section className="progress-card interview-report-head">
        <div className="report-score"><strong>{session.overallScore}</strong><span>%</span></div>
        <div>
          <h2>{scoreLabel}</h2>
          <p>{session.summaryFeedback}</p>
          {session.xpEarned > 0 && <p className="field-hint">+{session.xpEarned} XP for finishing</p>}
        </div>
      </section>

      <section className="progress-card">
        <header><h2>How you did</h2></header>
        <ul className="rubric">
          {rubric.map((r) => (
            <li key={r.key}>
              <span>{r.label}</span>
              <div className="rubric-bar" role="img" aria-label={`${r.label} ${r.average ? r.average.toFixed(1) : 'not scored'} out of 10`}>
                <div style={{ width: `${r.average ? r.average * 10 : 0}%` }} />
              </div>
              <strong>{r.average ? r.average.toFixed(1) : '-'}</strong>
            </li>
          ))}
        </ul>
        {session.topFix && (
          <p className="report-fix"><strong>Work on this first.</strong> {session.topFix}</p>
        )}
        {session.courseId && (
          <Link className="btn btn-sm" to={`/courses/${session.courseId}`}>Review this course</Link>
        )}
      </section>

      <section className="progress-card">
        <header><h2>Question by question</h2></header>
        {questions.map((q, i) => (
          <details className="report-question" key={q.id}>
            <summary>
              <span className="report-q-score">{q.score}/10</span>
              <span>{q.parentQuestionId ? 'Follow-up: ' : `${i + 1}. `}{q.questionText}</span>
            </summary>
            <div className="report-q-body">
              <p><strong>Your answer.</strong> {q.learnerAnswer}</p>
              <p><strong>Feedback.</strong> {q.aiFeedback}</p>
              {q.keyStrengths && <p><strong>Went well.</strong> {q.keyStrengths}</p>}
              {q.areasToImprove && <p><strong>Work on.</strong> {q.areasToImprove}</p>}
              {q.idealAnswer && <p><strong>A stronger answer.</strong> {q.idealAnswer}</p>}
            </div>
          </details>
        ))}
      </section>

      <div className="form-actions">
        <Link className="btn" to="/interview">All interviews</Link>
        <button type="button" className="btn btn-primary" onClick={again} disabled={busy}>Practise again</button>
      </div>
    </div>
  );
}
