import { useEffect, useRef, useState } from 'react';
import Icon from '../../Icon';

/** "Recording" in the interview's top bar, so it is never unclear that the camera is on. */
export function RecordingPill() {
  return <span className="rec-pill" role="status"><i aria-hidden="true" /> Recording</span>;
}

/** A small mirrored view of the learner's own camera, like a video call, so they can see how they're framed. It can be hidden. */
export default function SelfView({ stream }) {
  const video = useRef(null);
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    if (video.current) video.current.srcObject = hidden ? null : stream;
  }, [stream, hidden]);

  if (!stream) return null;
  if (hidden) {
    return <button type="button" className="selfview-show btn btn-sm" onClick={() => setHidden(false)}><Icon name="camera" size={14} /> Show my camera</button>;
  }
  return (
    <div className="selfview">
      <video ref={video} muted playsInline autoPlay aria-label="Your camera" />
      <button type="button" onClick={() => setHidden(true)} aria-label="Hide my camera" title="Hide my camera"><Icon name="x" size={14} /></button>
    </div>
  );
}
