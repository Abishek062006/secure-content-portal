import { API_BASE } from '../../api';

export default function HtmlViewer({ ticket }) {
  return (
    <iframe
      sandbox=""
      src={`${API_BASE}/api/html/${ticket}`}
      className="sandboxed-frame"
      title="Sandboxed HTML content"
    />
  );
}
