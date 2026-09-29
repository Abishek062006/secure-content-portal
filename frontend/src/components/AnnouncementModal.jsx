import { useState } from 'react';
import { api } from '../api';
import Modal from './Modal';

/** Admin-only broadcast: fires an ANNOUNCEMENT notification to the chosen audience. */
export default function AnnouncementModal({ open, onClose, onSent }) {
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [priority, setPriority] = useState('IMPORTANT');
  const [actionUrl, setActionUrl] = useState('');
  const [targetAudience, setTargetAudience] = useState('ALL');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  function reset() {
    setTitle('');
    setMessage('');
    setPriority('IMPORTANT');
    setActionUrl('');
    setTargetAudience('ALL');
    setError(null);
  }

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.sendAdminAnnouncement({ title, message, priority, actionUrl: actionUrl || null, targetAudience });
      reset();
      onSent();
      onClose();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} title="Broadcast announcement" onClose={() => { reset(); onClose(); }}>
      <form className="announcement-form" onSubmit={submit}>
        {error && <p className="form-error">{error}</p>}
        <div className="field">
          <label htmlFor="ann-title">Title</label>
          <input id="ann-title" type="text" maxLength={200} required value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="ann-message">Message</label>
          <textarea id="ann-message" rows={3} required value={message} onChange={(e) => setMessage(e.target.value)} />
        </div>
        <div className="form-row">
          <div className="field">
            <label htmlFor="ann-audience">Audience</label>
            <select id="ann-audience" value={targetAudience} onChange={(e) => setTargetAudience(e.target.value)}>
              <option value="ALL">Everyone</option>
              <option value="LEARNERS">Learners only</option>
              <option value="ADMINS">Admins only</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="ann-priority">Priority</label>
            <select id="ann-priority" value={priority} onChange={(e) => setPriority(e.target.value)}>
              <option value="NORMAL">Normal</option>
              <option value="IMPORTANT">Important</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </div>
        </div>
        <div className="field">
          <label htmlFor="ann-action">Link (optional)</label>
          <input id="ann-action" type="text" placeholder="e.g. /courses" value={actionUrl} onChange={(e) => setActionUrl(e.target.value)} />
        </div>
        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={busy || !title.trim() || !message.trim()}>
            {busy ? 'Sending…' : 'Broadcast'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
