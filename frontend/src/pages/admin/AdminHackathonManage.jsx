import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';
import Modal from '../../components/Modal';
import { PHASE_LABEL } from '../../lib/hackathons';

/** Runs a hosted hackathon: problem statements, judges, scoring, and publishing results. */
export default function AdminHackathonManage() {
  const { id } = useParams();
  const [h, setH] = useState(null);
  const [judges, setJudges] = useState([]);
  const [projects, setProjects] = useState([]);
  const [standings, setStandings] = useState([]);
  const [problems, setProblems] = useState([]);
  const [editingProblem, setEditingProblem] = useState(null);
  const [email, setEmail] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const event = await api.getHackathon(id);
      setH(event);
      const [j, p, s, probs] = await Promise.all([
        api.getHackathonJudges(id),
        api.getHackathonProjects(id),
        api.getHackathonStandings(id),
        api.getAdminHackathonProblems(id),
      ]);
      setJudges(j);
      setProjects(p);
      setStandings(s);
      setProblems(probs || []);
    } catch (err) {
      setError(err.message);
    }
  }, [id]);
  useEffect(() => { load(); }, [load]);

  async function run(action) {
    setBusy(true);
    setError(null);
    try { await action(); await load(); } catch (err) { setError(err.message); } finally { setBusy(false); }
  }

  if (!h) return <div className="container"><Alert error={error} /><p className="pdf-loading">Loading...</p></div>;

  const unscored = projects.filter((p) => p.scoreCount === 0).length;
  const canPublish = h.phase === 'JUDGING' && judges.length > 0 && projects.length > 0 && unscored === 0;
  const availableTracks = h.tracks ? h.tracks.split(',').map((t) => t.trim()).filter(Boolean) : [];

  return (
    <div className="container">
      <Alert error={error} />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', flexWrap: 'wrap', gap: '8px' }}>
        <Link to="/admin/hackathons" className="interview-exit">&larr; All hackathons</Link>
        <div style={{ display: 'flex', gap: '8px' }}>
          <Link to={`/hackathons/${id}`} className="btn btn-sm">View Hackathon</Link>
          {h.phase === 'RESULTS' && (
            <Link to={`/hackathons/${id}/leaderboard`} className="btn btn-sm btn-primary">View Leaderboard</Link>
          )}
        </div>
      </div>
      <h1 className="page-title">{h.title}</h1>
      <p className="field-hint">Phase: <strong>{PHASE_LABEL[h.phase]}</strong>. {projects.length} project{projects.length === 1 ? '' : 's'} submitted.</p>

      {/* Problem Statements Management */}
      <section className="progress-card">
        <header className="page-head">
          <div>
            <h2>Problem Statements</h2>
            <p className="field-hint" style={{ margin: 0 }}>Create challenges for participating teams to choose from.</p>
          </div>
          <button
            type="button"
            className="btn btn-sm btn-primary"
            onClick={() => setEditingProblem({ title: '', description: '', track: '', requirements: '', evaluationCriteria: '', resourcesUrl: '' })}
          >
            Add Problem Statement
          </button>
        </header>

        {problems.length === 0 ? (
          <p className="field-hint" style={{ marginTop: '14px' }}>No problem statements defined yet. Add at least one so learners can select and work on challenges.</p>
        ) : (
          <div className="table-wrap" style={{ marginTop: '14px' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Title & Description</th>
                  <th>Track</th>
                  <th>Requirements</th>
                  <th>Evaluation Criteria</th>
                  <th>Resources</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {problems.map((prob) => (
                  <tr key={prob.id}>
                    <td>
                      <strong>{prob.title}</strong>
                      <p className="field-hint" style={{ margin: '4px 0 0', maxWidth: '280px', whiteSpace: 'normal', lineHeight: '1.4' }}>
                        {prob.description && prob.description.length > 120 ? `${prob.description.slice(0, 120)}...` : prob.description}
                      </p>
                    </td>
                    <td>{prob.track ? <span className="badge">{prob.track}</span> : <span className="field-hint">—</span>}</td>
                    <td style={{ maxWidth: '180px', whiteSpace: 'normal', fontSize: '0.85rem' }}>
                      {prob.requirements ? (prob.requirements.length > 90 ? `${prob.requirements.slice(0, 90)}...` : prob.requirements) : <span className="field-hint">—</span>}
                    </td>
                    <td style={{ maxWidth: '180px', whiteSpace: 'normal', fontSize: '0.85rem' }}>
                      {prob.evaluationCriteria ? (prob.evaluationCriteria.length > 90 ? `${prob.evaluationCriteria.slice(0, 90)}...` : prob.evaluationCriteria) : <span className="field-hint">—</span>}
                    </td>
                    <td>
                      {prob.resourcesUrl ? (
                        <a href={prob.resourcesUrl} target="_blank" rel="noopener noreferrer">Resource Link</a>
                      ) : <span className="field-hint">—</span>}
                    </td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <button
                        type="button"
                        className="btn btn-sm"
                        style={{ marginRight: '6px' }}
                        onClick={() => setEditingProblem(prob)}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="btn btn-sm btn-danger"
                        disabled={busy}
                        onClick={() => {
                          if (window.confirm(`Delete problem statement "${prob.title}"?`)) {
                            run(() => api.deleteAdminHackathonProblem(id, prob.id));
                          }
                        }}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="progress-card">
        <header><h2>Judges</h2></header>
        <p className="field-hint">Judges score projects once submissions close. They must have signed in to the platform, and can't be on a team in this event.</p>
        <ul className="interview-history">
          {judges.map((j) => (
            <li key={j.userId}>
              <div className="interview-history-main"><strong>{j.name}</strong><span className="field-hint">{j.email}</span></div>
              {h.phase !== 'RESULTS' && <button type="button" className="btn btn-sm btn-danger" disabled={busy} onClick={() => run(() => api.removeHackathonJudge(id, j.userId))}>Remove</button>}
            </li>
          ))}
          {judges.length === 0 && <li><span className="field-hint">No judges yet.</span></li>}
        </ul>
        {h.phase !== 'RESULTS' && (
          <form className="hack-join" onSubmit={(e) => { e.preventDefault(); run(async () => { await api.addHackathonJudge(id, email); setEmail(''); }); }}>
            <label htmlFor="judge-email">Add a judge by email</label>
            <div><input id="judge-email" type="text" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@example.com" />
              <button type="submit" className="btn" disabled={busy || !email.trim()}>Add judge</button></div>
          </form>
        )}
      </section>

      <section className="progress-card">
        <header><h2>Projects and scoring</h2></header>
        {projects.length === 0 ? <p className="field-hint">Nothing has been submitted yet.</p> : (
          <div className="table-wrap">
            <table className="data-table">
              <thead><tr><th>Team</th><th>Project</th><th>Scores in</th></tr></thead>
              <tbody>
                {projects.map((p) => (
                  <tr key={p.submissionId}>
                    <td>{p.teamName}</td>
                    <td><a href={p.repoUrl} target="_blank" rel="noopener noreferrer">{p.title}</a></td>
                    <td>{p.scoreCount} of {judges.length}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {standings.some((s) => s.scoreCount > 0) && (
          <>
            <h3 className="hack-section">Current standings</h3>
            <ol className="hack-results">
              {standings.map((r) => (
                <li key={r.teamName}><span className="hack-rank">{r.rank}</span>
                  <div><strong>{r.teamName}</strong><p>{r.title}</p></div>
                  <span className="hack-score">{r.overall.toFixed(1)}<small>/ 10</small></span></li>
              ))}
            </ol>
          </>
        )}
      </section>

      {h.phase !== 'RESULTS' ? (
        <section className="progress-card">
          <header><h2>Publish results</h2></header>
          <p className="field-hint">
            {h.phase !== 'JUDGING' ? 'Results can be published once submissions have closed.'
              : judges.length === 0 ? 'Add at least one judge first.'
              : unscored > 0 ? `${unscored} project${unscored === 1 ? ' still needs' : 's still need'} a score.`
              : 'Every project has been scored. Publishing shows the ranking to everyone and awards badges. It can\'t be undone.'}
          </p>
          <button type="button" className="btn btn-primary" disabled={!canPublish || busy}
                  onClick={() => { if (window.confirm('Publish the results? This cannot be undone.')) run(() => api.publishHackathon(id)); }}>
            Publish results
          </button>
        </section>
      ) : (
        <section className="progress-card" style={{ borderLeft: '4px solid var(--accent, #6366f1)' }}>
          <header><h2>Results Published</h2></header>
          <p className="field-hint">
            Official results are published! Winner, runner-up, and participation certificates and badges have been awarded.
          </p>
          <div style={{ display: 'flex', gap: '10px', marginTop: '12px' }}>
            <Link to={`/hackathons/${id}/leaderboard`} className="btn btn-primary">
              View Published Leaderboard &rarr;
            </Link>
            <Link to={`/hackathons/${id}`} className="btn">
              View Public Page
            </Link>
          </div>
        </section>
      )}

      {/* Problem Statement Modal */}
      <Modal
        open={editingProblem !== null}
        title={editingProblem?.id ? 'Edit Problem Statement' : 'Add Problem Statement'}
        onClose={() => setEditingProblem(null)}
        wide
      >
        {editingProblem && (
          <ProblemForm
            problem={editingProblem}
            availableTracks={availableTracks}
            onSave={async (data) => {
              if (editingProblem.id) {
                await api.updateAdminHackathonProblem(id, editingProblem.id, data);
              } else {
                await api.createAdminHackathonProblem(id, data);
              }
              setEditingProblem(null);
              await load();
            }}
            onClose={() => setEditingProblem(null)}
          />
        )}
      </Modal>
    </div>
  );
}

function ProblemForm({ problem, availableTracks, onSave, onClose }) {
  const [form, setForm] = useState({
    title: problem.title || '',
    description: problem.description || '',
    track: problem.track || '',
    requirements: problem.requirements || '',
    evaluationCriteria: problem.evaluationCriteria || '',
    resourcesUrl: problem.resourcesUrl || '',
  });
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState(null);

  const set = (key) => (e) => setForm((prev) => ({ ...prev, [key]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setSaving(true);
    setErr(null);
    try {
      await onSave(form);
    } catch (error) {
      setErr(error.message);
      setSaving(false);
    }
  }

  return (
    <form className="modal-form" onSubmit={submit}>
      <Alert error={err} />
      <div className="field">
        <label htmlFor="prob-title">Title *</label>
        <input
          id="prob-title"
          type="text"
          maxLength={150}
          value={form.title}
          onChange={set('title')}
          placeholder="e.g. AI-Powered Smart Assistant"
          required
        />
      </div>

      <div className="field">
        <label htmlFor="prob-track">Track (optional)</label>
        {availableTracks.length > 0 ? (
          <select id="prob-track" value={form.track} onChange={set('track')}>
            <option value="">-- No specific track / All tracks --</option>
            {availableTracks.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        ) : (
          <input
            id="prob-track"
            type="text"
            maxLength={100}
            value={form.track}
            onChange={set('track')}
            placeholder="e.g. AI, Web3, Healthcare"
          />
        )}
      </div>

      <div className="field">
        <label htmlFor="prob-desc">Description *</label>
        <textarea
          id="prob-desc"
          rows={4}
          maxLength={3000}
          value={form.description}
          onChange={set('description')}
          placeholder="Detailed problem background and description..."
          required
        />
      </div>

      <div className="field">
        <label htmlFor="prob-req">Requirements (optional)</label>
        <textarea
          id="prob-req"
          rows={3}
          maxLength={3000}
          value={form.requirements}
          onChange={set('requirements')}
          placeholder="Technical or functional requirements..."
        />
      </div>

      <div className="field">
        <label htmlFor="prob-crit">Evaluation Criteria (optional)</label>
        <textarea
          id="prob-crit"
          rows={3}
          maxLength={3000}
          value={form.evaluationCriteria}
          onChange={set('evaluationCriteria')}
          placeholder="How solutions will be evaluated (e.g., Innovation, Feasibility, Code Quality)..."
        />
      </div>

      <div className="field">
        <label htmlFor="prob-res">Resources URL (optional)</label>
        <input
          id="prob-res"
          type="text"
          maxLength={500}
          value={form.resourcesUrl}
          onChange={set('resourcesUrl')}
          placeholder="https://example.com/resources or documentation URL"
        />
        <p className="field-hint">Must begin with https:// if provided.</p>
      </div>

      <div className="form-actions">
        <button type="button" className="btn btn-sm" onClick={onClose} disabled={saving}>
          Cancel
        </button>
        <button type="submit" className="btn btn-sm btn-primary" disabled={saving || !form.title.trim() || !form.description.trim()}>
          {saving ? 'Saving...' : (problem.id ? 'Save changes' : 'Create problem statement')}
        </button>
      </div>
    </form>
  );
}
