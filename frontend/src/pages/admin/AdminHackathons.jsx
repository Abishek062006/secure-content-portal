import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';
import ConfirmDialog from '../../components/ConfirmDialog';
import Modal from '../../components/Modal';
import { MODE_LABEL, PHASE_LABEL, dateRange } from '../../lib/hackathons';

const EMPTY = {
  title: '', organizer: '', description: '', bannerUrl: '', stream: '', mode: 'ONLINE', location: '', prizePool: '',
  registrationUrl: '', registrationDeadline: '', eventStartDate: '', eventEndDate: '', featured: false, status: 'UPCOMING',
  kind: 'EXTERNAL', rules: '', tracks: '', prizes: '', minTeamSize: 1, maxTeamSize: 4,
};

/** An ISO instant as the local value a datetime-local input wants, and back. */
function toInput(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
const toIso = (value) => (value ? new Date(value).toISOString() : null);

function toForm(h) {
  return {
    ...EMPTY, ...h,
    organizer: h.organizer || '', description: h.description || '', bannerUrl: h.bannerUrl || '', location: h.location || '',
    prizePool: h.prizePool || '', rules: h.rules || '', tracks: h.tracks || '', prizes: h.prizes || '', registrationUrl: h.registrationUrl || '',
    registrationDeadline: toInput(h.registrationDeadline),
    eventStartDate: toInput(h.eventStartDate), eventEndDate: toInput(h.eventEndDate),
  };
}

function toPayload(form) {
  return {
    ...form,
    organizer: form.organizer || null, description: form.description || null, bannerUrl: form.bannerUrl || null,
    location: form.location || null, prizePool: form.prizePool || null, registrationUrl: form.registrationUrl || null,
    rules: form.rules || null, tracks: form.tracks || null, prizes: form.prizes || null,
    minTeamSize: Number(form.minTeamSize), maxTeamSize: Number(form.maxTeamSize),
    registrationDeadline: toIso(form.registrationDeadline), eventStartDate: toIso(form.eventStartDate),
    eventEndDate: toIso(form.eventEndDate),
  };
}

function HackathonForm({ initial, onSaved, onClose }) {
  const [form, setForm] = useState(initial);
  const [banner, setBanner] = useState(null);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  async function submit(e) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const payload = toPayload(form);
      const saved = form.id ? await api.updateAdminHackathon(form.id, payload) : await api.createAdminHackathon(payload);
      if (banner) await api.uploadHackathonBanner(saved.id, banner);
      await onSaved();
      onClose();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="modal-form" onSubmit={submit}>
      <div className="field"><label htmlFor="h-kind">Type</label>
        <select id="h-kind" value={form.kind} onChange={set('kind')} disabled={Boolean(form.id)}>
          <option value="EXTERNAL">External listing (registration happens on the organiser's site)</option>
          <option value="HOSTED">Hosted event (teams, submissions and judging happen here)</option>
        </select></div>
      <div className="field"><label htmlFor="h-title">Title</label>
        <input id="h-title" type="text" maxLength={200} value={form.title} onChange={set('title')} required /></div>
      <div className="field"><label htmlFor="h-organizer">Organiser</label>
        <input id="h-organizer" type="text" maxLength={200} value={form.organizer} onChange={set('organizer')} /></div>
      {form.kind === 'EXTERNAL' && (
        <div className="field"><label htmlFor="h-url">Registration link</label>
          <input id="h-url" type="text" maxLength={1000} value={form.registrationUrl} onChange={set('registrationUrl')}
                 placeholder="https://" required />
          <p className="field-hint">The organiser's own page. It must start with https://.</p></div>
      )}
      {form.kind === 'HOSTED' && (
        <>
          <div className="field"><label htmlFor="h-rules">Rules</label>
            <textarea id="h-rules" rows={5} maxLength={5000} value={form.rules} onChange={set('rules')} required /></div>
          <div className="field"><label htmlFor="h-tracks">Tracks (optional, separated by commas)</label>
            <input id="h-tracks" type="text" maxLength={500} value={form.tracks} onChange={set('tracks')} placeholder="e.g. Web, AI, Social good" />
            <p className="field-hint">When there are tracks, each team picks one.</p></div>
          <div className="field"><label htmlFor="h-prizes">Prizes (optional)</label>
            <textarea id="h-prizes" rows={2} maxLength={2000} value={form.prizes} onChange={set('prizes')} /></div>
          <div className="form-row">
            <div className="field"><label htmlFor="h-min">Smallest team</label>
              <input id="h-min" type="number" min="1" max="10" value={form.minTeamSize} onChange={set('minTeamSize')} /></div>
            <div className="field"><label htmlFor="h-max">Largest team</label>
              <input id="h-max" type="number" min="1" max="10" value={form.maxTeamSize} onChange={set('maxTeamSize')} /></div>
          </div>
          <p className="field-hint">The event runs on its dates: teams form until registration closes, build from the start date, submit until the end date, then judges score.</p>
        </>
      )}
      <div className="field"><label htmlFor="h-stream">Stream</label>
        <input id="h-stream" type="text" maxLength={100} value={form.stream} onChange={set('stream')}
               placeholder="e.g. AI & Data Science" required /></div>
      <div className="form-row">
        <div className="field"><label htmlFor="h-mode">Format</label>
          <select id="h-mode" value={form.mode} onChange={set('mode')}>
            {Object.entries(MODE_LABEL).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select></div>
        {form.kind === 'EXTERNAL' && (
          <div className="field"><label htmlFor="h-status">Status</label>
            <select id="h-status" value={form.status} onChange={set('status')}>
              <option value="UPCOMING">Upcoming</option><option value="ACTIVE">Active</option><option value="COMPLETED">Completed</option>
            </select></div>
        )}
      </div>
      <div className="form-row">
        <div className="field"><label htmlFor="h-start">{form.kind === 'HOSTED' ? 'Building starts' : 'Starts'}</label>
          <input id="h-start" type="datetime-local" value={form.eventStartDate} onChange={set('eventStartDate')} /></div>
        <div className="field"><label htmlFor="h-end">{form.kind === 'HOSTED' ? 'Submissions close' : 'Ends'}</label>
          <input id="h-end" type="datetime-local" value={form.eventEndDate} onChange={set('eventEndDate')} /></div>
      </div>
      <div className="field"><label htmlFor="h-deadline">Registration closes</label>
        <input id="h-deadline" type="datetime-local" value={form.registrationDeadline} onChange={set('registrationDeadline')} /></div>
      <div className="form-row">
        <div className="field"><label htmlFor="h-location">Location</label>
          <input id="h-location" type="text" maxLength={200} value={form.location} onChange={set('location')} /></div>
        <div className="field"><label htmlFor="h-prize">Prize pool</label>
          <input id="h-prize" type="text" maxLength={100} value={form.prizePool} onChange={set('prizePool')} placeholder="e.g. Rs 2,00,000" /></div>
      </div>
      <div className="field"><label htmlFor="h-banner-file">Banner image</label>
        <input id="h-banner-file" type="file" accept="image/png,image/jpeg,image/webp" onChange={(e) => setBanner(e.target.files?.[0] || null)} />
        <p className="field-hint">PNG, JPG or WebP, up to 5 MB. {form.id && 'Choose a file only to replace the current banner.'}</p></div>
      <div className="field"><label htmlFor="h-banner">Or a banner image link (optional)</label>
        <input id="h-banner" type="text" maxLength={500} value={form.bannerUrl} onChange={set('bannerUrl')} placeholder="https://" /></div>
      <div className="field"><label htmlFor="h-desc">Description</label>
        <textarea id="h-desc" rows={5} maxLength={5000} value={form.description} onChange={set('description')} /></div>
      <label className="board-toggle"><input type="checkbox" checked={form.featured} onChange={set('featured')} /> Feature this hackathon</label>
      {error && <p className="form-error">{error}</p>}
      <div className="form-actions"><button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving...' : 'Save'}</button></div>
    </form>
  );
}

export default function AdminHackathons() {
  const [items, setItems] = useState(null);
  const [error, setError] = useState(null);
  const [editing, setEditing] = useState(null);
  const [toDelete, setToDelete] = useState(null);

  const load = () => api.getAdminHackathons().then(setItems).catch((err) => setError(err.message));
  useEffect(() => { load(); }, []);

  async function remove() {
    const target = toDelete;
    setToDelete(null);
    try {
      await api.deleteAdminHackathon(target.id);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="container-wide">
      <Alert error={error} />
      <div className="page-head">
        <h1 className="page-title">Hackathons</h1>
        <button type="button" className="btn btn-primary" onClick={() => setEditing(EMPTY)}>Add hackathon</button>
      </div>
      <p className="field-hint">Listings for real events run by other organisers. Learners save them and register on the organiser's site.</p>

      {items && items.length === 0 && <div className="empty-state"><p>No hackathons are listed yet.</p></div>}
      {items && items.length > 0 && (
        <div className="table-wrap">
          <table className="data-table">
            <thead><tr><th>Title</th><th>Format</th><th>When</th><th>Status</th><th /></tr></thead>
            <tbody>
              {items.map((h) => (
                <tr key={h.id}>
                  <td><strong>{h.title}</strong><br /><span className="field-hint">{h.organizer || h.stream}</span></td>
                  <td>{MODE_LABEL[h.mode] || h.mode}</td>
                  <td>{dateRange(h.eventStartDate, h.eventEndDate) || 'No date yet'}</td>
                  <td>{h.kind === 'HOSTED' ? `Hosted: ${(PHASE_LABEL[h.phase] || '').toLowerCase()}` : h.status.charAt(0) + h.status.slice(1).toLowerCase()}{h.featured ? ', featured' : ''}</td>
                  <td className="row-actions">
                    {h.kind === 'HOSTED' && <Link className="btn btn-sm" to={`/admin/hackathons/${h.id}/manage`}>Manage</Link>}
                    <Link className="btn btn-sm" to={`/feed?hackathon=${h.id}`}>Share to feed</Link>
                    <button type="button" className="btn btn-sm" onClick={() => setEditing(toForm(h))}>Edit</button>
                    <button type="button" className="btn btn-sm btn-danger" onClick={() => setToDelete(h)}>Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={editing !== null} title={editing?.id ? 'Edit hackathon' : 'Add hackathon'} onClose={() => setEditing(null)} wide>
        {editing && <HackathonForm initial={editing} onSaved={load} onClose={() => setEditing(null)} />}
      </Modal>
      <ConfirmDialog open={toDelete !== null} title={toDelete?.title} detail="Learners' saves of it are removed too."
                     onCancel={() => setToDelete(null)} onConfirm={remove} />
    </div>
  );
}
