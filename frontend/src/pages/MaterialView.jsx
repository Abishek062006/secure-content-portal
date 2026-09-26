import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { API_BASE, api } from '../api';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import VideoViewer from './viewers/VideoViewer';
import PdfViewer from './viewers/PdfViewer';
import HtmlViewer from './viewers/HtmlViewer';
import { MATERIAL_LABEL } from '../lib/materials';

/** Opens one piece of module material: shown inside the portal with the same protections as library content. */
export default function MaterialView() {
  const { courseId, id } = useParams();
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setError(null);
    api.get(`/api/courses/${courseId}/materials/${id}`)
      .then((res) => { if (!cancelled) setData(res); })
      .catch((err) => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [courseId, id]);

  if (error) {
    return (
      <div className="container-wide">
        <p className="breadcrumb"><Link to={`/courses/${courseId}`}>← Back to the course</Link></p>
        <p className="field-error">{error}</p>
      </div>
    );
  }
  if (!data) return <div className="container-wide"><p className="pdf-loading">Loading…</p></div>;

  const { material, ticket, courseTitle } = data;
  const downloadUrl = `${API_BASE}/api/courses/${courseId}/materials/${id}/download`;

  return (
    <div className="container-wide">
      <p className="breadcrumb"><Link to={`/courses/${courseId}`}>← {courseTitle}</Link></p>
      <div className="page-head">
        <div>
          <h1>{material.title}</h1>
          <p className="field-hint">{MATERIAL_LABEL[material.kind]}{material.sizeLabel ? ` · ${material.sizeLabel}` : ''}</p>
          {material.description && <p className="field-hint">{material.description}</p>}
        </div>
        {material.downloadable && material.kind !== 'LINK' && (
          <a className="btn btn-primary" href={downloadUrl}><Icon name="send" size={16} /> Download</a>
        )}
      </div>

      {material.kind === 'PDF' && <PdfViewer ticket={ticket} pageCount={material.pageCount} basePath={`/api/material/${ticket}/pdf`} />}
      {material.kind === 'HTML' && <HtmlViewer ticket={ticket} path={`/api/material/${ticket}/html`} />}
      {material.kind === 'VIDEO' && <VideoViewer ticket={ticket} viewerEmail={user?.email} src={`/api/material/${ticket}/video`} />}
      {material.kind === 'LINK' && (
        <div className="material-card">
          <p>This material is a link to another website.</p>
          <a className="btn btn-primary" href={material.url} target="_blank" rel="noopener noreferrer nofollow">
            <Icon name="link" size={16} /> Open link
          </a>
          <p className="field-hint">{material.url}</p>
        </div>
      )}
      {material.kind === 'DOCUMENT' && (
        <div className="material-card">
          <p>This document opens in its own app. Download it to read it.</p>
          <a className="btn btn-primary" href={downloadUrl}>Download {material.filename}</a>
        </div>
      )}
      {!material.downloadable && material.kind !== 'LINK' && (
        <p className="field-hint">Your instructor made this view-only, so there's no download.</p>
      )}
    </div>
  );
}
