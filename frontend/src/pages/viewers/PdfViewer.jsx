import { useState } from 'react';
import { API_BASE } from '../../api';

export default function PdfViewer({ ticket, pageCount }) {
  const totalPages = pageCount || 1;
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  function goTo(n) {
    setPage(n);
    setLoading(true);
    setFailed(false);
  }

  return (
    <div>
      <div className="pdf-toolbar">
        <button type="button" className="btn" disabled={page <= 1} onClick={() => goTo(page - 1)}>&larr; Previous</button>
        <span>Page {page} of {totalPages}</span>
        <button type="button" className="btn" disabled={page >= totalPages} onClick={() => goTo(page + 1)}>Next &rarr;</button>
      </div>

      <div className="pdf-page-wrap">
        {(loading || failed) && (
          <div className="pdf-loading">
            {failed ? 'Could not load this page. Try again in a moment.' : 'Loading page…'}
          </div>
        )}
        <img
          alt="PDF page"
          hidden={loading || failed}
          src={`${API_BASE}/api/pdf/${ticket}/page/${page}`}
          onLoad={() => setLoading(false)}
          onError={() => {
            setLoading(false);
            setFailed(true);
          }}
        />
      </div>
    </div>
  );
}
