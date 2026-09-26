import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import Alert from '../components/Alert';
import Avatar from '../components/Avatar';
import Icon from '../components/Icon';
import { useAuth } from '../context/AuthContext';
import { MODE_LABEL, PHASE_LABEL, dateTime, timeline } from '../lib/hackathons';

export default function HackathonDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [h, setH] = useState(null);
  const [team, setTeam] = useState(undefined); // undefined: loading, null: no team
  const [results, setResults] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      const event = await api.getHackathon(id);
      if (event.kind !== 'HOSTED') {
        navigate('/hackathons', { replace: true });
        return;
      }
      setH(event);
      setTeam(user?.admin ? null : await api.getMyTeam(id));
      setResults(event.resultsPublished ? await api.getHackathonResults(id) : null);
    } catch (err) {
      setError(err.message);
    }
  }, [id, navigate, user?.admin]);
  useEffect(() => { load(); }, [load]);

  if (error && !h) return <div className="container"><Alert error={error} /><Link className="btn" to="/hackathons">All hackathons</Link></div>;
  if (!h) return <div className="container"><p className="pdf-loading">Loading...</p></div>;

  const marks = timeline(h);
  return (
    <div className="container-wide hack-detail">
      <Alert error={error} />
      <Link to="/hackathons" className="interview-exit"><Icon name="arrow-left" size={16} /> All hackathons</Link>
      <header className="hack-detail-head">
        <div className="hack-tags">
          <span className="badge status-featured">Hosted here</span>
          <span className="badge">{MODE_LABEL[h.mode] || h.mode}</span>
          <span className="badge">{PHASE_LABEL[h.phase]}</span>
        </div>
        <h1 className="page-title">{h.title}</h1>
        {h.organizer && <p className="field-hint">{h.organizer}</p>}
        {user?.admin && <Link className="btn btn-sm" to={`/admin/hackathons/${h.id}/manage`}>Manage this event</Link>}
      </header>

      <div className="hack-detail-grid">
        <div>
          <section className="progress-card">
            <header><h2>About</h2></header>
            {h.description && <p className="hack-prose">{h.description}</p>}
            {h.tracks && <p><strong>Tracks.</strong> {h.tracks}</p>}
            {h.prizes && <p><strong>Prizes.</strong> {h.prizes}</p>}
            <p><strong>Teams.</strong> {h.minTeamSize === h.maxTeamSize ? `${h.maxTeamSize} people` : `${h.minTeamSize} to ${h.maxTeamSize} people`}</p>
          </section>
          <section className="progress-card">
            <header><h2>Rules</h2></header>
            <p className="hack-prose">{h.rules}</p>
          </section>

          {!user?.admin && team !== undefined && <TeamPanel h={h} team={team} reload={load} setError={setError} />}
          {!user?.admin && team && <SubmissionPanel h={h} team={team} reload={load} setError={setError} />}
          {results && <Results results={results} myTeam={team?.name} />}
        </div>

        <aside>
          <section className="progress-card">
            <header><h2>Timeline</h2></header>
            <ol className="hack-timeline">
              {marks.map((m) => (
                <li key={m.key} className={m.done ? 'done' : ''}>
                  <span className="hack-dot" aria-hidden="true">{m.done && <Icon name="check" size={12} />}</span>
                  <div><strong>{m.label}</strong>{m.at && <span>{dateTime(m.at)}</span>}</div>
                </li>
              ))}
            </ol>
          </section>
        </aside>
      </div>
    </div>
  );
}

function TeamPanel({ h, team, reload, setError }) {
  const [name, setName] = useState('');
  const [track, setTrack] = useState('');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const tracks = h.tracks ? h.tracks.split(',').map((t) => t.trim()) : [];

  async function run(action) {
    setBusy(true);
    setError(null);
    try { await action(); await reload(); } catch (err) { setError(err.message); } finally { setBusy(false); }
  }

  if (!team) {
    if (!h.registrationOpen) {
      return <section className="progress-card"><header><h2>Your team</h2></header><p className="field-hint">Registration for this hackathon has closed.</p></section>;
    }
    return (
      <section className="progress-card">
        <header><h2>Join this hackathon</h2></header>
        <form className="modal-form" onSubmit={(e) => { e.preventDefault(); run(() => api.createTeam(h.id, name, track || tracks[0] || '')); }}>
          <div className="field"><label htmlFor="team-name">Team name</label>
            <input id="team-name" type="text" maxLength={80} value={name} onChange={(e) => setName(e.target.value)} required />
            <p className="field-hint">Entering alone? Your team is just you. Others can join with your invite link.</p></div>
          {tracks.length > 0 && (
            <div className="field"><label htmlFor="team-track">Track</label>
              <select id="team-track" value={track || tracks[0]} onChange={(e) => setTrack(e.target.value)}>
                {tracks.map((t) => <option key={t} value={t}>{t}</option>)}
              </select></div>
          )}
          <div className="form-actions"><button type="submit" className="btn btn-primary" disabled={busy}>Create team</button></div>
        </form>
        <form className="hack-join" onSubmit={(e) => { e.preventDefault(); run(() => api.joinTeam(code)); }}>
          <label htmlFor="invite">Have an invite code?</label>
          <div><input id="invite" type="text" maxLength={16} value={code} onChange={(e) => setCode(e.target.value)} placeholder="Paste it here" />
            <button type="submit" className="btn" disabled={busy || !code.trim()}>Join team</button></div>
        </form>
      </section>
    );
  }

  const link = `${window.location.origin}/hackathons/join/${team.inviteCode}`;
  return (
    <section className="progress-card">
      <header><h2>Your team: {team.name}</h2>{team.track && <span className="badge">{team.track}</span>}</header>
      <ul className="hack-members">
        {team.members.map((m) => (
          <li key={m.userId}><Avatar name={m.name} url={m.pictureUrl} size={32} /> <span>{m.name}</span>{m.leader && <em>Leader</em>}</li>
        ))}
      </ul>
      {h.phase === 'REGISTRATION' && team.members.length < h.maxTeamSize && (
        <div className="hack-invite">
          <span className="field-hint">Invite people with this link ({team.members.length} of {h.maxTeamSize} places filled)</span>
          <div>
            <input type="text" readOnly value={link} onFocus={(e) => e.target.select()} aria-label="Invite link" />
            <button type="button" className="btn btn-sm" onClick={async () => { await navigator.clipboard?.writeText(link); setCopied(true); }}>
              {copied ? 'Copied' : 'Copy'}
            </button>
          </div>
        </div>
      )}
      {h.phase === 'REGISTRATION' && (
        <button type="button" className="btn btn-sm btn-danger" disabled={busy} onClick={() => run(() => api.leaveTeam(h.id))}>Leave team</button>
      )}
    </section>
  );
}

