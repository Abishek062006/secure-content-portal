import { useEffect, useRef, useState } from 'react';
import Icon from '../../Icon';
import { FRAMING_ADVICE, LIGHTING_ADVICE } from '../../../lib/frameChecks';
import { FrameMonitor } from '../../../lib/frameMonitor';
import { MicMeter } from '../../../lib/micMeter';

const ICON = { ok: 'check-circle', warn: 'info', pending: 'clock' };

function Check({ status, children }) {
  return (
    <li className={`setup-check-item ${status}`}>
      <Icon name={ICON[status]} size={18} />
      <div>{children}</div>
    </li>
  );
}

/** Level bar position, 0 to 1, from a loudness in dB below full scale. */
const barWidth = (db) => Math.max(0, Math.min(1, (db + 70) / 55));

/**
 * A live look at how the camera and microphone will do: the learner sees themselves and a tick or a plain tip for the lighting, how
 * they sit in the frame, and whether the microphone hears them. Everything is worked out in the browser; nothing is sent anywhere.
 */
export default function SetupCheck({ stream }) {
  const video = useRef(null);
  const [frame, setFrame] = useState(null);
  const [mic, setMic] = useState(null);
  const [heard, setHeard] = useState(false);

  useEffect(() => {
    if (video.current) video.current.srcObject = stream;
  }, [stream]);

  useEffect(() => {
    const monitor = new FrameMonitor(stream, { intervalMs: 700, onUpdate: setFrame });
    monitor.start();
    const meter = new MicMeter(stream, (level) => {
      setMic(level);
      if (level.peakDb - level.floorDb >= 15 && level.peakDb > -40) setHeard(true);
    });
    meter.start();
    return () => {
      monitor.stop();
      meter.stop();
    };
  }, [stream]);

  const lighting = frame?.lighting;
  const framing = frame?.framing;
  const noisy = mic && mic.floorDb != null && mic.floorDb > -35;

  return (
    <div className="setup-check">
      <video ref={video} className="setup-preview" muted playsInline autoPlay aria-label="Your camera" />
      <ul className="setup-check-list" aria-live="polite">
        <Check status="ok"><strong>Camera is on.</strong></Check>

        {lighting == null
          ? <Check status="pending">Checking the lighting...</Check>
          : <Check status={lighting === 'ok' ? 'ok' : 'warn'}>{LIGHTING_ADVICE[lighting]}</Check>}

        {frame?.faceCheck === 'unavailable' && (
          <Check status="warn">The face check couldn't load, so we can't tell how you sit in the frame. You can still record.</Check>
        )}
        {frame?.faceCheck !== 'unavailable' && (framing == null
          ? <Check status="pending">Loading the face check (the first time takes a moment)...</Check>
          : <Check status={framing === 'ok' ? 'ok' : 'warn'}>{FRAMING_ADVICE[framing]}</Check>)}

        <Check status={heard ? 'ok' : 'pending'}>
          <span>{heard ? 'Your microphone is picking you up.' : 'Say a few words to test your microphone.'}</span>
          <span className="mic-meter" role="img" aria-label="Microphone level">
            <span style={{ width: `${Math.round(barWidth(mic?.db ?? -80) * 100)}%` }} />
          </span>
          {noisy && <span className="field-hint">The room sounds noisy. A quieter spot helps your voice answers get measured.</span>}
        </Check>
      </ul>
      <p className="field-hint setup-check-note">
        Wear headphones if you can, so the interviewer's voice isn't picked up by your microphone. These checks run in your browser and the
        picture never leaves your device.
      </p>
    </div>
  );
}
