import { useEffect, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import Modal from './Modal';

/** "Tell me more, contact me" — on every course regardless of free/paid/register. Prefills from the
 *  account, but the learner can give a different email or add a phone number to actually be reached on. */
export default function EnquiryModal({ open, courseId, courseTitle, onClose }) {
  const { user } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [sent, setSent] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName(user?.displayName || '');
    setEmail(user?.email || '');
    setPhone('');
    setMessage('');
    setError(null);
    setSent(false);
  }, [open, user]);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.post(`/api/courses/${courseId}/enquiry`, { name, email, phone, message });
      setSent(true);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} title={`Enquire about ${courseTitle || 'this course'}`} onClose={onClose}>
      {sent ? (
        <div className="enquiry-sent">
          <p>Sent. Someone from the team will get back to you.</p>
          <div className="form-actions"><button type="button" className="btn btn-primary" onClick={onClose}>Close</button></div>
        </div>
      ) : (
        <form className="enquiry-form" onSubmit={submit}>
          {error && <p className="form-error">{error}</p>}
          <div className="field">
            <label htmlFor="enq-name">Name</label>
            <input id="enq-name" type="text" maxLength={200} required value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="enq-email">Email</label>
            <input id="enq-email" type="email" maxLength={320} required value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="enq-phone">Phone (optional)</label>
            <input id="enq-phone" type="tel" maxLength={32} value={phone} placeholder="For a quicker reply"
                   onChange={(e) => setPhone(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="enq-message">What would you like to know?</label>
            <textarea id="enq-message" rows={4} maxLength={1000} value={message}
                      placeholder="Optional — anything specific you want answered before deciding" onChange={(e) => setMessage(e.target.value)} />
          </div>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Sending…' : 'Send'}</button>
          </div>
        </form>
      )}
    </Modal>
  );
}
