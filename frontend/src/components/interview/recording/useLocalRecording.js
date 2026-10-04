import { useEffect, useState } from 'react';
import { getRecording } from '../../../lib/localRecordings';

/**
 * The learner's recording of this interview if it is on this device, else null. A recording with nothing saved in it, or one
 * made by a different user of the same browser, doesn't count.
 */
export function useLocalRecording(sessionId, userId) {
  const [meta, setMeta] = useState(null);

  useEffect(() => {
    if (userId == null) return undefined;
    let live = true;
    getRecording(sessionId)
      .then((found) => { if (live) setMeta(found && found.userId === userId && found.sizeBytes > 0 ? found : null); })
      .catch(() => {});
    return () => { live = false; };
  }, [sessionId, userId]);

  return meta;
}
