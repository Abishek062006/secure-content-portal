import Icon from '../../Icon';
import { FRAMING_ADVICE, LIGHTING_ADVICE } from '../../../lib/frameChecks';

/** How long a problem has to last before the learner is told, so a glance away or a passing shadow doesn't nag. */
const FACE_MISSING_SECONDS = 4;
const LIGHTING_BAD_SECONDS = 8;

/** A quiet note when the camera can't see the learner's face or the lighting has gone bad, and it goes away once that's fixed. */
export default function SetupNotice({ frame }) {
  if (!frame) return null;
  let text = null;
  if (frame.faceCheck === 'ready' && frame.faceMissingSeconds >= FACE_MISSING_SECONDS) text = FRAMING_ADVICE.none;
  else if (frame.lightingBadSeconds >= LIGHTING_BAD_SECONDS && frame.lighting && frame.lighting !== 'ok') text = LIGHTING_ADVICE[frame.lighting];
  if (!text) return null;
  return <p className="setup-notice" role="status"><Icon name="info" size={16} /> {text}</p>;
}
