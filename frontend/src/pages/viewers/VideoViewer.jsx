import { useMemo, useState } from 'react';
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
  const watermarkTile = useMemo(
    () => (viewerEmail ? buildWatermarkTile(viewerEmail) : null),
    [viewerEmail],
  );

  return (
    <div className="video-wrap">
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
      {watermarkTile && (
        <div className="video-watermark" style={{ backgroundImage: watermarkTile }} />
      )}
      {failed && <p className="field-error">This video could not be loaded. Try refreshing the page.</p>}
    </div>
  );
}
