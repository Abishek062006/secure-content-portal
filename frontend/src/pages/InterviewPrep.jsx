import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import Alert from '../components/Alert';
import Icon from '../components/Icon';
import ScoreTrend from '../components/interview/ScoreTrend';
import ResumeCard from '../components/interview/ResumeCard';
import SkillsInput from '../components/interview/SkillsInput';
import { DIFFICULTIES, INTERVIEW_TYPES, MAX_SKILLS, QUESTION_COUNTS, READINESS, TRACKS, goalLabel } from '../lib/interview';

/** The skills read off the resume. In a technical interview the learner picks which ones the questions focus on. */
function ResumeKeywordList({ keywords, selected, onToggle, selectable }) {
  if (!keywords.length) {
    return <p className="field-hint">We couldn't pick out specific skills from your resume. Add the ones you want asked about below.</p>;
  }
  return (
    <div className="resume-keywords">
      <p className="resume-keywords-label">Found in your resume</p>
      <div className="keyword-chips">
        {keywords.map((keyword) => (selectable ? (
          <button type="button" key={keyword} className={`keyword-chip${selected.includes(keyword) ? ' on' : ''}`}
                  aria-pressed={selected.includes(keyword)} onClick={() => onToggle(keyword)}>
            {selected.includes(keyword) && <Icon name="check" size={12} />}{keyword}
          </button>
        ) : (
          <span key={keyword} className="keyword-chip on">{keyword}</span>
        )))}
      </div>
      <p className="field-hint">
        {selectable
          ? `Questions focus on the ${selected.length} selected. Tap a skill to leave it out.`
          : 'Your interviewer has read your resume and will ask about your own experience.'}
      </p>
    </div>
  );
}

