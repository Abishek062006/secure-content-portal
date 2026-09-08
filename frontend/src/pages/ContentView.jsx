import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import VideoViewer from './viewers/VideoViewer';
import PdfViewer from './viewers/PdfViewer';
import HtmlViewer from './viewers/HtmlViewer';

/**
 * Fetches a fresh ticket on every mount rather than caching one across
 * navigations — tickets are short-lived (5-30 min) and session-bound, so
 * reusing a stale one is never correct.
 */
export default function ContentView() {
  const { id } = useParams();
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setError(null);
    api.get(`/api/content/${id}`)
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (error) {
    return (
      <div className="container-wide">
        <p className="field-error">{error}</p>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="container-wide">
        <p className="pdf-loading">Loading…</p>
      </div>
    );
  }

  const { item, ticket } = data;

  return (
    <div className="container-wide">
      <h1>{item.title}</h1>
      {item.description && <p className="field-hint">{item.description}</p>}

      {item.contentType === 'VIDEO' && <VideoViewer ticket={ticket} viewerEmail={user?.email} />}
      {item.contentType === 'PDF' && <PdfViewer ticket={ticket} pageCount={item.pageCount} />}
      {item.contentType === 'HTML' && <HtmlViewer ticket={ticket} />}
    </div>
  );
}
