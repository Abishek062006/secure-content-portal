import { useEffect, useState } from 'react';
import { api } from '../api';
import Alert from '../components/Alert';

const CRITERIA = [
  { key: 'innovation', label: 'Innovation', hint: 'How original and inventive is the idea?' },
  { key: 'execution', label: 'Execution', hint: 'How well is it built and does it work?' },
  { key: 'impact', label: 'Impact', hint: 'How useful would it be to real people?' },
  { key: 'presentation', label: 'Presentation', hint: 'How clearly is it explained and demonstrated?' },
];

export default function Judging() {
  const [events, setEvents] = useState(null);
  const [selected, setSelected] = useState(null);
  const [projects, setProjects] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => { api.getJudgedEvents().then(setEvents).catch((err) => setError(err.message)); }, []);

  async function open(event) {
    setSelected(event);
    setError(null);
    try {
      setProjects(event.phase === 'JUDGING' ? await api.getProjectsToJudge(event.id) : []);
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="container">
      <Alert error={error} />
      <h1 className="page-title">Judging</h1>
      {events && events.length === 0 && <div className="empty-state"><p>You haven't been asked to judge a hackathon that's ready for scoring.</p></div>}
      {events && events.length > 0 && !selected && (
        <ul className="interview-history">
          {events.map((e) => (
            <li key={e.id}>
              <div className="interview-history-main"><strong>{e.title}</strong>
                <span className="field-hint">{e.phase === 'JUDGING' ? 'Ready for scoring' : 'Results published'}</span></div>
              <button type="button" className="btn btn-sm" onClick={() => open(e)}>Open</button>
            </li>
          ))}
        </ul>
      )}
      {selected && (
        <>
          <button type="button" className="link-button" onClick={() => setSelected(null)}>All events</button>
          <h2>{selected.title}</h2>
          {selected.phase !== 'JUDGING' && <p className="field-hint">Results are published, so scoring is closed.</p>}
          {projects.map((p) => <Project key={p.submissionId} p={p} eventId={selected.id} onError={setError} />)}
        </>
      )}
    </div>
  );
}

function Project({ p, eventId, onError }) {
  const [scores, setScores] = useState({
    innovation: p.myInnovation ?? 5, execution: p.myExecution ?? 5, impact: p.myImpact ?? 5, presentation: p.myPresentation ?? 5,
  });
  const [comment, setComment] = useState(p.myComment || '');
  const [state, setState] = useState(p.myInnovation != null ? 'saved' : 'new');
  const [busy, setBusy] = useState(false);

  async function save(e) {
    e.preventDefault();
    setBusy(true);
    onError(null);
    try {
      await api.scoreProject(eventId, p.submissionId, { ...scores, comment });
      setState('saved');
    } catch (err) {
      onError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="progress-card judge-project" onSubmit={save}>
      <header><h3>{p.title}</h3><span className="badge">{p.teamName}{p.track ? `, ${p.track}` : ''}</span></header>
      <p className="hack-prose">{p.description}</p>
      <p><a href={p.repoUrl} target="_blank" rel="noopener noreferrer">Code repository</a>
        {p.demoUrl && <> · <a href={p.demoUrl} target="_blank" rel="noopener noreferrer">Demo</a></>}</p>
      {CRITERIA.map((c) => (
        <div className="judge-row" key={c.key}>
          <label htmlFor={`${p.submissionId}-${c.key}`}><strong>{c.label}</strong><span className="field-hint">{c.hint}</span></label>
          <input id={`${p.submissionId}-${c.key}`} type="range" min="1" max="10" value={scores[c.key]}
                 onChange={(e) => { setState('edited'); setScores({ ...scores, [c.key]: Number(e.target.value) }); }} />
          <output>{scores[c.key]}</output>
        </div>
      ))}
      <div className="field"><label htmlFor={`${p.submissionId}-c`}>Comment for the organisers (optional)</label>
        <textarea id={`${p.submissionId}-c`} rows={2} maxLength={1000} value={comment} onChange={(e) => { setState('edited'); setComment(e.target.value); }} /></div>
      <div className="form-actions">
        {state === 'saved' && <span className="field-hint">Scored. You can change it until results are published.</span>}
        <button type="submit" className="btn btn-primary" disabled={busy}>{state === 'new' ? 'Save score' : 'Update score'}</button>
      </div>
    </form>
  );
}