function SubmissionPanel({ h, team, reload, setError }) {
  const s = team.submission;
  const [form, setForm] = useState({ title: s?.title || '', repoUrl: s?.repoUrl || '', demoUrl: s?.demoUrl || '', description: s?.description || '' });
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const set = (key) => (e) => { setSaved(false); setForm({ ...form, [key]: e.target.value }); };

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try { await api.submitProject(h.id, form); setSaved(true); await reload(); } catch (err) { setError(err.message); } finally { setBusy(false); }
  }

  if (h.phase === 'REGISTRATION') {
    return <section className="progress-card"><header><h2>Your project</h2></header><p className="field-hint">You can submit once building starts on {dateTime(h.eventStartDate)}.</p></section>;
  }
  if (h.phase !== 'BUILDING') {
    return (
      <section className="progress-card">
        <header><h2>Your project</h2></header>
        {s ? (
          <>
            <p><strong>{s.title}</strong></p>
            <p><a href={s.repoUrl} target="_blank" rel="noopener noreferrer">Code</a>{s.demoUrl && <> · <a href={s.demoUrl} target="_blank" rel="noopener noreferrer">Demo</a></>}</p>
            <p className="field-hint">{h.phase === 'JUDGING' ? 'Submissions are closed and judges are scoring.' : 'The results are out.'}</p>
          </>
        ) : <p className="field-hint">Your team didn't submit a project.</p>}
      </section>
    );
  }
  return (
    <section className="progress-card">
      <header><h2>Your project</h2></header>
      <form className="modal-form" onSubmit={submit}>
        <div className="field"><label htmlFor="p-title">Project name</label>
          <input id="p-title" type="text" maxLength={150} value={form.title} onChange={set('title')} required /></div>
        <div className="field"><label htmlFor="p-repo">Code repository</label>
          <input id="p-repo" type="text" maxLength={500} value={form.repoUrl} onChange={set('repoUrl')} placeholder="https://github.com/..." required /></div>
        <div className="field"><label htmlFor="p-demo">Demo video or live link (optional)</label>
          <input id="p-demo" type="text" maxLength={500} value={form.demoUrl} onChange={set('demoUrl')} placeholder="https://" /></div>
        <div className="field"><label htmlFor="p-desc">What did you build, and why does it matter?</label>
          <textarea id="p-desc" rows={6} maxLength={3000} value={form.description} onChange={set('description')} required /></div>
        <div className="form-actions">
          {saved && <span className="field-hint">Saved. You can keep improving it until {dateTime(h.eventEndDate)}.</span>}
          <button type="submit" className="btn btn-primary" disabled={busy}>{s ? 'Update project' : 'Submit project'}</button>
        </div>
      </form>
    </section>
  );
}

function Results({ results, myTeam }) {
  return (
    <section className="progress-card">
      <header><h2>Results</h2></header>
      <ol className="hack-results">
        {results.map((r) => (
          <li key={r.teamName} className={`${r.rank <= 3 ? `podium p${r.rank}` : ''}${r.teamName === myTeam ? ' me' : ''}`}>
            <span className="hack-rank">{r.rank}</span>
            <div>
              <strong>{r.teamName}</strong> <span className="field-hint">{r.members.join(', ')}</span>
              <p>{r.title}</p>
              <p className="field-hint">
                <a href={r.repoUrl} target="_blank" rel="noopener noreferrer">Code</a>
                {r.demoUrl && <> · <a href={r.demoUrl} target="_blank" rel="noopener noreferrer">Demo</a></>}
              </p>
            </div>
            <span className="hack-score">{r.overall.toFixed(1)}<small>/ 10</small></span>
          </li>
        ))}
      </ol>
    </section>
  );
}
