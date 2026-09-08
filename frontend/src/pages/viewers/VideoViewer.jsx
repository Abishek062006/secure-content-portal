import { useState } from 'react';
import { API_BASE } from '../../api';

export default function VideoViewer({ ticket }) {
  const [failed, setFailed] = useState(false);

  return (
    <>
      <video
        controls
        controlsList="nodownload noremoteplayback"
        disablePictureInPicture
        className="media-player"
        src={`${API_BASE}/api/stream/${ticket}`}
        onError={() => setFailed(true)}
      >
        Your browser doesn't support inline video.
      </video>
      {failed && <p className="field-error">This video could not be loaded. Try refreshing the page.</p>}
    </>
  );
}