export default function InterviewPrep() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const courseId = params.get('courseId');

  const [course, setCourse] = useState(null);
  const [interviewType, setInterviewType] = useState('TECHNICAL');
  const [role, setRole] = useState('');
  const [skills, setSkills] = useState([]);
  const [jobDescription, setJobDescription] = useState('');
  const [showJob, setShowJob] = useState(false);
  const [track, setTrack] = useState('STUDENT');
  const [difficulty, setDifficulty] = useState('MEDIUM');
  const [questionCount, setQuestionCount] = useState(5);
  const [history, setHistory] = useState([]);
  const [quota, setQuota] = useState(null);
  const [resume, setResume] = useState(null);
  const [useResume, setUseResume] = useState(false);
  const [focus, setFocus] = useState([]);
  const [error, setError] = useState(null);
  const [starting, setStarting] = useState(false);

  function resumeChanged(next) {
    setResume(next);
    setUseResume(Boolean(next));
    setFocus(next?.keywords || []);
  }

  useEffect(() => {
    api.getInterviewHistory().then(setHistory).catch(() => {});
    api.getInterviewQuota().then(setQuota).catch(() => {});
    api.getResume().then(resumeChanged).catch(() => {});
  }, []);

  useEffect(() => {
    if (!courseId) return;
    api.get(`/api/courses/${courseId}`).then((res) => setCourse(res.course)).catch(() => setCourse(null));
  }, [courseId]);

  const fromCourse = Boolean(courseId && course);
  const technical = interviewType === 'TECHNICAL';
  const resumeOn = useResume && Boolean(resume);
  const left = quota ? Math.max(0, quota.limit - quota.used) : null;

  // Focus skills from the resume first, then the ones typed in; the interview takes at most twelve.
  const combined = [...(resumeOn ? focus : []), ...skills]
    .filter((s, i, all) => all.findIndex((o) => o.toLowerCase() === s.toLowerCase()) === i);

  function toggleFocus(keyword) {
    setFocus((current) => (current.includes(keyword) ? current.filter((k) => k !== keyword) : [...current, keyword]));
  }

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
    const shared = { track, difficulty, interviewType, questionCount };
    const payload = fromCourse
      ? { ...shared, courseId }
      : {
        ...shared,
        targetRole: role,
        skills: technical ? combined.slice(0, MAX_SKILLS) : [],
        jobDescription: technical && showJob ? jobDescription : '',
        useResume: resumeOn,
      };
    begin(() => api.startInterview(payload));
  }

  return (
    <div className="container">
      <Alert error={error} />
      <h1 className="page-title">Interview practice</h1>
      <p className="field-hint">Choose a technical or an HR round, answer by typing or speaking, and get scored on every answer. Your score counts towards the leaderboard.</p>

      <form className="progress-card interview-form" onSubmit={submit}>
        <fieldset className="interview-type">
          <legend>Interview type</legend>
          <div className="interview-type-options">
            {INTERVIEW_TYPES.map((t) => (
              <label key={t.id} className={interviewType === t.id ? 'selected' : ''}>
                <input type="radio" name="interview-type" value={t.id} checked={interviewType === t.id}
                       onChange={() => setInterviewType(t.id)} />
                <span className="interview-type-name">{t.label} interview</span>
                <span className="field-hint">{t.hint}</span>
              </label>
            ))}
          </div>
        </fieldset>

        {fromCourse ? (
          <p className="interview-from-course">
            Practising from <strong>{course.title}</strong>. <Link to="/interview">Set up my own instead</Link>
          </p>
        ) : (
          <>
            <div className="field interview-resume-inline">
              <label>Your resume</label>
              <ResumeCard resume={resume} onChange={resumeChanged} onError={setError} embedded />
              {resume && (
                <label className="board-toggle">
                  <input type="checkbox" checked={useResume} onChange={(e) => setUseResume(e.target.checked)} />
                  Use my resume for this interview
                </label>
              )}
              {resumeOn && (
                <ResumeKeywordList keywords={resume.keywords || []} selected={focus} onToggle={toggleFocus} selectable={technical} />
              )}
            </div>

            <div className="field"><label htmlFor="role">Role you're preparing for{resumeOn ? ' (optional with a resume)' : ''}</label>
              <input id="role" type="text" maxLength={100} value={role} onChange={(e) => setRole(e.target.value)}
                     placeholder="e.g. Backend developer" required={!resumeOn} /></div>

            {technical && (
              <>
                <div className="field"><label>{resumeOn ? 'Other skills to cover' : 'Your skills'}</label>
                  <SkillsInput skills={skills} onChange={setSkills} />
                  <p className="field-hint">
                    {combined.length > MAX_SKILLS
                      ? `The interview covers the first ${MAX_SKILLS} of your ${combined.length} skills.`
                      : resumeOn ? 'Optional. Add anything your resume leaves out.' : 'Add up to 12. The questions are built around them.'}
                  </p></div>

                {showJob ? (
                  <div className="field"><label htmlFor="jd">Job description</label>
                    <textarea id="jd" rows={6} maxLength={4000} value={jobDescription} onChange={(e) => setJobDescription(e.target.value)}
                              placeholder="Paste the job description you're applying to." />
                    <p className="field-hint">Optional. {jobDescription.length}/4000</p></div>
                ) : (
                  <button type="button" className="link-button" onClick={() => setShowJob(true)}>Add a job description</button>
                )}
              </>
            )}
          </>
        )}

        <div className="form-row interview-settings">
          <div className="field"><label htmlFor="track">I am a</label>
            <select id="track" value={track} onChange={(e) => setTrack(e.target.value)}>
              {TRACKS.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
            </select></div>
          <div className="field"><label htmlFor="difficulty">Difficulty</label>
            <select id="difficulty" value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
              {DIFFICULTIES.map((d) => <option key={d.id} value={d.id}>{d.label}</option>)}
            </select></div>
          <div className="field"><label htmlFor="count">Questions</label>
            <select id="count" value={questionCount} onChange={(e) => setQuestionCount(Number(e.target.value))}>
              {QUESTION_COUNTS.map((n) => <option key={n} value={n}>{n}</option>)}
            </select></div>
        </div>

        <div className="form-actions interview-start">
          {left !== null && <span className="field-hint">{left} of {quota.limit} interviews left today</span>}
          <button type="submit" className="btn btn-primary btn-lg" disabled={starting || left === 0}>
            {starting ? 'Preparing your questions...' : `Start ${technical ? 'technical' : 'HR'} interview`}
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
