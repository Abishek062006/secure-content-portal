import { useEffect, useState } from 'react';
import { api } from '../api';
import Alert from '../components/Alert';
import Icon from '../components/Icon';

const CRITERIA = [
  { key: 'innovation', label: 'Innovation', hint: 'Originality, creativity, and uniqueness of the solution approach.' },
  { key: 'execution', label: 'Execution', hint: 'Technical depth, system architecture, completeness, and prototype stability.' },
  { key: 'impact', label: 'Impact', hint: 'Real-world relevance, practical usefulness, and scalability of the solution.' },
  { key: 'presentation', label: 'Presentation', hint: 'Clarity of explanation, demo walkthrough, and documentation.' },
];

const RUBRIC = [
  { range: '9 – 10', label: 'Exceptional', desc: 'Production-ready quality, highly original idea, game-changing real-world impact, and flawless demo.' },
  { range: '7 – 8', label: 'Advanced', desc: 'Solid technical implementation, distinct innovation, clear measurable utility, and great walkthrough.' },
  { range: '4 – 6', label: 'Proficient', desc: 'Functional prototype meeting core challenge requirements, standard approach, and basic presentation.' },
  { range: '1 – 3', label: 'Developing', desc: 'Incomplete build, minimal functionality, weak alignment with challenge goals, or missing demo.' },
];

