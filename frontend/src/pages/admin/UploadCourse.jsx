import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';

const MAX_VIDEO_BYTES = 5 * 1024 ** 3;

export default function UploadCourse() {
  const navigate = useNavigate();
  const [errorMessage, setErrorMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [progress, setProgress] = useState(0);
  const [thumbnailFile, setThumbnailFile] = useState(null);
  const [thumbnailPreview, setThumbnailPreview] = useState(null);

  useEffect(() => {
    if (!thumbnailFile) {
      setThumbnailPreview(null);
      return undefined;
    }
    const url = URL.createObjectURL(thumbnailFile);
    setThumbnailPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [thumbnailFile]);

  async function onSubmit(e) {
    e.preventDefault();
    setErrorMessage(null);

    const form = e.currentTarget;
    const video = form.video.files[0];
    if (!/\.(mp4|webm)$/i.test(video.name)) {
      setErrorMessage('The lecture video must be an .mp4 or .webm file.');
      return;
    }
    if (video.size > MAX_VIDEO_BYTES) {
      setErrorMessage('That video is over the 5 GB limit.');
      return;
    }

    setProgress(0);
    setSubmitting(true);
    const formData = new FormData();
    formData.set('title', form.title.value);
    formData.set('description', form.description.value);
    formData.set('category', form.category.value);
    formData.set('video', video);
    if (form.thumbnail.files[0]) formData.set('thumbnail', form.thumbnail.files[0]);
    if (form.transcript.files[0]) formData.set('transcript', form.transcript.files[0]);

    try {
      const created = await api.uploadWithProgress('/api/admin/courses', formData, setProgress);
      navigate('/admin/courses', { state: { success: `"${created.title}" created.` } });
    } catch (err) {
      setErrorMessage(err.message);
      setSubmitting(false);
    }
  }

  const percent = Math.round(progress * 100);

  return (
    <div className="container">
      <Alert error={errorMessage} />
      <h1>New course</h1>
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
          <label htmlFor="video">Lecture video</label>
          <input id="video" name="video" type="file" accept=".mp4,.webm" required />
          <p className="field-hint">The Zoom recording (.mp4 or .webm), up to 5 GB. Large files can take a while; keep this tab open until it finishes.</p>
        </div>

        <div className="field">
          <label htmlFor="thumbnail">Cover image (optional)</label>
          <input id="thumbnail" name="thumbnail" type="file" accept=".jpg,.jpeg,.png,.webp"
                 onChange={(e) => setThumbnailFile(e.target.files[0] || null)} />
          <p className="field-hint">JPG, PNG or WebP, up to 5 MB. 16:9 looks best.</p>
          {thumbnailPreview && <img className="thumb-preview" src={thumbnailPreview} alt="Cover preview" />}
        </div>

        <div className="field">
          <label htmlFor="transcript">Transcript (optional)</label>
          <input id="transcript" name="transcript" type="file" accept=".vtt" />
          <p className="field-hint">
            The .vtt file Zoom saves next to the recording. Learners can click a line to jump to it,
            and it's what quizzes will be generated from later.
          </p>
        </div>

        {submitting && (
          <div className="progress" role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
            <div className="progress-bar" style={{ width: `${percent}%` }} />
            <span className="progress-label">{percent < 100 ? `Uploading… ${percent}%` : 'Saving…'}</span>
          </div>
        )}

        <div className="form-actions">
          <Link className="btn" to="/admin/courses">Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Uploading…' : 'Create course'}
          </button>
        </div>
      </form>
    </div>
  );
}
