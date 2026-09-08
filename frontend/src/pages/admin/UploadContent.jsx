import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';

const CONTENT_TYPES = [
  { value: 'VIDEO', label: 'Video', accept: '.mp4,.webm' },
  { value: 'PDF', label: 'PDF', accept: '.pdf' },
  { value: 'HTML', label: 'HTML page', accept: '.html,.htm' },
];

export default function UploadContent() {
  const navigate = useNavigate();
  const [contentType, setContentType] = useState('VIDEO');
  const [errorMessage, setErrorMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setErrorMessage(null);
    setSubmitting(true);

    const form = e.currentTarget;
    const formData = new FormData();
    formData.set('title', form.title.value);
    formData.set('description', form.description.value);
    formData.set('category', form.category.value);
    formData.set('contentType', contentType);
    if (form.file.files[0]) {
      formData.set('file', form.file.files[0]);
    }

    try {
      const created = await api.upload('/api/admin/content', formData);
      navigate('/admin/content', { state: { success: `"${created.title}" uploaded.` } });
    } catch (err) {
      setErrorMessage(err.message);
      setSubmitting(false);
    }
  }

  const accept = CONTENT_TYPES.find((t) => t.value === contentType)?.accept || '';

  return (
    <div className="container">
      <Alert error={errorMessage} />
      <h1>Upload content</h1>
      <form className="form-panel" onSubmit={onSubmit}>
        <div className="field">
          <label htmlFor="title">Title</label>
          <input id="title" name="title" type="text" maxLength={200} required />
        </div>

        <div className="field">
          <label htmlFor="description">Description</label>
          <textarea id="description" name="description" maxLength={2000} rows={4} />
        </div>

        <div className="field">
          <label htmlFor="category">Category / tag</label>
          <input id="category" name="category" type="text" maxLength={80} placeholder="e.g. Onboarding" />
        </div>

        <div className="field">
          <label htmlFor="contentType">Content type</label>
          <select id="contentType" name="contentType" value={contentType} onChange={(e) => setContentType(e.target.value)}>
            {CONTENT_TYPES.map((t) => (
              <option key={t.value} value={t.value}>{t.label}</option>
            ))}
          </select>
        </div>

        <div className="field">
          <label htmlFor="file">File</label>
          <input id="file" name="file" type="file" accept={accept} required />
          <p className="field-hint">Video up to 512 MB (.mp4, .webm) &middot; PDF up to 32 MB &middot; HTML up to 2 MB</p>
        </div>

        <div className="form-actions">
          <Link className="btn" to="/admin/content">Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Uploading…' : 'Upload'}
          </button>
        </div>
      </form>
    </div>
  );
}
