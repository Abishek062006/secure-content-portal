import { useEffect, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import { mediaUrl } from '../lib/media';
import { dateRange } from '../lib/hackathons';
import Modal from './Modal';
import Avatar from './Avatar';
import Icon from './Icon';

const MODES = {
  post: { title: 'Create a post', placeholder: 'What do you want to talk about?' },
  article: { title: 'Write an article', placeholder: 'Write your article here…' },
  certificate: { title: 'Share a certificate', placeholder: 'Say something about this achievement…' },
};

/** A caption built from what the admin already filled in for the hackathon — editable afterwards, like anything else here. */
function hackathonCaption(h) {
  const facts = [];
  if (h.organizer) facts.push(`Hosted by ${h.organizer}`);
  const when = dateRange(h.eventStartDate, h.eventEndDate);
  if (when) facts.push(when);
  if (h.location) facts.push(h.location);
  if (h.prizePool) facts.push(`Prize pool: ${h.prizePool}`);
  const lines = [`${h.title} is open!`];
  if (facts.length) lines.push(facts.join(' · '));
  lines.push(h.kind === 'HOSTED' ? 'Join a team and build with us.' : 'Take a look and register before it closes.');
  return lines.join('\n');
}

/** "Start a post" — one dialog for posts, photos/videos, articles and sharing a certificate; admins get extra options. */
export default function ComposerModal({ open, mode, me, courses, hackathons = [], presetCourseId, presetHackathonId, onClose, onCreated }) {
  const { user } = useAuth();
  const [body, setBody] = useState('');
  const [title, setTitle] = useState('');
  const [media, setMedia] = useState(null);
  const [mediaIsAuto, setMediaIsAuto] = useState(false);
  const [bodyIsAuto, setBodyIsAuto] = useState(false);
  const [preview, setPreview] = useState(null);
  const [certificateId, setCertificateId] = useState('');
  const [courseId, setCourseId] = useState('');
  const [hackathonId, setHackathonId] = useState('');
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
    const shared = presetHackathonId ? hackathons.find((h) => String(h.id) === String(presetHackathonId)) : null;
    setBody(presetCourseId ? 'New course is live! ' : shared ? hackathonCaption(shared) : '');
    setBodyIsAuto(Boolean(shared));
    setCourseId(presetCourseId || '');
    setHackathonId(presetHackathonId ? String(presetHackathonId) : '');
    setTitle('');
    setMedia(null);
    setMediaIsAuto(false);
    setPinned(false);
    setPublishAt('');
    setCertificateId(mode === 'certificate' && certificates[0] ? certificates[0].id : '');
  }, [open, mode, hackathons.length]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!media) { setPreview(null); return undefined; }
    const url = URL.createObjectURL(media);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [media]);

  // Pull in the hackathon's own poster as the post image, unless the admin has attached their own photo.
  useEffect(() => {
    if (!open || !hackathonId || (media && !mediaIsAuto)) return undefined;
    const shared = hackathons.find((h) => String(h.id) === String(hackathonId));
    if (!shared?.bannerUrl) return undefined;
    let cancelled = false;
    fetch(mediaUrl(shared.bannerUrl), { credentials: 'include' })
      .then((res) => (res.ok ? res.blob() : null))
      .then((blob) => {
        if (cancelled || !blob) return;
        setMedia(new File([blob], `hackathon-${shared.id}-banner.jpg`, { type: blob.type || 'image/jpeg' }));
        setMediaIsAuto(true);
      })
      .catch(() => {}); // admin can still attach a photo by hand
    return () => { cancelled = true; };
  }, [hackathonId, open]); // eslint-disable-line react-hooks/exhaustive-deps

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.append('body', body);
      if (media) form.append(media.type.startsWith('video/') ? 'video' : 'image', media);
      const adminExtras = user?.admin && !article && (pinned || publishAt || courseId || hackathonId);
      if (adminExtras) {
        form.append('pinned', pinned);
        if (courseId) form.append('courseId', courseId);
        if (hackathonId) form.append('hackathonId', hackathonId);
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
        <textarea value={body} onChange={(e) => { setBody(e.target.value); setBodyIsAuto(false); }} rows={article ? 12 : 6}
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
            {mediaIsAuto && <span className="field-hint composer-auto-tag">From the hackathon poster</span>}
            <button type="button" className="btn btn-icon" aria-label="Remove attachment" onClick={() => { setMedia(null); setMediaIsAuto(false); }}><Icon name="x" size={16} /></button>
          </div>
        )}

        {user?.admin && !article && mode !== 'certificate' && (
          <div className="composer-admin">
            <span className="field-hint">Admin options</span>
            <select value={courseId} onChange={(e) => setCourseId(e.target.value)} aria-label="Promote a course">
              <option value="">No course attached</option>
              {courses.map((c) => <option key={c.id} value={c.id}>Promote: {c.title}</option>)}
            </select>
            <select value={hackathonId} onChange={(e) => {
              const nextId = e.target.value;
              setHackathonId(nextId);
              const shared = hackathons.find((h) => String(h.id) === nextId);
              if (shared && (bodyIsAuto || !body.trim())) { setBody(hackathonCaption(shared)); setBodyIsAuto(true); }
            }} aria-label="Share a hackathon">
              <option value="">No hackathon attached</option>
              {hackathons.map((h) => <option key={h.id} value={h.id}>Share hackathon: {h.title}</option>)}
            </select>
            {hackathonId && <p className="field-hint">Poster and caption are filled in from the hackathon — edit either, or attach your own photo above.</p>}
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
                  <Icon name="image" size={22} /> <input type="file" hidden accept="image/jpeg,image/png,image/webp" onChange={(e) => { setMedia(e.target.files[0] || null); setMediaIsAuto(false); e.target.value = ''; }} />
                </label>
                {!article && (
                  <label className="tool-btn" title="Add a video (up to 500 MB)">
                    <Icon name="video" size={22} /> <input type="file" hidden accept="video/mp4,video/webm" onChange={(e) => { setMedia(e.target.files[0] || null); setMediaIsAuto(false); e.target.value = ''; }} />
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
