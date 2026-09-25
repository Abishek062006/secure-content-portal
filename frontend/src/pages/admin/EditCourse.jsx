import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { API_BASE, api } from '../../api';
import Alert from '../../components/Alert';

export default function EditCourse() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [course, setCourse] = useState(null);
  const [form, setForm] = useState({ title: '', description: '', category: '' });
  const [errorMessage, setErrorMessage] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [replacing, setReplacing] = useState(null);

  useEffect(() => {
    api.get('/api/admin/courses')
      .then((courses) => {
        const found = courses.find((c) => c.id === id);
        if (found) {
          setCourse(found);
          setForm({ title: found.title, description: found.description || '', category: found.category || '' });
        } else {
          setErrorMessage('Course not found.');
        }
      })
      .catch((err) => setErrorMessage(err.message));
  }, [id]);

  async function onSubmit(e) {
    e.preventDefault();
    setErrorMessage(null);
    setSubmitting(true);
    try {
      const updated = await api.put(`/api/admin/courses/${id}`, form);
      navigate('/admin/courses', { state: { success: `"${updated.title}" updated.` } });
    } catch (err) {
      setErrorMessage(err.message);
      setSubmitting(false);
    }
  }

  async function replaceFile(kind, input) {
    const file = input.files[0];
    if (!file) return;
    setErrorMessage(null);
    setSuccessMessage(null);
    setReplacing(kind);
    const formData = new FormData();
    formData.set('file', file);
    try {
      const updated = await api.upload(`/api/admin/courses/${id}/${kind}`, formData);
      setCourse(updated);
      setSuccessMessage(kind === 'thumbnail' ? 'Cover image replaced.' : 'Transcript replaced.');
      input.value = '';
    } catch (err) {
      setErrorMessage(err.message);
    } finally {
      setReplacing(null);
    }
  }

  if (!course) {
    return (
      <div className="container">
        <Alert error={errorMessage} />
      </div>
    );
  }

  return (
    <div className="container">
      <Alert success={successMessage} error={errorMessage} />
      <h1>Edit course</h1>
      <p className="field-hint" style={{ marginTop: -10 }}>
        {course.videoFilename} &middot; {course.videoSizeLabel}
        {' '}— the video itself can't be replaced here; delete and re-create the course to change it.
      </p>

      <form className="form-panel" onSubmit={onSubmit}>
        <div className="field">
          <label htmlFor="title">Title</label>
          <input id="title" type="text" maxLength={200} value={form.title}
                 onChange={(e) => setForm({ ...form, title: e.target.value })} required />
        </div>

        <div className="field">
          <label htmlFor="description">Description</label>
          <textarea id="description" maxLength={2000} rows={4} value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </div>

        <div className="field">
          <label htmlFor="category">Category / tag</label>
          <input id="category" type="text" maxLength={80} value={form.category}
                 onChange={(e) => setForm({ ...form, category: e.target.value })} />
        </div>

        <div className="form-actions">
          <Link className="btn" to="/admin/courses">Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>

      <div className="form-panel" style={{ marginTop: 20 }}>
        <div className="field">
          <label htmlFor="thumbnail">Cover image</label>
          {course.thumbnailUrl
            ? <img className="thumb-preview" src={`${API_BASE}${course.thumbnailUrl}`} alt="Current cover" />
            : <p className="field-hint">No cover image yet.</p>}
          <input id="thumbnail" type="file" accept=".jpg,.jpeg,.png,.webp" disabled={replacing === 'thumbnail'}
                 onChange={(e) => replaceFile('thumbnail', e.target)} />
          <p className="field-hint">Choosing a file replaces the cover immediately. JPG, PNG or WebP, up to 5 MB.</p>
        </div>

        <div className="field">
          <label htmlFor="transcript">Transcript</label>
          <p className="field-hint">{course.hasTranscript ? `Current: ${course.transcriptFilename}` : 'No transcript yet.'}</p>
          <input id="transcript" type="file" accept=".vtt" disabled={replacing === 'transcript'}
                 onChange={(e) => replaceFile('transcript', e.target)} />
          <p className="field-hint">Choosing a .vtt file replaces the transcript immediately.</p>
        </div>
      </div>
    </div>
  );
}
