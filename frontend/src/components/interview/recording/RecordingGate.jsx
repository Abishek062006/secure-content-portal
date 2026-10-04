import Icon from '../../Icon';
import { KEEP_RECORDINGS } from '../../../lib/localRecordings';
import SetupCheck from './SetupCheck';

/**
 * Shown before the first question when the learner chose to record. First the plain facts and a button to open the camera, then a
 * live check of the setup, then a deliberate start. Recording never begins on its own.
 */
export default function RecordingGate({ stream, opening, starting, error, onPreview, onStart, onSkip }) {
  return (
    <section className="recording-gate" aria-labelledby="recording-gate-title">
      <h2 id="recording-gate-title"><Icon name="video" size={18} /> {stream ? 'Check your setup' : 'Record this interview?'}</h2>

      {!stream && (
        <>
          <p>
            Your camera and microphone record the whole interview so you can watch it back afterwards. The video is saved only in this
            browser, on this device. It is never uploaded.
          </p>
          <ul className="recording-gate-notes">
            <li>First we check your lighting and how you sit in the frame, so the recording comes out well.</li>
            <li>Delete it any time. Only your latest {KEEP_RECORDINGS} recordings are kept.</li>
            <li>If you clear this browser's site data, the recording is lost.</li>
          </ul>
        </>
      )}

      {stream && <SetupCheck stream={stream} />}

      {error && <p className="field-error" role="alert">{error}</p>}
      <div className="recording-gate-actions">
        {stream ? (
          <button type="button" className="btn btn-primary btn-lg" onClick={onStart} disabled={starting}>
            {starting ? 'Starting...' : 'Start recording and begin'}
          </button>
        ) : (
          <button type="button" className="btn btn-primary btn-lg" onClick={onPreview} disabled={opening}>
            {opening ? 'Opening your camera...' : 'Check my setup'}
          </button>
        )}
        <button type="button" className="btn btn-lg" onClick={onSkip} disabled={starting || opening}>Continue without recording</button>
      </div>
    </section>
  );
}
