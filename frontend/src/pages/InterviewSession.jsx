import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import Alert from '../components/Alert';
import Icon from '../components/Icon';
import VoiceAnswer from '../components/interview/VoiceAnswer';
import InterviewerStage from '../components/interview/InterviewerStage';
import RecordingGate from '../components/interview/recording/RecordingGate';
import DeliveryReport from '../components/interview/DeliveryReport';
import SelfView, { RecordingPill } from '../components/interview/recording/SelfView';
import { personaById, pick } from '../components/interview/avatar/personas';
import { useAuth } from '../context/AuthContext';
import { InterviewRecorder, startProblem } from '../lib/interviewRecorder';
import { useLocalRecording } from '../components/interview/recording/useLocalRecording';
import { formatLength, requestPersistence } from '../lib/localRecordings';
import { recordingSupported, wantsRecording } from '../lib/recordingPrefs';
import { answerDeliveryLine } from '../lib/delivery';
import { CATEGORY, READINESS, categoryAverages, goalLabel, rubricAverages, strengthsAndGaps } from '../lib/interview';

const MIN_ANSWER = 10;
const MAX_ANSWER = 4000;
const MUTE_KEY = 'gn-interviewer-muted';

/** A fresh stopwatch for one question. `thinkStart` is when the learner could begin: the question being asked, then the interviewer finishing it. */
const newTiming = (questionId) => ({
  questionId, thinkStart: performance.now(), spoke: false, heardEnd: false, firstKey: null, recordings: [],
});

const sum = (list) => list.reduce((total, value) => total + value, 0);

/**
 * How the answer was given, for feedback: how long the learner thought before starting, and for spoken answers their pace and
 * pauses. Typed answers get the thinking time only, and a spoken answer from a room too noisy to measure gets no pace or pauses.
 */
function deliveryOf(timing) {
  const voice = timing.recordings;
  const startedVoice = voice.map((r) => r.recordStart + (r.clear ? r.leadingSilenceSeconds * 1000 : 0));
  const firsts = [timing.firstKey, ...startedVoice].filter((v) => v != null);
  const thinkingSeconds = timing.thinkStart != null && firsts.length ? Math.max(0, (Math.min(...firsts) - timing.thinkStart) / 1000) : null;
  if (voice.length === 0) return { mode: 'TYPED', thinkingSeconds };

  const clear = voice.every((r) => r.clear);
  const speakingSeconds = sum(voice.map((r) => r.speakingSeconds || 0));
  const words = sum(voice.map((r) => r.words || 0));
  return {
    mode: 'VOICE',
    thinkingSeconds,
    audioClear: clear,
    speakingSeconds: clear ? speakingSeconds : null,
    wordsPerMinute: clear && speakingSeconds > 0 ? (words * 60) / speakingSeconds : null,
    longPauses: clear ? sum(voice.map((r) => r.longPauses || 0)) : null,
    longestPauseSeconds: clear ? Math.max(0, ...voice.map((r) => r.longestPauseSeconds || 0)) : null,
  };
}

function readMuted() {
  try { return localStorage.getItem(MUTE_KEY) === 'yes'; } catch { return false; }
}

