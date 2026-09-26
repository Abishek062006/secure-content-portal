import { API_BASE } from '../../api';

export default function HtmlViewer({ ticket, path }) {
  return (
    <iframe
      sandbox=""
      src={`${API_BASE}${path || `/api/html/${ticket}`}`}
      className="sandboxed-frame"
      title="Sandboxed HTML content"
    />
  );
}
