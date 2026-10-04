import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';
import ConfirmDialog from '../../components/ConfirmDialog';
import Modal from '../../components/Modal';
import { MODE_LABEL, PHASE_LABEL, dateRange } from '../../lib/hackathons';

import Icon from '../../components/Icon';

const EMPTY = {
  title: '', organizer: '', description: '', bannerUrl: '', stream: '', mode: 'ONLINE', location: '', prizePool: '',
  registrationUrl: '', registrationDeadline: '', eventStartDate: '', eventEndDate: '', featured: false, status: 'UPCOMING',
  kind: 'HOSTED', rules: '', tracks: '', prizes: '', minTeamSize: 1, maxTeamSize: 4,
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
    minTeamSize: h.minTeamSize || 1, maxTeamSize: h.maxTeamSize || 4,
  };
}

function toPayload(form) {
  return {
    ...form,
    organizer: form.organizer || null, description: form.description || null, bannerUrl: form.bannerUrl || null,
    location: form.location || null, prizePool: form.prizePool || null, registrationUrl: form.registrationUrl || null,
    rules: form.rules || null, tracks: form.tracks || null, prizes: form.prizes || null,
    minTeamSize: Number(form.minTeamSize) || 1, maxTeamSize: Number(form.maxTeamSize) || 4,
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

  const isHosted = form.kind === 'HOSTED';
  const isEditing = Boolean(form.id);

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
    <form className="modal-form hack-form-container" onSubmit={submit}>
      <Alert error={error} />

      {/* Event Type Selector */}
      <div className="hack-kind-selector">
        <div
          className={`hack-kind-card ${isHosted ? 'active' : ''}`}
          onClick={() => { if (!isEditing) setForm((prev) => ({ ...prev, kind: 'HOSTED' })); }}
          role="button"
          tabIndex={0}
        >
          <input
            type="radio"
            name="kind"
            value="HOSTED"
            checked={isHosted}
            disabled={isEditing}
            onChange={() => {}}
          />
          <span className="hack-kind-badge">Full Platform Hackathon</span>
          <div className="hack-kind-name">Hosted Event</div>
          <p className="hack-kind-hint">
            Teams form, choose problem statements, build in a workspace, submit projects, and receive judge evaluations &amp; certificates right here.
          </p>
        </div>

        <div
          className={`hack-kind-card ${!isHosted ? 'active' : ''}`}
          onClick={() => { if (!isEditing) setForm((prev) => ({ ...prev, kind: 'EXTERNAL' })); }}
          role="button"
          tabIndex={0}
        >
          <input
            type="radio"
            name="kind"
            value="EXTERNAL"
            checked={!isHosted}
            disabled={isEditing}
            onChange={() => {}}
          />
          <span className="hack-kind-badge">External Website</span>
          <div className="hack-kind-name">External Listing</div>
          <p className="hack-kind-hint">
            Promote an event hosted elsewhere. Learners are directed to register and submit on the organiser&apos;s external website.
          </p>
        </div>
      </div>

      {isEditing && (
        <p className="field-hint" style={{ marginTop: '-6px', marginBottom: '8px' }}>
          * Event type (Hosted vs External) cannot be changed once created.
        </p>
      )}

      {isHosted ? (
        /* =========================================================================
           HOSTED EVENT: 6 Clean Sections
           ========================================================================= */
        <>
          {/* 1. Basic Details */}
          <div className="hack-form-section">
            <div className="hack-section-head">
              <span className="hack-section-num">1</span>
              <div className="hack-section-title-wrap">
                <h3 className="hack-section-title">Basic Details</h3>
                <p className="hack-section-desc">Title, organiser, overview description and branding</p>
              </div>
            </div>

            <div className="field">
              <label htmlFor="h-title">Title *</label>
              <input
                id="h-title"
                type="text"
                maxLength={200}
                value={form.title}
                onChange={set('title')}
                placeholder="e.g. NextGen AI Innovation Challenge 2026"
                required
              />
            </div>

            <div className="field">
              <label htmlFor="h-organizer">Organiser (optional)</label>
              <input
                id="h-organizer"
                type="text"
                maxLength={200}
                value={form.organizer}
                onChange={set('organizer')}
                placeholder="e.g. GradientNova AI or Partner Organization"
              />
            </div>

            <div className="field">
              <label htmlFor="h-desc">Description (optional)</label>
              <textarea
                id="h-desc"
                rows={4}
                maxLength={5000}
                value={form.description}
                onChange={set('description')}
                placeholder="Overview, mission, and background of the hackathon..."
              />
            </div>

            <div className="form-row">
              <div className="field">
                <label htmlFor="h-banner-file">Banner Image File</label>
                <input
                  id="h-banner-file"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  onChange={(e) => setBanner(e.target.files?.[0] || null)}
                />
                <p className="field-hint">PNG, JPG or WebP, up to 5 MB.{form.id ? ' Choose file only to replace existing banner.' : ''}</p>
              </div>
              <div className="field">
                <label htmlFor="h-banner">Or Banner Image URL (optional)</label>
                <input
                  id="h-banner"
                  type="text"
                  maxLength={500}
                  value={form.bannerUrl}
                  onChange={set('bannerUrl')}
                  placeholder="https://images.example.com/banner.jpg"
                />
                <p className="field-hint">Must start with https:// if provided.</p>
              </div>
            </div>

            <label className="board-toggle" style={{ marginTop: '12px' }}>
              <input type="checkbox" checked={form.featured} onChange={set('featured')} />
              <span>Feature this hackathon (highlight at top of explore listings &amp; home)</span>
            </label>
          </div>

          {/* 2. Schedule */}
          <div className="hack-form-section">
            <div className="hack-section-head">
              <span className="hack-section-num">2</span>
              <div className="hack-section-title-wrap">
                <h3 className="hack-section-title">Schedule</h3>
                <p className="hack-section-desc">Timeline for registration, building, and automated deadline enforcement</p>
              </div>
            </div>

            <div className="hack-schedule-guide">
              <span className="hack-schedule-step">Registration Closes</span>
              <span className="hack-schedule-arrow">&rarr;</span>
              <span className="hack-schedule-step">Building Starts</span>
              <span className="hack-schedule-arrow">&rarr;</span>
              <span className="hack-schedule-step">Submissions Close &amp; Judging</span>
            </div>

            <div className="form-row">
              <div className="field">
                <label htmlFor="h-deadline">Registration Closes *</label>
                <input
                  id="h-deadline"
                  type="datetime-local"
                  value={form.registrationDeadline}
                  onChange={set('registrationDeadline')}
                  required
                />
                <p className="field-hint">Learners can form and join teams until this deadline.</p>
              </div>

              <div className="field">
                <label htmlFor="h-start">Building Starts *</label>
                <input
                  id="h-start"
                  type="datetime-local"
                  value={form.eventStartDate}
                  onChange={set('eventStartDate')}
                  required
                />
                <p className="field-hint">When project workspaces open and building begins.</p>
              </div>
            </div>

            <div className="field" style={{ marginTop: '12px' }}>
              <label htmlFor="h-end">Submissions Close (Strict Deadline) *</label>
              <input
                id="h-end"
                type="datetime-local"
                value={form.eventEndDate}
                onChange={set('eventEndDate')}
                required
              />
              <p className="field-hint" style={{ color: 'var(--accent-deep, #4338ca)' }}>
                <strong>Strict Deadline:</strong> Submissions automatically lock once this timestamp passes. Judges begin evaluation immediately after.
              </p>
            </div>
          </div>

          {/* 3. Participation */}
          <div className="hack-form-section">
            <div className="hack-section-head">
              <span className="hack-section-num">3</span>
              <div className="hack-section-title-wrap">
                <h3 className="hack-section-title">Participation</h3>
                <p className="hack-section-desc">Team capacity constraints and topic tracks</p>
              </div>
            </div>

            <div className="form-row">
              <div className="field">
                <label htmlFor="h-min">Smallest Team (Min Members) *</label>
                <input
                  id="h-min"
                  type="number"
                  min="1"
                  max="10"
                  value={form.minTeamSize}
                  onChange={set('minTeamSize')}
                  required
                />
                <p className="field-hint">Minimum required members to submit (1–10).</p>
              </div>

              <div className="field">
                <label htmlFor="h-max">Largest Team (Max Members) *</label>
                <input
                  id="h-max"
                  type="number"
                  min="1"
                  max="10"
                  value={form.maxTeamSize}
                  onChange={set('maxTeamSize')}
                  required
                />
                <p className="field-hint">Maximum members allowed per team (1–10).</p>
              </div>
            </div>

            <div className="field" style={{ marginTop: '12px' }}>
              <label htmlFor="h-tracks">Tracks (optional, separated by commas)</label>
              <input
                id="h-tracks"
                type="text"
                maxLength={500}
                value={form.tracks}
                onChange={set('tracks')}
                placeholder="e.g. AI & Machine Learning, Web3, FinTech, Open Innovation"
              />
              <p className="field-hint">Up to 8 tracks. Each team picks one track for their project.</p>
            </div>
          </div>

          {/* 4. Event Details */}
          <div className="hack-form-section">
            <div className="hack-section-head">
              <span className="hack-section-num">4</span>
              <div className="hack-section-title-wrap">
                <h3 className="hack-section-title">Event Details</h3>
                <p className="hack-section-desc">Technical domain, format, and venue location</p>
              </div>
            </div>

            <div className="form-row">
              <div className="field">
                <label htmlFor="h-stream">Stream *</label>
                <input
                  id="h-stream"
                  type="text"
                  maxLength={100}
                  value={form.stream}
                  onChange={set('stream')}
                  placeholder="e.g. AI & Data Science"
                  required
                />
              </div>

              <div className="field">
                <label htmlFor="h-mode">Format *</label>
                <select id="h-mode" value={form.mode} onChange={set('mode')} required>
                  {Object.entries(MODE_LABEL).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="field" style={{ marginTop: '12px' }}>
              <label htmlFor="h-location">Location (optional)</label>
              <input
                id="h-location"
                type="text"
                maxLength={200}
                value={form.location}
                onChange={set('location')}
                placeholder="e.g. Online (Discord / Zoom) or Physical Venue Address"
              />
            </div>
          </div>

          {/* 5. Rewards */}
          <div className="hack-form-section">
            <div className="hack-section-head">
              <span className="hack-section-num">5</span>
              <div className="hack-section-title-wrap">
                <h3 className="hack-section-title">Rewards</h3>
                <p className="hack-section-desc">Prize pool and reward breakdown for winners</p>
              </div>
            </div>

            <div className="field">
              <label htmlFor="h-prize">Prize Pool (optional)</label>
              <input
                id="h-prize"
                type="text"
                maxLength={100}
                value={form.prizePool}
                onChange={set('prizePool')}
                placeholder="e.g. $10,000 or ₹2,00,000 in prizes"
              />
            </div>

            <div className="field">
              <label htmlFor="h-prizes">Prizes (optional)</label>
              <textarea
                id="h-prizes"
                rows={3}
                maxLength={2000}
                value={form.prizes}
                onChange={set('prizes')}
                placeholder="e.g. 1st Place: $5,000 + Trophy, 2nd Place: $3,000, 3rd Place: $2,000. All valid final submissions receive digital certificates."
              />
            </div>
          </div>

          {/* 6. Rules */}
          <div className="hack-form-section">
            <div className="hack-section-head">
              <span className="hack-section-num">6</span>
              <div className="hack-section-title-wrap">
                <h3 className="hack-section-title">Rules</h3>
                <p className="hack-section-desc">Participation rules, guidelines, and code of conduct</p>
              </div>
            </div>

            <div className="field">
              <label htmlFor="h-rules">Hackathon Rules *</label>
              <textarea
                id="h-rules"
                rows={5}
                maxLength={5000}
                value={form.rules}
                onChange={set('rules')}
                placeholder="1. All code must be built during the building phase.&#10;2. Projects must include accessible code repositories.&#10;3. Follow code of conduct and integrity guidelines..."
                required
              />
              <p className="field-hint">Required for hosted events. Displayed to participating teams in their workspace.</p>
            </div>
          </div>

          {/* Post-creation management notice */}
          <div className="hack-next-steps-banner">
            <Icon name="info" size={20} style={{ flexShrink: 0, marginTop: '2px' }} />
            <div>
              <strong>Manage After Creation:</strong>
              <p style={{ margin: '4px 0 0', fontSize: '0.85rem' }}>
                Problem Statements, Judge assignments, and Publishing Results are managed from the <strong>Manage</strong> page after this hackathon is created.
              </p>
            </div>
          </div>
        </>
      ) : (
        /* =========================================================================
           EXTERNAL LISTING: Separate, Simple Layout
           ========================================================================= */
        <>
          <div className="hack-form-section">
            <div className="hack-section-head">
              <div className="hack-section-title-wrap">
                <h3 className="hack-section-title">External Event Details</h3>
                <p className="hack-section-desc">Event title, organiser, format, and overview</p>
              </div>
            </div>

            <div className="field">
              <label htmlFor="h-title">Title *</label>
              <input
                id="h-title"
                type="text"
                maxLength={200}
                value={form.title}
                onChange={set('title')}
                placeholder="e.g. Global Hackathon 2026"
                required
              />
            </div>

            <div className="form-row">
              <div className="field">
                <label htmlFor="h-organizer">Organiser</label>
                <input
                  id="h-organizer"
                  type="text"
                  maxLength={200}
                  value={form.organizer}
                  onChange={set('organizer')}
                  placeholder="e.g. Major League Hacking"
                />
              </div>

              <div className="field">
                <label htmlFor="h-stream">Stream *</label>
                <input
                  id="h-stream"
                  type="text"
                  maxLength={100}
                  value={form.stream}
                  onChange={set('stream')}
                  placeholder="e.g. Web Development"
                  required
                />
              </div>
            </div>

            <div className="form-row">
              <div className="field">
                <label htmlFor="h-mode">Format *</label>
                <select id="h-mode" value={form.mode} onChange={set('mode')}>
                  {Object.entries(MODE_LABEL).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
              </div>

              <div className="field">
                <label htmlFor="h-status">Status *</label>
                <select id="h-status" value={form.status} onChange={set('status')}>
                  <option value="UPCOMING">Upcoming</option>
                  <option value="ACTIVE">Active</option>
                  <option value="COMPLETED">Completed</option>
                </select>
              </div>
            </div>

            <div className="field" style={{ marginTop: '10px' }}>
              <label htmlFor="h-desc">Description</label>
              <textarea
                id="h-desc"
                rows={4}
                maxLength={5000}
                value={form.description}
                onChange={set('description')}
                placeholder="Overview and details of the external hackathon..."
              />
            </div>

            <label className="board-toggle" style={{ marginTop: '10px' }}>
              <input type="checkbox" checked={form.featured} onChange={set('featured')} />
              <span>Feature this hackathon (show at top of explore listings)</span>
            </label>
          </div>

          <div className="hack-form-section">
            <div className="hack-section-head">
              <div className="hack-section-title-wrap">
                <h3 className="hack-section-title">Registration Link</h3>
                <p className="hack-section-desc">External URL where learners register on the organiser&apos;s site</p>
              </div>
            </div>

            <div className="field">
              <label htmlFor="h-url">Registration Link *</label>
              <input
                id="h-url"
                type="text"
                maxLength={1000}
                value={form.registrationUrl}
                onChange={set('registrationUrl')}
                placeholder="https://"
                required
              />
              <p className="field-hint">The organiser&apos;s official registration page. Must start with https://.</p>
            </div>

            <div className="field" style={{ marginTop: '10px' }}>
              <label htmlFor="h-deadline">Registration Closes</label>
              <input
                id="h-deadline"
                type="datetime-local"
                value={form.registrationDeadline}
                onChange={set('registrationDeadline')}
              />
            </div>
          </div>

          <div className="hack-form-section">
            <div className="hack-section-head">
              <div className="hack-section-title-wrap">
                <h3 className="hack-section-title">Schedule, Location &amp; Banner</h3>
                <p className="hack-section-desc">Date range, prize pool, and event banner</p>
              </div>
            </div>

            <div className="form-row">
              <div className="field">
                <label htmlFor="h-start">Starts</label>
                <input
                  id="h-start"
                  type="datetime-local"
                  value={form.eventStartDate}
                  onChange={set('eventStartDate')}
                />
              </div>

              <div className="field">
                <label htmlFor="h-end">Ends</label>
                <input
                  id="h-end"
                  type="datetime-local"
                  value={form.eventEndDate}
                  onChange={set('eventEndDate')}
                />
              </div>
            </div>

            <div className="form-row" style={{ marginTop: '10px' }}>
              <div className="field">
                <label htmlFor="h-location">Location</label>
                <input
                  id="h-location"
                  type="text"
                  maxLength={200}
                  value={form.location}
                  onChange={set('location')}
                  placeholder="e.g. San Francisco or Virtual"
                />
              </div>

              <div className="field">
                <label htmlFor="h-prize">Prize Pool</label>
                <input
                  id="h-prize"
                  type="text"
                  maxLength={100}
                  value={form.prizePool}
                  onChange={set('prizePool')}
                  placeholder="e.g. $50,000"
                />
              </div>
            </div>

            <div className="form-row" style={{ marginTop: '10px' }}>
              <div className="field">
                <label htmlFor="h-banner-file">Banner Image File</label>
                <input
                  id="h-banner-file"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  onChange={(e) => setBanner(e.target.files?.[0] || null)}
                />
                <p className="field-hint">PNG, JPG or WebP, up to 5 MB.</p>
              </div>
              <div className="field">
                <label htmlFor="h-banner">Or Banner Image URL (optional)</label>
                <input
                  id="h-banner"
                  type="text"
                  maxLength={500}
                  value={form.bannerUrl}
                  onChange={set('bannerUrl')}
                  placeholder="https://"
                />
              </div>
            </div>
          </div>
        </>
      )}

      {/* Form Actions Footer */}
      <div className="form-actions" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '8px' }}>
        <button type="button" className="btn" onClick={onClose} disabled={saving}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Saving...' : (form.id ? 'Save changes' : (isHosted ? 'Create Hosted Hackathon' : 'Create External Listing'))}
        </button>
      </div>
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

      <Modal open={editing !== null} title={editing?.id ? (editing.kind === 'HOSTED' ? 'Edit Hosted Hackathon' : 'Edit External Hackathon') : 'Create Hackathon'} onClose={() => setEditing(null)} wide>
        {editing && <HackathonForm initial={editing} onSaved={load} onClose={() => setEditing(null)} />}
      </Modal>
      <ConfirmDialog open={toDelete !== null} title={toDelete?.title} detail="Learners' saves of it are removed too."
                     onCancel={() => setToDelete(null)} onConfirm={remove} />
    </div>
  );
}
