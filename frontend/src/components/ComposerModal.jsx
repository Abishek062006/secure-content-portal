import { useEffect, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import Modal from './Modal';
import Avatar from './Avatar';
import Icon from './Icon';

const MODES = {
  post: { title: 'Create a post', placeholder: 'What do you want to talk about?' },
  article: { title: 'Write an article', placeholder: 'Write your article here…' },
  certificate: { title: 'Share a certificate', placeholder: 'Say something about this achievement…' },
};

/** "Start a post" — one dialog for posts, photos/videos, articles and sharing a certificate; admins get extra options. */
export default function ComposerModal({ open, mode, me, courses, presetCourseId, onClose, onCreated }) {
  const { user } = useAuth();
  const [body, setBody] = useState('');
  const [title, setTitle] = useState('');
  const [media, setMedia] = useState(null);
  const [preview, setPreview] = useState(null);
  const [certificateId, setCertificateId] = useState('');
  const [courseId, setCourseId] = useState('');
  const [pinned, setPinned] = useState(false);
  const [publishAt, setPublishAt] = useState('');
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState(null);

  const certificates = me?.certificates || [];
  const article = mode === 'article';

  useEffect(() => {
    if (!open) return;
    setError(null);
    setBody(presetCourseId ? 'New course is live! ' : '');
    setCourseId(presetCourseId || '');
    setTitle('');
    setMedia(null);
    setPinned(false);
    setPublishAt('');
    setCertificateId(mode === 'certificate' && certificates[0] ? certificates[0].id : '');
  }, [open, mode]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!media) { setPreview(null); return undefined; }
    const url = URL.createObjectURL(media);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [media]);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.append('body', body);
      if (media) form.append(media.type.startsWith('video/') ? 'video' : 'image', media);
      const adminExtras = user?.admin && !article && (pinned || publishAt || courseId);
      if (adminExtras) {
        form.append('pinned', pinned);
        if (courseId) form.append('courseId', courseId);
        if (publishAt) form.append('publishAt', new Date(publishAt).toISOString());
      } else {
        if (article) { form.append('article', 'true'); form.append('title', title); }
        if (certificateId) form.append('certificateId', certificateId);
      }
      setProgress(0);
      const created = await api.uploadWithProgress(adminExtras ? '/api/admin/posts' : '/api/posts', form, setProgress);
      onCreated(created);
      onClose();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const shownMode = MODES[mode] || MODES.post;
  const canPost = body.trim() && (!article || title.trim()) && (mode !== 'certificate' || certificateId);

  return (
    <Modal open={open} title={shownMode.title} onClose={busy ? () => {} : onClose} wide>
      <form className="composer-form" onSubmit={submit}>
        <div className="composer-who">
          <Avatar name={user?.displayName} url={me?.avatarUrl} size={48} />
          <strong>{user?.displayName}</strong>
        </div>

        {article && (
          <input className="article-title" value={title} maxLength={200} placeholder="Title" onChange={(e) => setTitle(e.target.value)} />
        )}
        <textarea value={body} onChange={(e) => setBody(e.target.value)} rows={article ? 12 : 6}
                  maxLength={article ? 20000 : 3000} placeholder={shownMode.placeholder} autoFocus />

        {mode === 'certificate' && (
          certificates.length === 0
            ? <p className="field-hint">You haven't earned a certificate yet. Finish a course to share it here.</p>
            : (
              <select value={certificateId} onChange={(e) => setCertificateId(e.target.value)} aria-label="Certificate">
                {certificates.map((c) => <option key={c.id} value={c.id}>{c.courseTitle}</option>)}
              </select>
            )
        )}

        {preview && (
          <div className="composer-preview">
            {media.type.startsWith('video/') ? <video src={preview} controls /> : <img src={preview} alt="" />}
            <button type="button" className="btn btn-icon" aria-label="Remove attachment" onClick={() => setMedia(null)}><Icon name="x" size={16} /></button>
          </div>
        )}

        {user?.admin && !article && mode !== 'certificate' && (
          <div className="composer-admin">
            <span className="field-hint">Admin options</span>
            <select value={courseId} onChange={(e) => setCourseId(e.target.value)} aria-label="Promote a course">
              <option value="">No course attached</option>
              {courses.map((c) => <option key={c.id} value={c.id}>Promote: {c.title}</option>)}
            </select>
            <label><input type="checkbox" checked={pinned} onChange={(e) => setPinned(e.target.checked)} /> Pin to top</label>
            <label>Schedule <input type="datetime-local" value={publishAt} onChange={(e) => setPublishAt(e.target.value)} /></label>
          </div>
        )}

        {busy && media && (
          <div className="progress" role="progressbar" aria-valuenow={Math.round(progress * 100)} aria-valuemin={0} aria-valuemax={100}>
            <div className="progress-bar" style={{ width: `${progress * 100}%` }} />
            <span className="progress-label">{progress < 1 ? `Uploading… ${Math.round(progress * 100)}%` : 'Saving…'}</span>
          </div>
        )}
        {error && <p className="form-error">{error}</p>}

        <div className="composer-footer">
          <div className="composer-tools">
            {!media && mode !== 'certificate' && (
              <>
                <label className="tool-btn" title="Add a photo">
                  <Icon name="image" size={22} /> <input type="file" hidden accept="image/jpeg,image/png,image/webp" onChange={(e) => { setMedia(e.target.files[0] || null); e.target.value = ''; }} />
                </label>
                {!article && (
                  <label className="tool-btn" title="Add a video (up to 500 MB)">
                    <Icon name="video" size={22} /> <input type="file" hidden accept="video/mp4,video/webm" onChange={(e) => { setMedia(e.target.files[0] || null); e.target.value = ''; }} />
                  </label>
                )}
              </>
            )}
          </div>
          <button type="submit" className="btn btn-primary" disabled={busy || !canPost}>
            {busy ? 'Posting…' : publishAt ? 'Schedule' : 'Post'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