export default function Judging() {
  const [events, setEvents] = useState(null);
  const [selected, setSelected] = useState(null);
  const [projects, setProjects] = useState([]);
  const [filter, setFilter] = useState('ALL'); // 'ALL' | 'PENDING' | 'EVALUATED'
  const [showRubric, setShowRubric] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.getJudgedEvents()
      .then(setEvents)
      .catch((err) => setError(err.message));
  }, []);

  async function open(event) {
    setSelected(event);
    setError(null);
    setFilter('ALL');
    try {
      setProjects(event.phase === 'JUDGING' ? await api.getProjectsToJudge(event.id) : []);
    } catch (err) {
      setError(err.message);
    }
  }

  function handleScoreSaved(submissionId, newScores, comment) {
    setProjects((prev) =>
      prev.map((p) => {
        if (p.submissionId === submissionId) {
          return {
            ...p,
            myInnovation: newScores.innovation,
            myExecution: newScores.execution,
            myImpact: newScores.impact,
            myPresentation: newScores.presentation,
            myComment: comment,
          };
        }
        return p;
      })
    );
  }

  // Progress calculations
  const totalCount = projects.length;
  const evaluatedCount = projects.filter((p) => p.myInnovation != null).length;
  const pendingCount = totalCount - evaluatedCount;
  const progressPct = totalCount > 0 ? Math.round((evaluatedCount / totalCount) * 100) : 0;

  const visibleProjects = projects.filter((p) => {
    if (filter === 'PENDING') return p.myInnovation == null;
    if (filter === 'EVALUATED') return p.myInnovation != null;
    return true;
  });

  return (
    <div className="container">
      <Alert error={error} />
      <h1 className="page-title">Judging</h1>

      {events && events.length === 0 && (
        <div className="empty-state">
          <p>You haven't been asked to judge a hackathon that's ready for scoring.</p>
        </div>
      )}

      {events && events.length > 0 && !selected && (
        <ul className="interview-history">
          {events.map((e) => (
            <li key={e.id}>
              <div className="interview-history-main">
                <strong>{e.title}</strong>
                <span className="field-hint">
                  {e.phase === 'JUDGING' ? 'Ready for scoring' : 'Results published (closed)'}
                </span>
              </div>
              <button type="button" className="btn btn-sm" onClick={() => open(e)}>
                Open Scoring
              </button>
            </li>
          ))}
        </ul>
      )}

      {selected && (
        <>
          <div style={{ marginBottom: '14px' }}>
            <button type="button" className="link-button" onClick={() => setSelected(null)}>
              &larr; All judging events
            </button>
          </div>

          <div className="page-head" style={{ marginBottom: '16px' }}>
            <div>
              <h2 style={{ margin: 0 }}>{selected.title}</h2>
              {selected.phase !== 'JUDGING' && (
                <p className="field-hint" style={{ color: 'var(--ink-mid)', marginTop: '4px' }}>
                  Results are published, so scoring is closed.
                </p>
              )}
            </div>
            <button
              type="button"
              className="btn btn-sm"
              onClick={() => setShowRubric(!showRubric)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            >
              <Icon name="info" size={14} />
              {showRubric ? 'Hide Scoring Guide' : 'Show Scoring Guide'}
            </button>
          </div>

          {/* Evaluation Progress Card */}
          {selected.phase === 'JUDGING' && totalCount > 0 && (
            <div
              className="progress-card"
              style={{
                marginBottom: '20px',
                padding: '16px 20px',
                background: 'var(--surface-alt)',
                border: '1px solid var(--line)',
                borderRadius: 'var(--radius)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
                <div>
                  <strong style={{ fontSize: '1rem', color: 'var(--ink)' }}>
                    Evaluation Progress: {evaluatedCount} of {totalCount} submissions evaluated
                  </strong>
                  <span className="field-hint" style={{ marginLeft: '8px' }}>
                    ({progressPct}% completed)
                  </span>
                </div>
                <div style={{ display: 'flex', gap: '6px' }}>
                  <button
                    type="button"
                    className={`btn btn-sm ${filter === 'ALL' ? 'btn-primary' : ''}`}
                    onClick={() => setFilter('ALL')}
                  >
                    All ({totalCount})
                  </button>
                  <button
                    type="button"
                    className={`btn btn-sm ${filter === 'PENDING' ? 'btn-primary' : ''}`}
                    onClick={() => setFilter('PENDING')}
                  >
                    Pending ({pendingCount})
                  </button>
                  <button
                    type="button"
                    className={`btn btn-sm ${filter === 'EVALUATED' ? 'btn-primary' : ''}`}
                    onClick={() => setFilter('EVALUATED')}
                  >
                    Evaluated ({evaluatedCount})
                  </button>
                </div>
              </div>

              {/* Visual Progress Bar */}
              <div
                style={{
                  marginTop: '12px',
                  width: '100%',
                  height: '8px',
                  backgroundColor: 'var(--line)',
                  borderRadius: '4px',
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    width: `${progressPct}%`,
                    height: '100%',
                    backgroundColor: progressPct === 100 ? '#16a34a' : 'var(--accent, #4f46e5)',
                    transition: 'width 0.3s ease',
                  }}
                />
              </div>
            </div>
          )}

          {/* Scoring Guidance Card */}
          {showRubric && (
            <div
              className="progress-card"
              style={{
                marginBottom: '24px',
                padding: '16px 20px',
                background: '#f8fafc',
                border: '1px solid #cbd5e1',
                borderRadius: 'var(--radius)',
              }}
            >
              <header style={{ marginBottom: '10px' }}>
                <h3 style={{ margin: 0, fontSize: '1rem', display: 'inline-flex', alignItems: 'center', gap: '6px', color: '#1e293b' }}>
                  <Icon name="star" size={16} /> Scoring Guidance & Rubric (1 – 10 Scale)
                </h3>
                <p className="field-hint" style={{ margin: '4px 0 0', fontSize: '0.85rem' }}>
                  Evaluate each submission independently across the four core criteria. Use the reference scale below:
                </p>
              </header>

              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                  gap: '12px',
                  marginTop: '10px',
                }}
              >
                {RUBRIC.map((r) => (
                  <div
                    key={r.range}
                    style={{
                      padding: '10px 12px',
                      background: '#ffffff',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid #e2e8f0',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                      <strong style={{ fontSize: '0.9rem', color: '#0f172a' }}>{r.label}</strong>
                      <span className="badge" style={{ fontWeight: 700, fontSize: '0.75rem' }}>{r.range}</span>
                    </div>
                    <p style={{ margin: 0, fontSize: '0.8rem', color: '#475569', lineHeight: 1.4 }}>{r.desc}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Project List */}
          {visibleProjects.length === 0 ? (
            <div className="empty-state">
              <p>
                {filter === 'PENDING'
                  ? 'All projects have been evaluated! Great job!'
                  : filter === 'EVALUATED'
                  ? 'No projects evaluated yet in this filter.'
                  : 'No submissions are available to evaluate.'}
              </p>
            </div>
          ) : (
            visibleProjects.map((p) => (
              <Project
                key={p.submissionId}
                p={p}
                eventId={selected.id}
                onError={setError}
                onScored={handleScoreSaved}
              />
            ))
          )}
        </>
      )}
    </div>
  );
}

function Project({ p, eventId, onError, onScored }) {
  const [scores, setScores] = useState({
    innovation: p.myInnovation ?? 5,
    execution: p.myExecution ?? 5,
    impact: p.myImpact ?? 5,
    presentation: p.myPresentation ?? 5,
  });
  const [comment, setComment] = useState(p.myComment || '');
  const [state, setState] = useState(p.myInnovation != null ? 'saved' : 'new');
  const [busy, setBusy] = useState(false);
  const [showProblemDetails, setShowProblemDetails] = useState(true);

  // Live average score
  const liveAvg = (
    (Number(scores.innovation) + Number(scores.execution) + Number(scores.impact) + Number(scores.presentation)) /
    4
  ).toFixed(1);

  async function save(e) {
    e.preventDefault();
    if (busy) return; // Prevent duplicate scoring requests
    setBusy(true);
    onError(null);

    // Validate 1 to 10
    const values = [scores.innovation, scores.execution, scores.impact, scores.presentation];
    for (const val of values) {
      if (val < 1 || val > 10) {
        onError('Each score must be between 1 and 10.');
        setBusy(false);
        return;
      }
    }

    try {
      await api.scoreProject(eventId, p.submissionId, { ...scores, comment });
      setState('saved');
      if (onScored) {
        onScored(p.submissionId, scores, comment);
      }
    } catch (err) {
      onError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="progress-card judge-project" onSubmit={save} style={{ marginBottom: '24px' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '8px' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '1.2rem' }}>{p.title}</h3>
          <div style={{ display: 'flex', gap: '6px', alignItems: 'center', marginTop: '6px', flexWrap: 'wrap' }}>
            <span className="badge">{p.teamName}</span>
            {p.track && <span className="badge status-featured">Track: {p.track}</span>}
            {p.myInnovation != null && (
              <span className="badge" style={{ background: '#dcfce7', color: '#15803d', fontWeight: 600 }}>
                Scored: {liveAvg} / 10
              </span>
            )}
          </div>
        </div>

        <div style={{ textAlign: 'right' }}>
          <span style={{ fontSize: '0.9rem', color: 'var(--ink-mid)' }}>Overall Score:</span>
          <div style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--ink)' }}>{liveAvg} <span style={{ fontSize: '0.85rem', fontWeight: 500 }}>/ 10</span></div>
        </div>
      </header>

      {/* Selected Problem Statement Details */}
      {p.problemStatement ? (
        <div
          style={{
            margin: '14px 0',
            padding: '14px 16px',
            background: 'var(--surface-alt)',
            borderRadius: 'var(--radius)',
            border: '1px solid var(--line)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Icon name="target" size={16} />
              <strong style={{ fontSize: '0.95rem', color: 'var(--ink)' }}>
                Target Challenge: {p.problemStatement.title}
              </strong>
            </div>
            <button
              type="button"
              className="btn btn-sm"
              onClick={() => setShowProblemDetails(!showProblemDetails)}
              style={{ fontSize: '0.78rem', padding: '2px 8px' }}
            >
              {showProblemDetails ? 'Collapse Challenge' : 'View Challenge Details'}
            </button>
          </div>

          {showProblemDetails && (
            <div style={{ marginTop: '8px' }}>
              <p className="hack-prose" style={{ margin: '0 0 10px', fontSize: '0.9rem' }}>
                {p.problemStatement.description}
              </p>

              {p.problemStatement.requirements && (
                <div style={{ margin: '6px 0', fontSize: '0.85rem' }}>
                  <strong style={{ color: 'var(--ink)' }}>Requirements & Constraints: </strong>
                  <span style={{ color: 'var(--ink-mid)' }}>{p.problemStatement.requirements}</span>
                </div>
              )}

              {p.problemStatement.evaluationCriteria && (
                <div style={{ margin: '6px 0', fontSize: '0.85rem' }}>
                  <strong style={{ color: 'var(--accent, #4f46e5)' }}>Evaluation Criteria: </strong>
                  <span style={{ color: 'var(--ink-mid)' }}>{p.problemStatement.evaluationCriteria}</span>
                </div>
              )}

              {p.problemStatement.resourcesUrl && (
                <div style={{ marginTop: '8px', fontSize: '0.82rem' }}>
                  <a href={p.problemStatement.resourcesUrl} target="_blank" rel="noopener noreferrer">
                    Starter Resources & Documentation &rarr;
                  </a>
                </div>
              )}
            </div>
          )}
        </div>
      ) : (
        <div style={{ margin: '10px 0', padding: '8px 12px', background: 'var(--surface-alt)', borderRadius: 'var(--radius-sm)', fontSize: '0.85rem', color: 'var(--ink-mid)' }}>
          <em>No specific problem statement selected for this team (General submission).</em>
        </div>
      )}

      {/* Submission Description */}
      <div style={{ margin: '14px 0' }}>
        <strong>Project Description:</strong>
        <p className="hack-prose" style={{ margin: '6px 0 10px', fontSize: '0.92rem' }}>
          {p.description}
        </p>
      </div>

      {/* Code Repository and Demo Links */}
      <p style={{ display: 'flex', gap: '16px', alignItems: 'center', margin: '0 0 16px', flexWrap: 'wrap' }}>
        <a
          href={p.repoUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="btn btn-sm"
          style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
        >
          <Icon name="code" size={14} /> View Code Repository &rarr;
        </a>
        {p.demoUrl && (
          <a
            href={p.demoUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="btn btn-sm"
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <Icon name="play" size={14} /> Open Live Demo / Video &rarr;
          </a>
        )}
      </p>

      {/* Scoring Sliders */}
      <div style={{ borderTop: '1px solid var(--line)', paddingTop: '16px', marginTop: '16px' }}>
        <h4 style={{ margin: '0 0 12px', fontSize: '1rem' }}>Score Submission (1 – 10)</h4>
        {CRITERIA.map((c) => (
          <div className="judge-row" key={c.key} style={{ marginBottom: '14px' }}>
            <label htmlFor={`${p.submissionId}-${c.key}`}>
              <strong>{c.label}</strong>
              <span className="field-hint" style={{ fontSize: '0.82rem' }}>{c.hint}</span>
            </label>
            <input
              id={`${p.submissionId}-${c.key}`}
              type="range"
              min="1"
              max="10"
              step="1"
              value={scores[c.key]}
              disabled={busy}
              onChange={(e) => {
                const val = Math.min(10, Math.max(1, Number(e.target.value)));
                setState('edited');
                setScores({ ...scores, [c.key]: val });
              }}
            />
            <output style={{ fontWeight: 700, fontSize: '1.05rem', minWidth: '24px', textAlign: 'center' }}>
              {scores[c.key]}
            </output>
          </div>
        ))}
      </div>

      {/* Judge Comments */}
      <div className="field" style={{ marginTop: '14px' }}>
        <label htmlFor={`${p.submissionId}-c`}>
          Feedback & Notes for Organizers (optional)
        </label>
        <textarea
          id={`${p.submissionId}-c`}
          rows={2}
          maxLength={1000}
          value={comment}
          disabled={busy}
          placeholder="Constructive feedback, technical strengths, or areas for improvement..."
          onChange={(e) => {
            setState('edited');
            setComment(e.target.value);
          }}
        />
      </div>

      {/* Actions and Status */}
      <div className="form-actions" style={{ marginTop: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '10px' }}>
        <div>
          {state === 'saved' && (
            <span className="field-hint" style={{ color: '#15803d', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
              <Icon name="check-circle" size={14} /> Score saved ({liveAvg} / 10). You can update it until results are published.
            </span>
          )}
          {state === 'edited' && (
            <span className="field-hint" style={{ color: '#b45309' }}>
              You have unsaved score changes.
            </span>
          )}
          {state === 'new' && (
            <span className="field-hint">
              Not yet scored. Select ratings (1–10) and save.
            </span>
          )}
        </div>
        <button
          type="submit"
          className="btn btn-primary"
          disabled={busy}
        >
          {busy ? 'Saving score...' : (state === 'new' ? 'Save Score' : 'Update Score')}
        </button>
      </div>
    </form>
  );
}
