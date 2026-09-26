import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import Alert from '../components/Alert';
import Avatar from '../components/Avatar';
import ConfirmDialog from '../components/ConfirmDialog';
import Modal from '../components/Modal';
import PostCard from '../components/PostCard';
import useSocialBackground from '../lib/useLinkedInBackground';
import { mediaUrl } from '../lib/media';

const KIND_LABEL = {
  EDUCATION: { add: 'Add education', title: 'Education', first: 'School', second: 'Degree or field of study' },
  EXPERIENCE: { add: 'Add experience', title: 'Experience', first: 'Company', second: 'Role' },
  SKILL: { add: 'Add skill', title: 'Skills', first: 'Skill', second: '' },
};

function years(entry) {
  if (!entry.startYear && !entry.endYear) return null;
  return `${entry.startYear ?? ''} – ${entry.endYear ?? 'Present'}`;
}

function DetailsForm({ profile, onSaved, onClose }) {
  const [values, setValues] = useState({
    headline: profile.headline || '', about: profile.about || '', location: profile.location || '', website: profile.website || '',
  });
  const [error, setError] = useState(null);
  const set = (key) => (e) => setValues({ ...values, [key]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    try {
      await api.put('/api/profile', values);
      await onSaved();
      onClose();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <form className="modal-form" onSubmit={submit}>
      <div className="field"><label htmlFor="headline">Headline</label>
        <input id="headline" type="text" maxLength={220} value={values.headline} onChange={set('headline')}
               placeholder="e.g. Student at St. Joseph's Institute of Technology" /></div>
      <div className="field"><label htmlFor="location">Location</label>
        <input id="location" type="text" maxLength={120} value={values.location} onChange={set('location')} placeholder="e.g. Chennai, Tamil Nadu" /></div>
      <div className="field"><label htmlFor="website">Website</label>
        <input id="website" type="text" maxLength={300} value={values.website} onChange={set('website')} placeholder="e.g. github.com/yourname" /></div>
      <div className="field"><label htmlFor="about">About</label>
        <textarea id="about" rows={6} maxLength={2600} value={values.about} onChange={set('about')} /></div>
      {error && <p className="form-error">{error}</p>}
      <div className="form-actions"><button type="submit" className="btn btn-primary">Save</button></div>
    </form>
  );
}

function EntryForm({ kind, entry, onSaved, onClose }) {
  const label = KIND_LABEL[kind];
  const [values, setValues] = useState({
    title: entry?.title || '', subtitle: entry?.subtitle || '', startYear: entry?.startYear ?? '', endYear: entry?.endYear ?? '',
    description: entry?.description || '',
  });
  const [error, setError] = useState(null);
  const set = (key) => (e) => setValues({ ...values, [key]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    const body = {
      kind, title: values.title, subtitle: values.subtitle, description: values.description,
      startYear: values.startYear === '' ? null : Number(values.startYear), endYear: values.endYear === '' ? null : Number(values.endYear),
    };
    try {
      if (entry) await api.put(`/api/profile/entries/${entry.id}`, body);
      else await api.post('/api/profile/entries', body);
      await onSaved();
      onClose();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <form className="modal-form" onSubmit={submit}>
      <div className="field"><label htmlFor="e-title">{label.first}</label>
        <input id="e-title" type="text" maxLength={200} required value={values.title} onChange={set('title')} autoFocus /></div>
      {kind !== 'SKILL' && (
        <>
          <div className="field"><label htmlFor="e-sub">{label.second}</label>
            <input id="e-sub" type="text" maxLength={200} value={values.subtitle} onChange={set('subtitle')} /></div>
          <div className="counts">
            <label className="count-field">Start year<input type="number" min={1950} max={2100} value={values.startYear} onChange={set('startYear')} /></label>
            <label className="count-field">End year (blank if current)<input type="number" min={1950} max={2100} value={values.endYear} onChange={set('endYear')} /></label>
          </div>
          <div className="field"><label htmlFor="e-desc">Description</label>
            <textarea id="e-desc" rows={4} maxLength={1500} value={values.description} onChange={set('description')} /></div>
        </>
      )}
      {error && <p className="form-error">{error}</p>}
      <div className="form-actions"><button type="submit" className="btn btn-primary">Save</button></div>
    </form>
  );
}

function Section({ title, mine, onAdd, addLabel, children, empty }) {
  return (
    <section className="profile-section">
      <header>
        <h2>{title}</h2>
        {mine && <button type="button" className="icon-link" onClick={onAdd} aria-label={addLabel}>＋</button>}
      </header>
      {empty ? <p className="muted">{mine ? empty : 'Nothing added yet.'}</p> : children}
    </section>
  );
}

export default function Profile() {
  useSocialBackground();
  const { id } = useParams();
  const { user } = useAuth();
  const userId = id ? Number(id) : user?.id;
  const [profile, setProfile] = useState(null);
  const [posts, setPosts] = useState([]);
  const [hasMore, setHasMore] = useState(false);
  const [page, setPage] = useState(0);
  const [error, setError] = useState(null);
  const [modal, setModal] = useState(null); // {type:'details'} | {type:'entry', kind, entry}
  const [toDelete, setToDelete] = useState(null);

  const load = useCallback(async () => {
    if (!userId) return;
    setProfile(await api.get(id ? `/api/profiles/${id}` : '/api/profile'));
  }, [id, userId]);

  const loadPosts = useCallback(async (target) => {
    const res = await api.get(`/api/profiles/${userId}/posts?page=${target}`);
    setPosts((prev) => (target === 0 ? res.posts : [...prev, ...res.posts]));
    setHasMore(res.hasMore);
    setPage(target);
  }, [userId]);

  useEffect(() => {
    setProfile(null);
    setPosts([]);
    load().catch((e) => setError(e.message));
    loadPosts(0).catch(() => {});
  }, [load, loadPosts]);

  async function pickImage(kind, file) {
    if (!file) return;
    const form = new FormData();
    form.set('file', file);
    try {
      setProfile(await api.upload(`/api/profile/${kind}`, form));
    } catch (e) {
      setError(e.message);
    }
  }

  async function removeImage(kind) {
    try {
      setProfile(await api.del(`/api/profile/${kind}`));
    } catch (e) {
      setError(e.message);
    }
  }

  async function confirmDeleteEntry() {
    const entry = toDelete;
    setToDelete(null);
    try {
      await api.del(`/api/profile/entries/${entry.id}`);
      await load();
    } catch (e) {
      setError(e.message);
    }
  }

  async function confirmDeletePost(post) {
    try {
      await api.del(`/api/posts/${post.id}`);
      await Promise.all([loadPosts(0), load()]);
    } catch (e) {
      setError(e.message);
    }
  }

  if (!profile) {
    return <div className="social-single"><Alert error={error} /></div>;
  }
  const mine = profile.mine;

  const entryList = (kind, list) => (
    <ul className="entry-list">
      {list.map((entry) => (
        <li key={entry.id}>
          <div className="entry-icon" aria-hidden="true">{kind === 'EDUCATION' ? '🎓' : '💼'}</div>
          <div className="entry-body">
            <strong>{entry.title}</strong>
            {entry.subtitle && <div>{entry.subtitle}</div>}
            {years(entry) && <div className="muted">{years(entry)}</div>}
            {entry.description && <p className="entry-desc">{entry.description}</p>}
          </div>
          {mine && (
            <div className="entry-actions">
              <button type="button" className="link-button" onClick={() => setModal({ type: 'entry', kind, entry })}>Edit</button>
              <button type="button" className="link-button danger" onClick={() => setToDelete(entry)}>Delete</button>
            </div>
          )}
        </li>
      ))}
    </ul>
  );

  return (
    <div className="social-single">
      <Alert error={error} />

      <section className="profile-header">
        <div className="profile-banner" style={profile.bannerUrl ? { backgroundImage: `url(${mediaUrl(profile.bannerUrl)})` } : undefined}>
          {mine && (
            <div className="banner-tools">
              <label className="tool-pill">📷 {profile.bannerUrl ? 'Change cover' : 'Add cover'}
                <input type="file" hidden accept="image/jpeg,image/png,image/webp" onChange={(e) => { pickImage('banner', e.target.files[0]); e.target.value = ''; }} />
              </label>
              {profile.bannerUrl && <button type="button" className="tool-pill" onClick={() => removeImage('banner')}>Remove</button>}
            </div>
          )}
        </div>
        <div className="profile-avatar-big"><Avatar name={profile.name} url={profile.avatarUrl} size={152} /></div>
        <div className="profile-header-body">
          <div className="profile-header-top">
            <div>
              <h1>{profile.name}</h1>
              {profile.headline && <p className="profile-headline">{profile.headline}</p>}
              <p className="muted">
                {profile.location}{profile.location && profile.website && ' · '}
                {profile.website && <a href={profile.website} target="_blank" rel="noopener noreferrer nofollow">{profile.website.replace(/^https?:\/\//, '')}</a>}
              </p>
            </div>
            {mine && <button type="button" className="btn" onClick={() => setModal({ type: 'details' })}>Edit profile</button>}
          </div>
          {mine && (
            <div className="photo-tools">
              <label className="link-button">Change photo
                <input type="file" hidden accept="image/jpeg,image/png,image/webp" onChange={(e) => { pickImage('avatar', e.target.files[0]); e.target.value = ''; }} />
              </label>
              {profile.hasUploadedAvatar && <button type="button" className="link-button danger" onClick={() => removeImage('avatar')}>Use my Google photo</button>}
            </div>
          )}
        </div>
      </section>

      <Section title="About" mine={mine} onAdd={() => setModal({ type: 'details' })} addLabel="Edit about" empty={!profile.about ? 'Tell people about yourself.' : null}>
        <p className="profile-about">{profile.about}</p>
      </Section>

      <Section title="Experience" mine={mine} onAdd={() => setModal({ type: 'entry', kind: 'EXPERIENCE' })} addLabel="Add experience"
               empty={profile.experience.length === 0 ? 'Add your internships and jobs.' : null}>
        {entryList('EXPERIENCE', profile.experience)}
      </Section>

      <Section title="Education" mine={mine} onAdd={() => setModal({ type: 'entry', kind: 'EDUCATION' })} addLabel="Add education"
               empty={profile.education.length === 0 ? 'Add your school or college.' : null}>
        {entryList('EDUCATION', profile.education)}
      </Section>

      <Section title="Licenses & certifications" empty={profile.certificates.length === 0 ? 'Certificates you earn from courses on this portal appear here automatically.' : null} mine={false}>
        <ul className="entry-list">
          {profile.certificates.map((c) => (
            <li key={c.id}>
              <div className="entry-icon" aria-hidden="true">🏅</div>
              <div className="entry-body">
                <strong>{c.courseTitle}</strong>
                <div>Secure Content Portal</div>
                <div className="muted">Issued {new Date(c.issuedAt).toLocaleDateString(undefined, { month: 'short', year: 'numeric' })} · Credential ID {c.code}</div>
                <Link className="btn btn-sm" to={`/verify/${c.code}`}>Show credential</Link>
              </div>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="Skills" mine={mine} onAdd={() => setModal({ type: 'entry', kind: 'SKILL' })} addLabel="Add skill"
               empty={profile.skills.length === 0 ? 'Add skills people can recognise you for.' : null}>
        <ul className="skill-list">
          {profile.skills.map((s) => (
            <li key={s.id}>{s.title}
              {mine && <button type="button" className="skill-x" aria-label={`Remove ${s.title}`} onClick={() => setToDelete(s)}>✕</button>}
            </li>
          ))}
        </ul>
      </Section>

      <section className="profile-section">
        <header><h2>Activity</h2></header>
        {posts.length === 0 && <p className="muted">{mine ? 'Share your first post from the feed.' : 'No posts yet.'}</p>}
        {posts.map((p) => <PostCard key={p.id} initial={p} onDelete={confirmDeletePost} onTogglePin={() => {}} />)}
        {hasMore && <button type="button" className="btn load-more" onClick={() => loadPosts(page + 1)}>Show more</button>}
      </section>

      <Modal open={modal?.type === 'details'} title="Edit profile" onClose={() => setModal(null)}>
        {modal?.type === 'details' && <DetailsForm profile={profile} onSaved={load} onClose={() => setModal(null)} />}
      </Modal>
      <Modal open={modal?.type === 'entry'} title={modal?.kind ? KIND_LABEL[modal.kind][modal.entry ? 'title' : 'add'] : ''} onClose={() => setModal(null)}>
        {modal?.type === 'entry' && <EntryForm kind={modal.kind} entry={modal.entry} onSaved={load} onClose={() => setModal(null)} />}
      </Modal>
      <ConfirmDialog open={!!toDelete} title={toDelete?.title || ''} detail="It will be removed from your profile" onCancel={() => setToDelete(null)} onConfirm={confirmDeleteEntry} />
    </div>
  );
}
