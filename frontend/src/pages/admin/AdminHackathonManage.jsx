import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';
import { PHASE_LABEL } from '../../lib/hackathons';

/** Runs a hosted hackathon: who judges it, how scoring is going, and publishing the results. */
export default function AdminHackathonManage() {
  const { id } = useParams();
  const [h, setH] = useState(null);
  const [judges, setJudges] = useState([]);
  const [projects, setProjects] = useState([]);
  const [standings, setStandings] = useState([]);
  const [email, setEmail] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const event = await api.getHackathon(id);
      setH(event);
      const [j, p, s] = await Promise.all([api.getHackathonJudges(id), api.getHackathonProjects(id), api.getHackathonStandings(id)]);
      setJudges(j);
      setProjects(p);
      setStandings(s);
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

  return (
    <div className="container">
      <Alert error={error} />
      <Link to="/admin/hackathons" className="interview-exit">All hackathons</Link>
      <h1 className="page-title">{h.title}</h1>
      <p className="field-hint">Phase: <strong>{PHASE_LABEL[h.phase]}</strong>. {projects.length} project{projects.length === 1 ? '' : 's'} submitted.</p>

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

      {h.phase !== 'RESULTS' && (
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
      )}
    </div>
  );
}
