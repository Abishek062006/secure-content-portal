import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import Alert from '../components/Alert';
import Avatar from '../components/Avatar';
import Icon from '../components/Icon';
import { MODE_LABEL, PHASE_LABEL, dateTime } from '../lib/hackathons';

export default function HackathonWorkspace() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [h, setH] = useState(null);
  const [team, setTeam] = useState(undefined); // undefined: loading, null: no team
  const [problems, setProblems] = useState([]);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const [copied, setCopied] = useState(false);

  // Submission form state
  const [showEditSubmission, setShowEditSubmission] = useState(false);
  const [subForm, setSubForm] = useState({ title: '', repoUrl: '', demoUrl: '', description: '' });
  const [subMessage, setSubMessage] = useState(null);

  // Problem statement change state
  const [changingProblem, setChangingProblem] = useState(false);

  // Live timer tick every second
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  const load = useCallback(async () => {
    try {
      const event = await api.getHackathon(id);
      if (event.kind !== 'HOSTED') {
        navigate('/hackathons', { replace: true });
        return;
      }
      setH(event);

      const [myTeam, probs] = await Promise.all([
        api.getMyTeam(id),
        api.getHackathonProblems(id),
      ]);

      setTeam(myTeam);
      setProblems(probs || []);

      if (myTeam?.submission) {
        setSubForm({
          title: myTeam.submission.title || '',
          repoUrl: myTeam.submission.repoUrl || '',
          demoUrl: myTeam.submission.demoUrl || '',
          description: myTeam.submission.description || '',
        });
      }
    } catch (err) {
      setError(err.message);
    }
  }, [id, navigate]);

  useEffect(() => {
    load();
  }, [load]);

  async function runAction(action) {
    setBusy(true);
    setError(null);
    try {
      await action();
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (error && !h) {
    return (
      <div className="container">
        <Alert error={error} />
        <Link className="btn" to={`/hackathons/${id}`}>Back to hackathon</Link>
      </div>
    );
  }

  if (!h || team === undefined) {
    return (
      <div className="container">
        <p className="pdf-loading">Loading workspace...</p>
      </div>
    );
  }

  // If learner is not in a team for this event
  if (!team) {
    return (
      <div className="container" style={{ maxWidth: '680px', marginTop: '40px' }}>
        <section className="progress-card" style={{ textAlign: 'center', padding: '36px 24px' }}>
          <header>
            <h1 style={{ fontSize: '1.4rem', margin: '0 0 10px' }}>Workspace Access Required</h1>
          </header>
          <p className="field-hint" style={{ fontSize: '0.95rem', margin: '0 0 20px' }}>
            The workspace is dedicated to registered team members. You need to create or join a team first to access problem selection, team collaboration, and submissions for <strong>{h.title}</strong>.
          </p>
          <Link to={`/hackathons/${h.id}`} className="btn btn-primary">
            Join or Create a Team &rarr;
          </Link>
        </section>
      </div>
    );
  }

  // Selected problem statement
  const selectedProblem = (problems || []).find((p) => p.id === team.problemStatementId);
  const isLeader = Boolean(team.leader);

  // Countdown calculations
  let deadlineTarget = null;
  let deadlineLabel = '';
  if (h.phase === 'REGISTRATION') {
    deadlineTarget = h.eventStartDate || h.registrationDeadline;
    deadlineLabel = 'Hacking Begins In';
  } else if (h.phase === 'BUILDING') {
    deadlineTarget = h.eventEndDate;
    deadlineLabel = 'Submission Deadline (Submissions close in)';
  } else if (h.phase === 'JUDGING') {
    deadlineLabel = 'Submissions Locked · Judging Underway';
  } else if (h.phase === 'RESULTS') {
    deadlineLabel = 'Hackathon Concluded · Results Announced';
  }

  const countdown = getCountdown(deadlineTarget, now);
  const diffMs = deadlineTarget ? new Date(deadlineTarget).getTime() - now : null;

  // Strict deadline & locking flags
  const isBuilding = h.phase === 'BUILDING';
  const isDeadlinePassed = isBuilding && diffMs != null && diffMs <= 0;
  const isLocked = h.phase === 'JUDGING' || h.phase === 'RESULTS' || isDeadlinePassed || (team?.submission?.status === 'LOCKED');

  const canSelectProblem = isLeader && (h.phase === 'REGISTRATION' || (h.phase === 'BUILDING' && !isDeadlinePassed));

  // Countdown warnings when 24 hours, 1 hour and 10 minutes remain during BUILDING phase
  let deadlineWarning = null;
  if (isBuilding && diffMs != null && diffMs > 0) {
    if (diffMs <= 10 * 60 * 1000) {
      deadlineWarning = {
        level: 'critical',
        title: 'CRITICAL DEADLINE WARNING (Less than 10 minutes remaining!)',
        message: 'Submissions lock in less than 10 minutes. Finalize your repository link, demo URL, and project submission immediately!',
      };
    } else if (diffMs <= 60 * 60 * 1000) {
      deadlineWarning = {
        level: 'urgent',
        title: 'URGENT DEADLINE WARNING (Less than 1 hour remaining)',
        message: 'Submissions close in under an hour. Verify your code repository link, demo URL, and project description now.',
      };
    } else if (diffMs <= 24 * 60 * 60 * 1000) {
      deadlineWarning = {
        level: 'notice',
        title: 'DEADLINE APPROACHING (Less than 24 hours remaining)',
        message: 'The submission deadline is within 24 hours. Ensure your team wraps up development and submits before the timer ends.',
      };
    }
  }

  // Effective submission state: DRAFT -> SUBMITTED -> LOCKED
  const rawStatus = team.submission?.status || (team.submission ? 'SUBMITTED' : 'NOT_SUBMITTED');
  const effectiveStatus = isLocked ? 'LOCKED' : rawStatus;

  const inviteLink = `${window.location.origin}/hackathons/join/${team.inviteCode}`;

  return (
    <div className="container-wide hack-detail">
      <Alert error={error} />

      <Link to={`/hackathons/${h.id}`} className="interview-exit">
        <Icon name="arrow-left" size={16} /> Back to Hackathon Overview
      </Link>

      {/* Top Workspace Header */}
      <header className="workspace-head">
        <div className="workspace-head-top">
          <div>
            <div className="hack-tags" style={{ marginBottom: '8px' }}>
              <span className={`workspace-phase-badge ${h.phase}`}>
                <Icon name={h.phase === 'BUILDING' ? 'zap' : (h.phase === 'RESULTS' ? 'award' : 'clock')} size={14} />
                {PHASE_LABEL[h.phase]}
              </span>
              <span className="badge">{MODE_LABEL[h.mode] || h.mode}</span>
              {team.track && <span className="badge status-featured">Track: {team.track}</span>}
              {effectiveStatus === 'LOCKED' && (
                <span className="badge" style={{ background: '#334155', color: '#f8fafc', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <Icon name="lock" size={12} /> LOCKED
                </span>
              )}
              {effectiveStatus === 'SUBMITTED' && (
                <span className="badge" style={{ background: '#16a34a', color: '#ffffff', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <Icon name="check-circle" size={12} /> SUBMITTED
                </span>
              )}
              {effectiveStatus === 'DRAFT' && (
                <span className="badge" style={{ background: '#2563eb', color: '#ffffff', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <Icon name="clock" size={12} /> DRAFT
                </span>
              )}
            </div>
            <h1 className="page-title" style={{ margin: 0 }}>{h.title} Workspace</h1>
            <p className="field-hint" style={{ margin: '6px 0 0' }}>
              Team: <strong>{team.name}</strong> · {team.members.length} member{team.members.length === 1 ? '' : 's'} · {isLeader ? 'You are Team Leader' : 'Team Member'}
            </p>
          </div>

          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <Link to={`/hackathons/${h.id}`} className="btn btn-sm">
              Event Details
            </Link>
            {h.resultsPublished && (
              <>
                <Link to={`/hackathons/${h.id}/leaderboard`} className="btn btn-sm btn-primary">
                  <Icon name="award" size={14} /> Leaderboard
                </Link>
                <Link to={`/hackathons/${h.id}/certificate`} className="btn btn-sm">
                  My Certificate
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Results Published Notification Banner */}
      {h.resultsPublished && (
        <div className="workspace-cta-banner" style={{ background: 'linear-gradient(135deg, #fefce8, #eff6ff)', borderColor: '#facc15' }}>
          <div>
            <h3 style={{ color: '#854d0e', display: 'flex', alignItems: 'center', gap: '8px', margin: '0 0 4px' }}>
              <Icon name="award" size={18} /> Results & Certificates Live!
            </h3>
            <p style={{ margin: 0, color: 'var(--ink-mid)' }}>
              Final judge scores are published. View where your team placed on the leaderboard and access your certificate.
            </p>
          </div>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <Link to={`/hackathons/${h.id}/leaderboard`} className="btn btn-primary" style={{ whiteSpace: 'nowrap' }}>
              View Leaderboard &rarr;
            </Link>
            <Link to={`/hackathons/${h.id}/certificate`} className="btn" style={{ whiteSpace: 'nowrap' }}>
              Download Certificate
            </Link>
          </div>
        </div>
      )}

      {/* Deadline Warning Banners (24h, 1h, 10m) */}
      {deadlineWarning && (
        <div className={`deadline-warning-banner ${deadlineWarning.level}`}>
          <Icon name={deadlineWarning.level === 'critical' ? 'bell-ring' : 'clock'} size={20} />
          <div>
            <div style={{ fontWeight: 700 }}>{deadlineWarning.title}</div>
            <div style={{ fontSize: '0.86rem', marginTop: '2px' }}>{deadlineWarning.message}</div>
          </div>
        </div>
      )}

      {/* Live Countdown Timer */}
      <div className="countdown-box">
        <div className="countdown-header">
          <h3>
            <Icon name={isLocked ? 'lock' : 'timer'} size={18} />
            {deadlineLabel}
          </h3>
          {deadlineTarget && (
            <span className="field-hint" style={{ fontSize: '0.85rem' }}>
              Target: {dateTime(deadlineTarget)}
            </span>
          )}
        </div>

        {countdown && !countdown.passed ? (
          <div className="countdown-tiles">
            <div className="countdown-tile">
              <span className="countdown-val">{String(countdown.days).padStart(2, '0')}</span>
              <span className="countdown-lbl">Days</span>
            </div>
            <div className="countdown-tile">
              <span className="countdown-val">{String(countdown.hours).padStart(2, '0')}</span>
              <span className="countdown-lbl">Hours</span>
            </div>
            <div className="countdown-tile">
              <span className="countdown-val">{String(countdown.minutes).padStart(2, '0')}</span>
              <span className="countdown-lbl">Minutes</span>
            </div>
            <div className="countdown-tile">
              <span className="countdown-val">{String(countdown.seconds).padStart(2, '0')}</span>
              <span className="countdown-lbl">Seconds</span>
            </div>
          </div>
        ) : (
          <p className="field-hint" style={{ margin: 0, fontWeight: 500, color: 'var(--ink)' }}>
            {isLocked
              ? 'Submission window is closed. All projects are locked for judging evaluation.'
              : (h.phase === 'JUDGING'
                ? 'All project submissions are locked. Judges are scoring submissions against evaluation criteria.'
                : 'Results and standings are finalized. Thank you for participating!')}
          </p>
        )}
      </div>

      <div className="hack-detail-grid">
        {/* Main Column */}
        <div>
          {/* Selected Problem Statement Section */}
          <section className="progress-card">
            <header className="page-head">
              <div>
                <h2>Selected Problem Statement</h2>
                <p className="field-hint" style={{ margin: 0 }}>Your team's target challenge for this hackathon.</p>
              </div>
              {canSelectProblem && selectedProblem && (
                <button
                  type="button"
                  className="btn btn-sm"
                  onClick={() => setChangingProblem(!changingProblem)}
                >
                  {changingProblem ? 'Cancel' : 'Change Challenge'}
                </button>
              )}
            </header>

            {/* Problem change dropdown if leader is changing */}
            {canSelectProblem && (changingProblem || !selectedProblem) && (
              <div style={{ marginTop: '16px', padding: '16px', background: 'var(--surface-alt)', borderRadius: 'var(--radius)', border: '1px solid var(--line)' }}>
                <label htmlFor="select-problem-dropdown" style={{ display: 'block', fontWeight: 600, marginBottom: '6px' }}>
                  {selectedProblem ? 'Switch to another challenge:' : 'Choose a challenge for your team:'}
                </label>
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  <select
                    id="select-problem-dropdown"
                    value={team.problemStatementId || ''}
                    onChange={(e) => {
                      const val = e.target.value ? Number(e.target.value) : null;
                      runAction(async () => {
                        await api.selectTeamProblem(h.id, val);
                        setChangingProblem(false);
                      });
                    }}
                    disabled={busy}
                    style={{ flex: 1, minWidth: '220px', padding: '8px 12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--line)', background: 'var(--surface)' }}
                  >
                    <option value="">-- Select a problem statement --</option>
                    {problems.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.title} {p.track ? `[Track: ${p.track}]` : ''}
                      </option>
                    ))}
                  </select>
                </div>
                {!isLeader && (
                  <p className="field-hint" style={{ marginTop: '6px', color: 'var(--accent-deep)' }}>
                    Only the team leader can change the selected problem statement.
                  </p>
                )}
              </div>
            )}

            {/* Display Selected Problem Statement Details */}
            {selectedProblem ? (
              <div className="problem-card-highlight">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '10px' }}>
                  <h3 style={{ margin: 0, fontSize: '1.2rem', color: 'var(--ink)' }}>{selectedProblem.title}</h3>
                  {selectedProblem.track && <span className="badge status-featured">{selectedProblem.track}</span>}
                </div>

                <div style={{ margin: '14px 0' }}>
                  <strong>Description:</strong>
                  <p className="hack-prose" style={{ margin: '6px 0 12px' }}>{selectedProblem.description}</p>
                </div>

                {selectedProblem.requirements && (
                  <div style={{ margin: '12px 0' }}>
                    <strong>Requirements & Constraints:</strong>
                    <p className="hack-prose" style={{ margin: '6px 0 12px', color: 'var(--ink-mid)' }}>{selectedProblem.requirements}</p>
                  </div>
                )}

                {selectedProblem.evaluationCriteria && (
                  <div style={{ margin: '12px 0' }}>
                    <strong>Evaluation Criteria:</strong>
                    <p className="hack-prose" style={{ margin: '6px 0 12px', color: 'var(--ink-mid)' }}>{selectedProblem.evaluationCriteria}</p>
                  </div>
                )}

                {selectedProblem.resourcesUrl && (
                  <div style={{ marginTop: '14px', paddingTop: '10px', borderTop: '1px solid var(--line)' }}>
                    <a
                      href={selectedProblem.resourcesUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn btn-sm"
                      style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                    >
                      <Icon name="link" size={14} /> Open Starter Resources & Documentation &rarr;
                    </a>
                  </div>
                )}
              </div>
            ) : (
              !changingProblem && (
                <div style={{ marginTop: '16px', padding: '20px', background: 'var(--surface-alt)', borderRadius: 'var(--radius)', border: '1px dashed var(--line)', textAlign: 'center' }}>
                  <p className="field-hint" style={{ fontSize: '0.95rem', margin: '0 0 12px' }}>
                    Your team hasn't selected a problem statement yet.
                  </p>
                  {canSelectProblem ? (
                    <button
                      type="button"
                      className="btn btn-primary btn-sm"
                      onClick={() => setChangingProblem(true)}
                    >
                      Choose Challenge Now
                    </button>
                  ) : (
                    <p className="field-hint" style={{ margin: 0 }}>
                      Please coordinate with your team leader to select a challenge.
                    </p>
                  )}
                </div>
              )
            )}
          </section>

          {/* Project Submission Section */}
          <section className="progress-card">
            <header className="page-head">
              <div>
                <h2>Project Submission</h2>
                <p className="field-hint" style={{ margin: 0 }}>Submit your working prototype and code repository.</p>
              </div>
            </header>

            {/* Submission Status Indicator (DRAFT -> SUBMITTED -> LOCKED) */}
            {effectiveStatus === 'LOCKED' ? (
              <div className="submission-status-banner locked">
                <div>
                  <strong style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '0.95rem' }}>
                    <Icon name="lock" size={16} /> Submissions Locked
                  </strong>
                  <p style={{ margin: '4px 0 0', fontSize: '0.85rem' }}>
                    {team.submission
                      ? (rawStatus === 'DRAFT'
                        ? 'The deadline has passed. This project was left as a draft and was not submitted.'
                        : `Project "${team.submission.title}" is locked and finalized for judge evaluation.`)
                      : 'The submission deadline has passed. No submission was registered for this team.'}
                  </p>
                </div>
                <span className="badge" style={{ background: '#334155', color: '#f8fafc', fontWeight: 700 }}>
                  LOCKED
                </span>
              </div>
            ) : effectiveStatus === 'SUBMITTED' ? (
              <div className="submission-status-banner submitted">
                <div>
                  <strong style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '0.95rem' }}>
                    <Icon name="check-circle" size={16} /> Submission Received
                  </strong>
                  <p style={{ margin: '4px 0 0', fontSize: '0.85rem' }}>
                    Project "{team.submission.title}" is submitted and registered for evaluation.
                  </p>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span className="badge" style={{ background: '#16a34a', color: '#ffffff', fontWeight: 700 }}>
                    SUBMITTED
                  </span>
                  {isBuilding && !showEditSubmission && (
                    <button
                      type="button"
                      className="btn btn-sm"
                      onClick={() => setShowEditSubmission(true)}
                    >
                      Edit Submission
                    </button>
                  )}
                </div>
              </div>
            ) : effectiveStatus === 'DRAFT' ? (
              <div className="submission-status-banner draft">
                <div>
                  <strong style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '0.95rem' }}>
                    <Icon name="clock" size={16} /> Draft Saved
                  </strong>
                  <p style={{ margin: '4px 0 0', fontSize: '0.85rem' }}>
                    Your project details are saved as a draft. Remember to click "Submit Project" before the deadline!
                  </p>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span className="badge" style={{ background: '#2563eb', color: '#ffffff', fontWeight: 700 }}>
                    DRAFT
                  </span>
                  {isBuilding && !showEditSubmission && (
                    <button
                      type="button"
                      className="btn btn-sm btn-primary"
                      onClick={() => setShowEditSubmission(true)}
                    >
                      Continue & Submit &rarr;
                    </button>
                  )}
                </div>
              </div>
            ) : (
              <div className="submission-status-banner pending">
                <div>
                  <strong style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '0.95rem' }}>
                    <Icon name="clock" size={16} /> Not Submitted Yet
                  </strong>
                  <p style={{ margin: '4px 0 0', fontSize: '0.85rem' }}>
                    {isBuilding
                      ? 'Ensure your team submits your solution before the countdown expires!'
                      : 'Submissions unlock during the building phase.'}
                  </p>
                </div>
                <span className="badge">DRAFT</span>
              </div>
            )}

            {/* Notification message after saving draft or submitting */}
            {subMessage && (
              <div
                style={{
                  padding: '10px 14px',
                  marginBottom: '14px',
                  borderRadius: 'var(--radius-sm)',
                  background: subMessage.type === 'draft' ? '#eff6ff' : '#ecfdf5',
                  border: `1px solid ${subMessage.type === 'draft' ? '#bfdbfe' : '#a7f3d0'}`,
                  color: subMessage.type === 'draft' ? '#1e40af' : '#065f46',
                  fontSize: '0.9rem',
                }}
              >
                {subMessage.text}
              </div>
            )}

            {/* View or Edit Submission Content */}
            {h.phase === 'REGISTRATION' && (
              <p className="field-hint">
                Building has not started yet. Submission form will be accessible starting {dateTime(h.eventStartDate)}.
              </p>
            )}

            {/* Active Building phase form */}
            {isBuilding && !isLocked && (
              <>
                {(!team.submission || showEditSubmission) ? (
                  <form
                    className="modal-form"
                    onSubmit={(e) => {
                      e.preventDefault();
                    }}
                    style={{ marginTop: '16px' }}
                  >
                    <div className="field">
                      <label htmlFor="w-title">Project Name *</label>
                      <input
                        id="w-title"
                        type="text"
                        maxLength={150}
                        value={subForm.title}
                        onChange={(e) => setSubForm({ ...subForm, title: e.target.value })}
                        placeholder="e.g. HealthBridge AI"
                        required
                      />
                    </div>

                    <div className="field">
                      <label htmlFor="w-repo">Code Repository URL *</label>
                      <input
                        id="w-repo"
                        type="text"
                        maxLength={500}
                        value={subForm.repoUrl}
                        onChange={(e) => setSubForm({ ...subForm, repoUrl: e.target.value })}
                        placeholder="https://github.com/your-team/project"
                        required
                      />
                      <p className="field-hint">Must be an accessible URL (e.g. GitHub, GitLab).</p>
                    </div>

                    <div className="field">
                      <label htmlFor="w-demo">Demo Video or Live Link (optional)</label>
                      <input
                        id="w-demo"
                        type="text"
                        maxLength={500}
                        value={subForm.demoUrl}
                        onChange={(e) => setSubForm({ ...subForm, demoUrl: e.target.value })}
                        placeholder="https://youtube.com/... or https://your-demo.app"
                      />
                    </div>

                    <div className="field">
                      <label htmlFor="w-desc">What did you build and why does it matter? *</label>
                      <textarea
                        id="w-desc"
                        rows={5}
                        maxLength={3000}
                        value={subForm.description}
                        onChange={(e) => setSubForm({ ...subForm, description: e.target.value })}
                        placeholder="Explain your solution, architecture, and impact..."
                        required
                      />
                    </div>

                    <div className="form-actions" style={{ marginTop: '16px', display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                      {showEditSubmission && team.submission && (
                        <button
                          type="button"
                          className="btn btn-sm"
                          onClick={() => setShowEditSubmission(false)}
                          disabled={busy}
                        >
                          Cancel
                        </button>
                      )}

                      {/* A project that is already submitted can only be updated, not turned back into a draft. */}
                      {rawStatus !== 'SUBMITTED' && (
                        <button
                          type="button"
                          className="btn btn-sm"
                          disabled={busy || !subForm.title.trim()}
                          onClick={() => {
                            runAction(async () => {
                              await api.submitProject(h.id, { ...subForm, draft: true });
                              setSubMessage({ type: 'draft', text: 'Draft saved successfully. Don\'t forget to click "Submit Project" before the deadline.' });
                              setShowEditSubmission(false);
                            });
                          }}
                        >
                          {busy ? 'Saving...' : 'Save Draft'}
                        </button>
                      )}

                      {/* Submit Project Button */}
                      <button
                        type="button"
                        className="btn btn-primary"
                        disabled={busy || !subForm.title.trim() || !subForm.repoUrl.trim() || !subForm.description.trim()}
                        onClick={() => {
                          runAction(async () => {
                            await api.submitProject(h.id, { ...subForm, draft: false });
                            setSubMessage({ type: 'submit', text: 'Project submitted successfully! Your submission is registered for evaluation.' });
                            setShowEditSubmission(false);
                          });
                        }}
                      >
                        {busy ? 'Submitting...' : (team.submission && rawStatus === 'SUBMITTED' ? 'Update Submission' : 'Submit Project')}
                      </button>
                    </div>
                  </form>
                ) : (
                  <div style={{ marginTop: '12px', padding: '16px', background: 'var(--surface-alt)', borderRadius: 'var(--radius)', border: '1px solid var(--line)' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <h3 style={{ margin: '0 0 6px', fontSize: '1.05rem' }}>{team.submission.title}</h3>
                      <span className="badge" style={{ textTransform: 'uppercase' }}>{team.submission.status}</span>
                    </div>
                    <p style={{ margin: '0 0 10px', fontSize: '0.9rem' }}>
                      {team.submission.repoUrl && (
                        <a href={team.submission.repoUrl} target="_blank" rel="noopener noreferrer" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontWeight: 600 }}>
                          <Icon name="code" size={14} /> Repository
                        </a>
                      )}
                      {team.submission.demoUrl && (
                        <> · <a href={team.submission.demoUrl} target="_blank" rel="noopener noreferrer" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontWeight: 600 }}>
                          <Icon name="play" size={14} /> Live Demo
                        </a></>
                      )}
                    </p>
                    <p className="hack-prose" style={{ margin: 0, fontSize: '0.92rem' }}>{team.submission.description}</p>
                  </div>
                )}
              </>
            )}

            {/* Read-only view when locked or in judging/results */}
            {isLocked && (
              <div>
                {team.submission ? (
                  <div style={{ marginTop: '12px', padding: '16px', background: 'var(--surface-alt)', borderRadius: 'var(--radius)', border: '1px solid var(--line)' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <h3 style={{ margin: '0 0 6px', fontSize: '1.05rem' }}>{team.submission.title}</h3>
                      <span className="badge" style={{ background: '#334155', color: '#f8fafc' }}>{rawStatus === 'DRAFT' ? 'NOT SUBMITTED' : 'LOCKED'}</span>
                    </div>
                    <p style={{ margin: '0 0 10px', fontSize: '0.9rem' }}>
                      {team.submission.repoUrl && <a href={team.submission.repoUrl} target="_blank" rel="noopener noreferrer" style={{ fontWeight: 600 }}>Code Repository</a>}
                      {team.submission.demoUrl && <> · <a href={team.submission.demoUrl} target="_blank" rel="noopener noreferrer" style={{ fontWeight: 600 }}>Demo</a></>}
                    </p>
                    <p className="hack-prose" style={{ margin: 0 }}>{team.submission.description}</p>
                    <p className="field-hint" style={{ marginTop: '12px', borderTop: '1px solid var(--line)', paddingTop: '8px' }}>
                      {rawStatus === 'DRAFT'
                        ? 'Submissions are closed. A draft that was never submitted is not judged.'
                        : 'Submissions are permanently locked. Judges are currently evaluating eligible projects.'}
                    </p>
                  </div>
                ) : (
                  <div style={{ marginTop: '12px', padding: '16px', background: 'var(--surface-alt)', borderRadius: 'var(--radius)', border: '1px solid var(--line)' }}>
                    <p className="field-hint" style={{ margin: 0 }}>
                      No project was submitted by your team before the deadline. Submissions are now closed.
                    </p>
                  </div>
                )}
              </div>
            )}
          </section>

          {/* Event Guidelines & Rules */}
          {h.rules && (
            <section className="progress-card">
              <header>
                <h2>Event Rules & Guidelines</h2>
              </header>
              <p className="hack-prose">{h.rules}</p>
              {h.prizes && (
                <div style={{ marginTop: '14px', paddingTop: '10px', borderTop: '1px solid var(--line)' }}>
                  <strong>Prizes & Recognition:</strong>
                  <p className="hack-prose" style={{ margin: '4px 0 0' }}>{h.prizes}</p>
                </div>
              )}
            </section>
          )}
        </div>

        {/* Sidebar Column */}
        <aside>
          {/* Team Details Card */}
          <section className="progress-card">
            <header>
              <h2>Team: {team.name}</h2>
              {team.track && <span className="badge">{team.track}</span>}
            </header>

            <p className="field-hint" style={{ marginTop: '4px' }}>
              Members: {team.members.length} of {h.maxTeamSize} maximum
            </p>

            <ul className="hack-members" style={{ marginTop: '10px' }}>
              {team.members.map((m) => (
                <li key={m.userId}>
                  <Avatar name={m.name} url={m.pictureUrl} size={32} />
                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                    <span style={{ fontWeight: 600, fontSize: '0.92rem' }}>{m.name}</span>
                    {m.email && <span className="field-hint" style={{ fontSize: '0.78rem' }}>{m.email}</span>}
                  </div>
                  {m.leader && <em>Leader</em>}
                </li>
              ))}
            </ul>

            {/* Invite link if registration is open and team is not full */}
            {h.phase === 'REGISTRATION' && team.members.length < h.maxTeamSize && (
              <div className="hack-invite">
                <span className="field-hint">Invite teammates with this code or link:</span>
                <div style={{ marginTop: '6px' }}>
                  <input
                    type="text"
                    readOnly
                    value={inviteLink}
                    onFocus={(e) => e.target.select()}
                    aria-label="Invite link"
                  />
                  <button
                    type="button"
                    className="btn btn-sm"
                    onClick={async () => {
                      await navigator.clipboard?.writeText(inviteLink);
                      setCopied(true);
                      setTimeout(() => setCopied(false), 2000);
                    }}
                  >
                    {copied ? 'Copied' : 'Copy'}
                  </button>
                </div>
              </div>
            )}

            {h.phase === 'REGISTRATION' && (
              <button
                type="button"
                className="btn btn-sm btn-danger"
                disabled={busy}
                onClick={() => {
                  if (window.confirm('Are you sure you want to leave this team?')) {
                    runAction(async () => {
                      await api.leaveTeam(h.id);
                      navigate(`/hackathons/${h.id}`);
                    });
                  }
                }}
                style={{ marginTop: '12px' }}
              >
                Leave team
              </button>
            )}
          </section>

          {/* Quick Links Card */}
          <section className="progress-card">
            <header>
              <h2>Workspace Navigation</h2>
            </header>
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <li>
                <Link to={`/hackathons/${h.id}`} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.9rem' }}>
                  <Icon name="file-text" size={16} /> Overview & All Challenges
                </Link>
              </li>
              <li>
                <Link to="/hackathons" style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.9rem' }}>
                  <Icon name="trophy" size={16} /> Explore Other Hackathons
                </Link>
              </li>
            </ul>
          </section>
        </aside>
      </div>
    </div>
  );
}

function getCountdown(targetDate, currentNow) {
  if (!targetDate) return null;
  const diff = new Date(targetDate).getTime() - currentNow;
  if (diff <= 0) {
    return { days: 0, hours: 0, minutes: 0, seconds: 0, passed: true };
  }
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  const hours = Math.floor((diff / (1000 * 60 * 60)) % 24);
  const minutes = Math.floor((diff / (1000 * 60)) % 60);
  const seconds = Math.floor((diff / 1000) % 60);
  return { days, hours, minutes, seconds, passed: false };
}
