import { useCallback, useEffect, useState } from 'react';
import { api } from '../api';
import Alert from '../components/Alert';
import Icon from '../components/Icon';
import { MODE_LABEL, closingSoon, dateRange, deadlineLabel } from '../lib/hackathons';

const MODES = [
  { id: 'all', label: 'All' },
  { id: 'ONLINE', label: 'Online' },
  { id: 'OFFLINE', label: 'In person' },
  { id: 'HYBRID', label: 'Hybrid' },
];

export default function Hackathons() {
  const [mode, setMode] = useState('all');
  const [savedOnly, setSavedOnly] = useState(false);
  const [items, setItems] = useState(null);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(() => {
    api.getHackathons(mode, savedOnly).then(setItems).catch((err) => setError(err.message));
  }, [mode, savedOnly]);
  useEffect(load, [load]);

  async function toggleSave(h) {
    setBusyId(h.id);
    setError(null);
    try {
      if (h.saved) await api.unsaveHackathon(h.id); else await api.saveHackathon(h.id);
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId(null);
    }
  }

  async function addToCalendar(h) {
    setError(null);
    try {
      await api.downloadHackathonCalendar(h.id);
    } catch (err) {
      setError(err.message);
    }
  }

  const open = (items || []).filter((h) => h.registrationOpen && h.status !== 'COMPLETED');
  const past = (items || []).filter((h) => !(h.registrationOpen && h.status !== 'COMPLETED'));
  const reminders = (items || []).filter((h) => closingSoon(h));

  return (
    <div className="container-wide">
      <Alert error={error} />
      <h1 className="page-title">Hackathons</h1>
      <p className="field-hint">Real events run by other organisers. Save the ones you want, add them to your calendar, and register on the organiser's site.</p>

      {reminders.length > 0 && (
        <section className="hack-reminders" aria-label="Closing soon">
          <Icon name="clock" size={18} />
          <div>
            <strong>Closing soon</strong>
            <ul>
              {reminders.map((h) => <li key={h.id}>{h.title}: {deadlineLabel(h).toLowerCase()}</li>)}
            </ul>
          </div>
        </section>
      )}

      <div className="hack-filters">
        <div className="segmented" role="tablist" aria-label="Format">
          {MODES.map((m) => (
            <button key={m.id} type="button" role="tab" aria-selected={mode === m.id} className={mode === m.id ? 'active' : ''}
                    onClick={() => setMode(m.id)}>{m.label}</button>
          ))}
        </div>
        <label className="board-toggle">
          <input type="checkbox" checked={savedOnly} onChange={(e) => setSavedOnly(e.target.checked)} />
          Saved only
        </label>
      </div>

      {items && items.length === 0 && (
        <div className="empty-state"><p>{savedOnly ? 'You haven\'t saved any hackathons yet.' : 'No hackathons are listed right now. Check back soon.'}</p></div>
      )}

      <div className="hack-grid">
        {open.map((h) => <Card key={h.id} h={h} busy={busyId === h.id} onSave={toggleSave} onCalendar={addToCalendar} />)}
      </div>

      {past.length > 0 && (
        <>
          <h2 className="hack-section">Past and closed</h2>
          <div className="hack-grid">
            {past.map((h) => <Card key={h.id} h={h} busy={busyId === h.id} onSave={toggleSave} onCalendar={addToCalendar} />)}
          </div>
        </>
      )}
    </div>
  );
}

function Card({ h, busy, onSave, onCalendar }) {
  const dates = dateRange(h.eventStartDate, h.eventEndDate);
  const deadline = deadlineLabel(h);
  return (
    <article className={`hack-card${h.registrationOpen ? '' : ' closed'}`}>
      {h.bannerUrl && <img className="hack-banner" src={h.bannerUrl} alt="" loading="lazy" referrerPolicy="no-referrer" />}
      <div className="hack-body">
        <div className="hack-tags">
          <span className="badge">{MODE_LABEL[h.mode] || h.mode}</span>
          <span className="badge">{h.stream}</span>
          {h.featured && <span className="badge status-featured">Featured</span>}
        </div>
        <h3>{h.title}</h3>
        {h.organizer && <p className="hack-organizer">{h.organizer}</p>}
        {h.description && <p className="hack-desc">{h.description}</p>}
        <dl className="hack-facts">
          {dates && <div><dt><Icon name="clock" size={15} /> When</dt><dd>{dates}</dd></div>}
          {h.location && <div><dt><Icon name="map-pin" size={15} /> Where</dt><dd>{h.location}</dd></div>}
          {h.prizePool && <div><dt><Icon name="trophy" size={15} /> Prize</dt><dd>{h.prizePool}</dd></div>}
        </dl>
        {deadline && <p className={`hack-deadline${h.registrationOpen ? '' : ' over'}`}>{deadline}</p>}
        <div className="hack-actions">
          {h.registrationOpen ? (
            <a className="btn btn-primary" href={h.registrationUrl} target="_blank" rel="noopener noreferrer">
              Register <Icon name="arrow-right" size={16} />
            </a>
          ) : (
            <a className="btn" href={h.registrationUrl} target="_blank" rel="noopener noreferrer">View event</a>
          )}
          <button type="button" className="btn" disabled={busy} onClick={() => onSave(h)} aria-pressed={h.saved}>
            <Icon name={h.saved ? 'check' : 'plus'} size={16} /> {h.saved ? 'Saved' : 'Save'}
          </button>
          {h.eventStartDate && (
            <button type="button" className="btn" onClick={() => onCalendar(h)}>Add to calendar</button>
          )}
        </div>
      </div>
    </article>
  );
}
