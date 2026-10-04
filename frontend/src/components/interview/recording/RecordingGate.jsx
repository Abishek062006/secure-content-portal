import Icon from '../../Icon';
import { KEEP_RECORDINGS } from '../../../lib/localRecordings';

/** Shown before the first question when the learner chose to record: the plain facts, then a deliberate start. */
export default function RecordingGate({ onStart, onSkip, busy, error }) {
  return (
    <section className="recording-gate" aria-labelledby="recording-gate-title">
      <h2 id="recording-gate-title"><Icon name="video" size={18} /> Record this interview?</h2>
      <p>
        Your camera and microphone record the whole interview so you can watch it back afterwards. The video is saved only in this
        browser, on this device. It is never uploaded.
      </p>
      <ul className="recording-gate-notes">
        <li>Delete it any time. Only your latest {KEEP_RECORDINGS} recordings are kept.</li>
        <li>Use headphones, so the interviewer's voice isn't picked up by your microphone.</li>
        <li>If you clear this browser's site data, the recording is lost.</li>
      </ul>
      {error && <p className="field-error" role="alert">{error}</p>}
      <div className="recording-gate-actions">
        <button type="button" className="btn btn-primary btn-lg" onClick={onStart} disabled={busy}>
          {busy ? 'Starting...' : 'Start recording and begin'}
        </button>
        <button type="button" className="btn btn-lg" onClick={onSkip} disabled={busy}>Continue without recording</button>
      </div>
    </section>
  );
}
