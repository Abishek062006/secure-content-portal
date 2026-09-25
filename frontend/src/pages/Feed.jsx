import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import Alert from '../components/Alert';
import ConfirmDialog from '../components/ConfirmDialog';
import PostCard from '../components/PostCard';

function Composer({ courses, presetCourseId, onCreated }) {
  const [body, setBody] = useState(presetCourseId ? 'New course is live! ' : '');
  const [courseId, setCourseId] = useState(presetCourseId || '');
  const [image, setImage] = useState(null);
  const [pinned, setPinned] = useState(false);
  const [publishAt, setPublishAt] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.append('body', body);
      form.append('pinned', pinned);
      if (courseId) form.append('courseId', courseId);
      if (image) form.append('image', image);
      if (publishAt) form.append('publishAt', new Date(publishAt).toISOString());
      const created = await api.upload('/api/admin/posts', form);
      setBody('');
      setCourseId('');
      setImage(null);
      setPinned(false);
      setPublishAt('');
      e.target.reset();
      onCreated(created);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="composer" onSubmit={submit}>
      <textarea value={body} onChange={(e) => setBody(e.target.value)} rows={3} maxLength={3000}
                placeholder="Share an update with your learners…" />
      <div className="composer-row">
        <select value={courseId} onChange={(e) => setCourseId(e.target.value)} aria-label="Promote a course">
          <option value="">No course attached</option>
          {courses.map((c) => <option key={c.id} value={c.id}>Promote: {c.title}</option>)}
        </select>
        <input type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => setImage(e.target.files[0] || null)} />
      </div>
      <div className="composer-row">
        <label><input type="checkbox" checked={pinned} onChange={(e) => setPinned(e.target.checked)} /> Pin to top</label>
        <label>Schedule <input type="datetime-local" value={publishAt} onChange={(e) => setPublishAt(e.target.value)} /></label>
        <button type="submit" className="btn btn-primary" disabled={busy || !body.trim()}>
          {busy ? 'Posting…' : publishAt ? 'Schedule' : 'Post'}
        </button>
      </div>
      {error && <p className="form-error">{error}</p>}
    </form>
  );
}

export default function Feed() {
  const { user } = useAuth();
  const [params] = useSearchParams();
  const [posts, setPosts] = useState([]);
  const [scheduled, setScheduled] = useState([]);
  const [courses, setCourses] = useState([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [toDelete, setToDelete] = useState(null);
  const [error, setError] = useState(null);

  const loadScheduled = useCallback(() => {
    if (user?.admin) api.get('/api/admin/posts/scheduled').then(setScheduled).catch(() => setScheduled([]));
  }, [user]);

  const load = useCallback(async (target) => {
    const res = await api.get(`/api/feed?page=${target}`);
    setPosts((prev) => (target === 0 ? res.posts : [...prev, ...res.posts]));
    setHasMore(res.hasMore);
    setPage(target);
  }, []);

  useEffect(() => {
    load(0).catch((e) => setError(e.message)).finally(() => setLoading(false));
    loadScheduled();
    if (user?.admin) api.get('/api/courses').then(setCourses).catch(() => setCourses([]));
  }, [load, loadScheduled, user]);

  useEffect(() => {
    if (!loading && window.location.hash) {
      document.getElementById(window.location.hash.slice(1))?.scrollIntoView({ block: 'center' });
    }
  }, [loading]);

  async function refresh() {
    await load(0);
    loadScheduled();
  }

  async function togglePin(post, setPost) {
    try {
      const updated = await api.put(`/api/admin/posts/${post.id}`, {
        body: post.body, courseId: post.course?.id ?? null, pinned: !post.pinned,
      });
      setPost(updated);
      refresh();
    } catch (e) {
      setError(e.message);
    }
  }

  async function confirmDelete() {
    const post = toDelete;
    setToDelete(null);
    try {
      await api.del(`/api/admin/posts/${post.id}`);
      await refresh();
    } catch (e) {
      setError(e.message);
    }
  }

  return (
    <div className="container feed">
      <div className="page-head">
        <h1>Feed</h1>
      </div>
      <Alert error={error} />

      {user?.admin && <Composer courses={courses} presetCourseId={params.get('promote')} onCreated={refresh} />}

      {user?.admin && scheduled.length > 0 && (
        <section>
          <h2 className="section-title">Scheduled</h2>
          {scheduled.map((p) => <PostCard key={p.id} initial={p} onDelete={setToDelete} onTogglePin={togglePin} />)}
        </section>
      )}

      {!loading && posts.length === 0 && (
        <div className="empty-state"><p>Nothing has been posted yet.</p></div>
      )}
      {posts.map((p) => <PostCard key={p.id} initial={p} onDelete={setToDelete} onTogglePin={togglePin} />)}
      {hasMore && (
        <button type="button" className="btn load-more" onClick={() => load(page + 1)}>Load more</button>
      )}

      <ConfirmDialog open={!!toDelete} title={toDelete ? toDelete.body.slice(0, 60) : ''}
                     detail="Its reactions and comments go with it"
                     onCancel={() => setToDelete(null)} onConfirm={confirmDelete} />
    </div>
  );
}
