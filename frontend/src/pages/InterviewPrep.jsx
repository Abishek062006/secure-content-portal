import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import Alert from '../components/Alert';
import ScoreTrend from '../components/interview/ScoreTrend';
import ResumeCard from '../components/interview/ResumeCard';
import SkillsInput from '../components/interview/SkillsInput';
import { DIFFICULTIES, READINESS, TRACKS, goalLabel } from '../lib/interview';

export default function InterviewPrep() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const courseId = params.get('courseId');

  const [course, setCourse] = useState(null);
  const [role, setRole] = useState('');
  const [skills, setSkills] = useState([]);
  const [jobDescription, setJobDescription] = useState('');
  const [showJob, setShowJob] = useState(false);
  const [track, setTrack] = useState('STUDENT');
  const [difficulty, setDifficulty] = useState('MEDIUM');
  const [history, setHistory] = useState([]);
  const [quota, setQuota] = useState(null);
  const [resume, setResume] = useState(null);
  const [useResume, setUseResume] = useState(false);
  const [error, setError] = useState(null);
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    api.getInterviewHistory().then(setHistory).catch(() => {});
    api.getInterviewQuota().then(setQuota).catch(() => {});
    api.getResume().then((r) => { setResume(r); setUseResume(Boolean(r)); }).catch(() => {});
  }, []);

  useEffect(() => {
    if (!courseId) return;
    api.get(`/api/courses/${courseId}`).then((res) => setCourse(res.course)).catch(() => setCourse(null));
  }, [courseId]);

  const fromCourse = Boolean(courseId && course);
  const left = quota ? Math.max(0, quota.limit - quota.used) : null;

  async function begin(body) {
    setStarting(true);
    setError(null);
    try {
      const session = await body();
      navigate(`/interview/${session.id}`);
    } catch (err) {
      setError(err.message);
      setStarting(false);
    }
  }

  function submit(e) {
    e.preventDefault();
    const payload = fromCourse
      ? { track, difficulty, courseId }
      : { track, difficulty, targetRole: role, skills, jobDescription: showJob ? jobDescription : '', useResume: useResume && Boolean(resume) };
    begin(() => api.startInterview(payload));
  }

  return (
    <div className="container">
      <Alert error={error} />
      <h1 className="page-title">Interview practice</h1>
      <p className="field-hint">Answer questions built around your target role, get scored on relevance, depth, structure and communication, and see how you improve.</p>

      <form className="progress-card interview-form" onSubmit={submit}>
        {fromCourse ? (
          <p className="interview-from-course">
            Practising from <strong>{course.title}</strong>. <Link to="/interview">Set up my own instead</Link>
          </p>
        ) : (
          <>
            <div className="field"><label htmlFor="role">Role you're preparing for{resume && useResume ? ' (optional — we can read it off your resume)' : ''}</label>
              <input id="role" type="text" maxLength={100} value={role} onChange={(e) => setRole(e.target.value)}
                     placeholder="e.g. Backend developer" required={!(resume && useResume)} /></div>
            <div className="field"><label>Your skills</label>
              <SkillsInput skills={skills} onChange={setSkills} />
              <p className="field-hint">Add up to 12. The questions are built around them.</p></div>

            <div className="field interview-resume-inline">
              <label>Or use your resume</label>
              <ResumeCard resume={resume} onChange={(r) => { setResume(r); setUseResume(Boolean(r)); }} onError={setError} embedded />
              {resume && (
                <label className="board-toggle">
                  <input type="checkbox" checked={useResume} onChange={(e) => setUseResume(e.target.checked)} />
                  Build questions from my resume too
                </label>
              )}
            </div>

            {showJob ? (
              <div className="field"><label htmlFor="jd">Job description</label>
                <textarea id="jd" rows={6} maxLength={4000} value={jobDescription} onChange={(e) => setJobDescription(e.target.value)}
                          placeholder="Paste the job description you're applying to." />
                <p className="field-hint">Needed only if you didn't add skills or use your resume. {jobDescription.length}/4000</p></div>
            ) : (
              <button type="button" className="link-button" onClick={() => setShowJob(true)}>Add a job description</button>
            )}
          </>
        )}

        <div className="form-row">
          <div className="field"><label htmlFor="track">I am a</label>
            <select id="track" value={track} onChange={(e) => setTrack(e.target.value)}>
              {TRACKS.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
            </select></div>
          <div className="field"><label htmlFor="difficulty">Difficulty</label>
            <select id="difficulty" value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
              {DIFFICULTIES.map((d) => <option key={d.id} value={d.id}>{d.label}</option>)}
            </select></div>
        </div>

        <div className="form-actions interview-start">
          {left !== null && <span className="field-hint">{left} of {quota.limit} interviews left today</span>}
          <button type="submit" className="btn btn-primary btn-lg" disabled={starting || left === 0}>
            {starting ? 'Preparing your questions...' : 'Start interview'}
          </button>
        </div>
      </form>

      {history.length > 0 && (
        <section className="progress-card">
          <header><h2>Your interviews</h2></header>
          <ScoreTrend sessions={history} />
          <ul className="interview-history">
            {history.map((s) => (
              <li key={s.id}>
                <Link to={`/interview/${s.id}`} className="interview-history-main">
                  <strong>{goalLabel(s)}</strong>
                  <span className="field-hint">
                    {new Date(s.createdAt).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}
                    {' · '}
                    {s.status === 'COMPLETED' ? `${s.overallScore}% · ${READINESS[s.readinessLevel]}` : s.status === 'IN_PROGRESS' ? 'In progress' : 'Not finished'}
                  </span>
                </Link>
                <button type="button" className="btn btn-sm" disabled={starting || left === 0}
                        onClick={() => begin(() => api.retryInterview(s.id))}>Practise again</button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
