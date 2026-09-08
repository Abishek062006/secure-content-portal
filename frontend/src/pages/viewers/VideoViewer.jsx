import { useEffect, useMemo, useRef, useState } from 'react';
import { API_BASE } from '../../api';

/**
 * A visible deterrent, not a security boundary — this is CSS drawn on top of
 * the player, not burned into the video's pixels. It matches PdfRenderService's
 * watermark on the backend (same angle, same translucency, same idea: tile the
 * viewer's own email across the content) but can't be, since real per-viewer
 * frame-by-frame transcoding is a different order of engineering than
 * rasterizing one PDF page — see the README's content-protection section for
 * the honest version of this trade-off.
 */
function buildWatermarkTile(text) {
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="280" height="150">
    <text x="140" y="80" font-family="Inter, -apple-system, sans-serif" font-size="14"
      font-weight="600" fill="rgba(255,255,255,0.32)" stroke="rgba(0,0,0,0.35)" stroke-width="0.6"
      text-anchor="middle" transform="rotate(-30 140 80)">${escaped}</text>
  </svg>`;
  return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
}

export default function VideoViewer({ ticket, viewerEmail }) {
  const [failed, setFailed] = useState(false);
  const videoRef = useRef(null);
  const watermarkTile = useMemo(
    () => (viewerEmail ? buildWatermarkTile(viewerEmail) : null),
    [viewerEmail],
  );

  // The overlay watermark is a DOM element positioned on top of the player —
  // it isn't part of the video itself, so native fullscreen (which hands the
  // whole screen to the OS's own video surface) leaves it behind. iOS Safari
  // in particular ignores controlsList entirely and always offers its own
  // fullscreen toggle regardless, so playsInline/controlsList alone isn't
  // enough there; this listens for iOS's fullscreen event and immediately
  // backs out of it, every time.
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return undefined;
    const exitNativeFullscreen = () => {
      if (video.webkitExitFullscreen) {
        video.webkitExitFullscreen();
      }
    };
    video.addEventListener('webkitbeginfullscreen', exitNativeFullscreen);
    return () => video.removeEventListener('webkitbeginfullscreen', exitNativeFullscreen);
  }, []);

  return (
    <div className="video-wrap">
      <video
        ref={videoRef}
        controls
        controlsList="nodownload noremoteplayback nofullscreen"
        disablePictureInPicture
        playsInline
        webkit-playsinline="true"
        className="media-player"
        src={`${API_BASE}/api/stream/${ticket}`}
        onError={() => setFailed(true)}
        onContextMenu={(e) => e.preventDefault()}
      >
        Your browser doesn't support inline video.
      </video>
      {watermarkTile && (
        <div className="video-watermark" style={{ backgroundImage: watermarkTile }} />
      )}
      {failed && <p className="field-error">This video could not be loaded. Try refreshing the page.</p>}
    </div>
  );
}
