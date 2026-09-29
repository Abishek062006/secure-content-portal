import { useRef, useState } from 'react';
import { api } from '../../api';
import Icon from '../Icon';

const RETENTION_DAYS = 90;

/** Upload, replace or delete the resume that interview questions can be built from.
 *  `embedded` drops the card chrome so it can sit inline inside another form/card. */
export default function ResumeCard({ resume, onChange, onError, embedded = false }) {
  const inputRef = useRef(null);
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);

  async function upload(file) {
    if (!file) return;
    setBusy(true);
    onError(null);
    try {
      onChange(await api.uploadResume(file, consent));
    } catch (err) {
      onError(err.message);
    } finally {
      setBusy(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  }

  async function remove() {
    setBusy(true);
    onError(null);
    try {
      await api.deleteResume();
      onChange(null);
      setConsent(false);
    } catch (err) {
      onError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const Wrapper = embedded ? 'div' : 'section';

  return (
    <Wrapper className={embedded ? 'resume-card' : 'progress-card resume-card'}>
      {!embedded && <header><h2>Your resume</h2></header>}

      {resume ? (
        <>
          <p className="resume-file"><Icon name="file-text" size={18} /> <strong>{resume.filename}</strong></p>
          <p className="field-hint">
            Uploaded {new Date(resume.uploadedAt).toLocaleDateString()}. Deleted automatically on {new Date(resume.expiresAt).toLocaleDateString()}.
          </p>
          <details className="resume-preview">
            <summary>What we read from it</summary>
            <pre>{resume.preview}</pre>
          </details>
          <div className="resume-actions">
            <button type="button" className="btn btn-sm btn-danger" onClick={remove} disabled={busy}>Delete my resume</button>
          </div>
        </>
      ) : (
        <p className="field-hint">Upload a PDF and your questions will be about your own projects and experience.</p>
      )}

      <label className="board-toggle">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>
          I agree to GradientNovaAI reading the text of my resume to write my interview questions. Only the text is kept, only I can see
          it, and it is deleted after {RETENTION_DAYS} days or whenever I delete it.
        </span>
      </label>
      <input ref={inputRef} type="file" accept="application/pdf,.pdf" hidden onChange={(e) => upload(e.target.files?.[0])} />
      <button type="button" className="btn" onClick={() => inputRef.current?.click()} disabled={busy || !consent}>
        <Icon name="upload" size={16} /> {busy ? 'Reading your resume...' : resume ? 'Replace resume' : 'Upload PDF'}
      </button>
      <p className="field-hint">PDF only, up to 10 pages and 3 MB. A text-based PDF works best.</p>
    </Wrapper>
  );
}
