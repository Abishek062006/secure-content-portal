import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { API_BASE, api } from '../api';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import LearnShell from '../components/learn/LearnShell';
import VideoViewer from './viewers/VideoViewer';
import PdfViewer from './viewers/PdfViewer';
import HtmlViewer from './viewers/HtmlViewer';
import { MATERIAL_LABEL } from '../lib/materials';

/** Opens one piece of module material inside the same player layout as lessons, with the same protections. */
export default function MaterialView() {
  const { courseId, id } = useParams();
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [outline, setOutline] = useState(null);
  const [error, setError] = useState(null);
  const isAdmin = Boolean(user?.admin);

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setError(null);
    Promise.all([api.get(`/api/courses/${courseId}/materials/${id}`), api.get(`/api/courses/${courseId}`)])
      .then(([material, course]) => {
        if (cancelled) return;
        setData(material);
        setOutline(course);
      })
      .catch((err) => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [courseId, id]);

  const current = { materialId: id };

  if (error) {
    return (
      <LearnShell courseId={courseId} outline={outline} current={current} isAdmin={isAdmin}>
        <div className="ln-stage">
          <p className="field-error">{error}</p>
          <Link className="btn" to={`/courses/${courseId}`}>Back to the course</Link>
        </div>
      </LearnShell>
    );
  }
  if (!data || !outline) {
    return (
      <LearnShell courseId={courseId} outline={null} current={current} isAdmin={isAdmin}>
        <div className="ln-stage"><div className="ln-video-skeleton" /></div>
      </LearnShell>
    );
  }

  const { material, ticket } = data;
  const module = outline.modules.find((m) => m.id === material.moduleId);
  const downloadUrl = `${API_BASE}/api/courses/${courseId}/materials/${id}/download`;

  return (
    <LearnShell courseId={courseId} outline={outline} current={current} isAdmin={isAdmin}>
      <div className="ln-stage">
        <div className="ln-title-row">
          <div>
            <p className="ln-kicker">{module ? `${module.title} · Resource` : 'Resource'}</p>
            <h1 className="ln-title">{material.title}</h1>
            <p className="ln-sub">{MATERIAL_LABEL[material.kind]}{material.sizeLabel ? ` · ${material.sizeLabel}` : ''}</p>
          </div>
          {material.downloadable && material.kind !== 'LINK' && (
            <div className="ln-actions">
              <a className="ln-btn dark" href={downloadUrl}><Icon name="download" size={16} /> Download</a>
            </div>
          )}
        </div>
        {material.description && <p className="ln-lede">{material.description}</p>}

        <div className="ln-viewer">
          {material.kind === 'PDF' && <PdfViewer ticket={ticket} pageCount={material.pageCount} basePath={`/api/material/${ticket}/pdf`} />}
          {material.kind === 'HTML' && <HtmlViewer ticket={ticket} path={`/api/material/${ticket}/html`} />}
          {material.kind === 'VIDEO' && <VideoViewer ticket={ticket} viewerEmail={user?.email} src={`/api/material/${ticket}/video`} />}
          {material.kind === 'LINK' && (
            <div className="ln-card">
              <span className="ln-resource-icon"><Icon name="link" size={22} /></span>
              <div>
                <p>This resource is a link to another website.</p>
                <p className="ln-muted">{material.url}</p>
                <a className="ln-btn dark" href={material.url} target="_blank" rel="noopener noreferrer nofollow">
                  <Icon name="link" size={16} /> Open link
                </a>
              </div>
            </div>
          )}
          {material.kind === 'DOCUMENT' && (
            <div className="ln-card">
              <span className="ln-resource-icon"><Icon name="file-text" size={22} /></span>
              <div>
                <p>This document opens in its own app. Download it to read it.</p>
                <a className="ln-btn dark" href={downloadUrl}><Icon name="download" size={16} /> Download {material.filename}</a>
              </div>
            </div>
          )}
        </div>
        {!material.downloadable && material.kind !== 'LINK' && (
          <p className="ln-muted">Your instructor made this view-only, so there's no download.</p>
        )}
      </div>
    </LearnShell>
  );
}