function saveMuted(muted) {
  try { localStorage.setItem(MUTE_KEY, muted ? 'yes' : 'no'); } catch { /* the choice just won't be remembered */ }
}

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
  const { user } = useAuth();
  const next = questions.find((q) => !q.answeredAt);
  const [justAnswered, setJustAnswered] = useState(null);
  // Recording is the learner's choice (off unless turned on) and starts only when they press the button, never on its own.
  // Only offered at the very start: a reload part-way through can't carry on the same recording.
  const [gate, setGate] = useState(() => questions.every((q) => !q.answeredAt) && wantsRecording() && recordingSupported());
  const [rec, setRec] = useState({ status: 'off', stream: null, problem: null });
  const recorder = useRef(null);
  const timing = useRef(newTiming(null));
  const [answer, setAnswer] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [muted, setMuted] = useState(readMuted);
  const [recording, setRecording] = useState(false);
  const [line, setLine] = useState(null);
  const boxRef = useRef(null);
  const elapsed = useElapsed(gate ? 'gate' : next?.id);
  const persona = personaById(session.interviewer);
  const answeredCount = questions.filter((q) => q.answeredAt).length;

  useEffect(() => { boxRef.current?.focus(); }, [next?.id, gate]);

  // Each question starts a new stopwatch for how long the learner takes to begin.
  useEffect(() => {
    if (!gate && next && !justAnswered) timing.current = newTiming(next.id);
  }, [gate, next?.id, justAnswered]); // eslint-disable-line react-hooks/exhaustive-deps

  // The learner could begin once the interviewer finished the question. Only the first time they stop counts: stopping them again
  // by starting to record, or asking to hear it again, doesn't move it.
  function interviewerSpeaking(speaking) {
    const t = timing.current;
    if (speaking) t.spoke = true;
    else if (t.spoke && !t.heardEnd) {
      t.thinkStart = performance.now();
      t.heardEnd = true;
    }
  }

  // Whatever is saved so far is kept if the learner leaves part-way.
  useEffect(() => () => { recorder.current?.stop(); }, []);

  // Notes where each question begins, so the replay can jump to it.
  useEffect(() => {
    if (rec.status === 'on' && next && !justAnswered) {
      recorder.current?.mark('question', { questionId: next.id, text: next.questionText.slice(0, 300) });
    }
  }, [rec.status, next?.id, justAnswered]); // eslint-disable-line react-hooks/exhaustive-deps

  async function startRecording() {
    setRec({ status: 'starting', stream: null, problem: null });
    const instance = new InterviewRecorder({
      sessionId: session.id, userId: user?.id, title: goalLabel(session),
      onProblem: (kind, message) => setRec((current) => ({ ...current, problem: message })),
    });
    try {
      await instance.start();
    } catch (err) {
      setRec({ status: 'off', stream: null, problem: startProblem(err) });
      return;
    }
    requestPersistence();
    recorder.current = instance;
    setRec({ status: 'on', stream: instance.stream, problem: null });
    setGate(false);
  }

  // What the interviewer says: a hello with the first question, a natural lead-in to each next one, a reaction to each answer
  // (and a heads-up when a follow-up is coming), and a goodbye once everything is answered.
  useEffect(() => {
    if (justAnswered) {
      const { question, followUp } = justAnswered;
      const reactions = question.score >= 8 ? persona.lines.strong : question.score >= 5 ? persona.lines.fine : persona.lines.weak;
      setLine({ id: `r-${question.id}`, text: `${pick(reactions)}${followUp ? ` ${pick(persona.lines.followUp)}` : ''}` });
    } else if (next) {
      const lead = answeredCount === 0 ? pick(persona.lines.hello) : next.parentQuestionId ? '' : pick(persona.lines.next);
      setLine({ id: `q-${next.id}`, text: `${lead} ${next.questionText}`.trim() });
    } else {
      setLine({ id: 'bye', text: pick(persona.lines.bye) });
    }
  }, [justAnswered?.question?.id, next?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const mode = busy ? 'thinking' : recording || answer.trim() ? 'listening' : 'idle';

  function toggleMute() {
    setMuted((current) => {
      saveMuted(!current);
      return !current;
    });
  }

  const position = next ? questions.indexOf(next) + 1 : questions.length;

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await api.answerInterview(session.id, next.id, answer, deliveryOf(timing.current));
      recorder.current?.mark('answer', { questionId: next.id });
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
      await recorder.current?.stop();
      await reload();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  const tooShort = answer.trim().length < MIN_ANSWER;

  return (
    <div className="interview-stage">
      <Alert error={error || (rec.status === 'on' ? rec.problem : null)} />
      <header className="interview-top">
        <Link to="/interview" className="interview-exit"><Icon name="arrow-left" size={16} /> Leave</Link>
        <span className="interview-goal">{goalLabel(session)}</span>
        {rec.status === 'on' && <RecordingPill />}
        <span className="interview-clock"><Icon name="timer" size={15} /> {elapsed}</span>
      </header>

      <div className="interview-steps" aria-label={`Question ${position} of ${questions.length}`}>
        {questions.map((q, i) => <span key={q.id} className={q.answeredAt ? 'done' : q === next ? 'current' : ''} aria-hidden="true" data-i={i} />)}
      </div>

      <InterviewerStage personaId={session.interviewer} line={gate ? null : line} mode={mode} muted={muted} hush={recording}
                        onToggleMute={toggleMute} onSpeakingChange={interviewerSpeaking} />

      {gate ? (
        <RecordingGate onStart={startRecording} onSkip={() => setGate(false)} busy={rec.status === 'starting'}
                       error={rec.status === 'off' ? rec.problem : null} />
      ) : justAnswered ? (
        <Feedback result={justAnswered} last={!next} onNext={() => setJustAnswered(null)} onFinish={finish} busy={busy} />
      ) : next ? (
        <form className="interview-question" onSubmit={submit}>
          <p className="interview-count">
            Question {position} of {questions.length}
            {next.parentQuestionId && <span className="badge">Follow-up</span>}
            <span className="badge">{CATEGORY[next.category] || next.category}</span>
          </p>
          <h1>{next.questionText}</h1>
          <textarea ref={boxRef} rows={9} maxLength={MAX_ANSWER} value={answer}
                    onChange={(e) => {
                      if (timing.current.firstKey == null) timing.current.firstKey = performance.now();
                      setAnswer(e.target.value);
                    }}
                    placeholder="Answer as you would in the room. Explain your reasoning." aria-label="Your answer" />
          <VoiceAnswer sessionId={session.id} disabled={busy} onError={setError} onRecordingChange={setRecording}
                       getSharedAudio={() => recorder.current?.audioForAnswer() || null}
                       onTiming={(t) => timing.current.recordings.push(t)}
                       onText={(text) => setAnswer((current) => (current.trim() ? `${current.trim()} ${text}` : text).slice(0, MAX_ANSWER))} />
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
      {rec.status === 'on' && <SelfView stream={rec.stream} />}
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
  const { user } = useAuth();
  const recording = useLocalRecording(session.id, user?.id);
  const [trend, setTrend] = useState([]);
  useEffect(() => { api.getDeliveryTrend().then(setTrend).catch(() => setTrend([])); }, []);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const rubric = rubricAverages(questions);
  const { good, work } = strengthsAndGaps(questions);
  const byType = categoryAverages(questions);
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
      <p className="interview-goal">{goalLabel(session)} · with {personaById(session.interviewer).name}</p>
      <h1 className="page-title">Your interview report</h1>

      <section className="progress-card interview-report-head">
        <div className="report-score"><strong>{session.overallScore}</strong><span>%</span></div>
        <div>
          <h2>{scoreLabel}</h2>
          <p>{session.summaryFeedback}</p>
          {session.xpEarned > 0 && (
            <p className="field-hint">+{session.xpEarned} XP added to your <Link to="/leaderboard">leaderboard</Link> score</p>
          )}
        </div>
      </section>

      {recording && (
        <section className="progress-card replay-card">
          <div>
            <h2>Watch your interview back</h2>
            <p className="field-hint">Recorded on this device, {formatLength(recording.durationMs)} long. Only you can see it.</p>
          </div>
          <Link className="btn btn-primary" to={`/interview/${session.id}/replay`}><Icon name="play" size={16} /> Watch recording</Link>
        </section>
      )}

      <section className="progress-card">
        <header><h2>What you're good at, and what to work on</h2></header>
        <div className="report-split">
          <div>
            <h3>Good at</h3>
            <ul className="report-points good">
              {good.map((p) => <li key={p.key}><strong>{p.title}</strong><span>{p.note}</span></li>)}
            </ul>
          </div>
          <div>
            <h3>Work on</h3>
            {work.length > 0 ? (
              <ul className="report-points work">
                {work.map((p) => <li key={p.key}><strong>{p.title}</strong><span>{p.note}</span></li>)}
              </ul>
            ) : (
              <p className="field-hint">Nothing stood out as weak this time. Try a harder interview next.</p>
            )}
          </div>
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
        {byType.length > 1 && (
          <>
            <h3 className="report-subhead">By question type</h3>
            <ul className="rubric">
              {byType.map((c) => (
                <li key={c.category}>
                  <span>{c.label}</span>
                  <div className="rubric-bar" role="img" aria-label={`${c.label} ${c.average.toFixed(1)} out of 10`}>
                    <div style={{ width: `${c.average * 10}%` }} />
                  </div>
                  <strong>{c.average.toFixed(1)}</strong>
                </li>
              ))}
            </ul>
          </>
        )}
        {session.topFix && (
          <p className="report-fix"><strong>Work on this first.</strong> {session.topFix}</p>
        )}
        {session.courseId && (
          <Link className="btn btn-sm" to={`/courses/${session.courseId}`}>Review this course</Link>
        )}
      </section>

      <DeliveryReport questions={questions} trend={trend} sessionId={session.id} />

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
              {answerDeliveryLine(q) && <p className="field-hint">{answerDeliveryLine(q)}</p>}
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
