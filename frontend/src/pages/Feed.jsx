import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import Alert from '../components/Alert';
import Avatar from '../components/Avatar';
import Icon from '../components/Icon';
import ComposerModal from '../components/ComposerModal';
import ConfirmDialog from '../components/ConfirmDialog';
import PostCard from '../components/PostCard';
import PriceTag from '../components/PriceTag';
import ProfileCard from '../components/ProfileCard';
import { mediaUrl } from '../lib/media';

function RecommendedCourses({ courses }) {
  if (!courses.length) return null;
  return (
    <aside className="side-card">
      <h2 className="side-title">Recommended courses</h2>
      <ul className="side-list">
        {courses.slice(0, 4).map((course) => (
          <li key={course.id}>
            {course.thumbnailUrl
              ? <img className="side-thumb" src={mediaUrl(course.thumbnailUrl)} alt="" />
              : <span className="side-thumb side-thumb-empty"><Icon name="play" size={18} /></span>}
            <div>
              <Link to={`/courses/${course.id}`} className="side-link">{course.title}</Link>
              <div className="muted">{course.lessonCount} lesson{course.lessonCount === 1 ? '' : 's'}</div>
              <PriceTag pricing={course.pricing} compact />
            </div>
          </li>
        ))}
      </ul>
      <Link to="/courses" className="side-more">Show all courses →</Link>
    </aside>
  );
}

export default function Feed() {
  const { user } = useAuth();
  const [params] = useSearchParams();
  const [me, setMe] = useState(null);
  const [posts, setPosts] = useState([]);
  const [scheduled, setScheduled] = useState([]);
  const [courses, setCourses] = useState([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [toDelete, setToDelete] = useState(null);
  const [error, setError] = useState(null);
  const [composer, setComposer] = useState({ open: false, mode: 'post' });

  const loadScheduled = useCallback(() => {
    if (user?.admin) api.get('/api/admin/posts/scheduled').then(setScheduled).catch(() => setScheduled([]));
  }, [user]);

  const load = useCallback(async (target) => {
    const res = await api.get(`/api/feed?page=${target}`);
    setPosts((prev) => (target === 0 ? res.posts : [...prev, ...res.posts]));
    setHasMore(res.hasMore);
    setPage(target);
  }, []);

  const loadMe = useCallback(() => api.get('/api/profile').then(setMe).catch(() => setMe(null)), []);

  useEffect(() => {
    load(0).catch((e) => setError(e.message)).finally(() => setLoading(false));
    loadScheduled();
    loadMe();
    api.get('/api/courses').then(setCourses).catch(() => setCourses([]));
  }, [load, loadScheduled, loadMe]);

  // "Promote this course" on the course editor lands here with the composer already open.
  useEffect(() => {
    if (params.get('promote')) setComposer({ open: true, mode: 'post' });
  }, [params]);

  useEffect(() => {
    if (!loading && window.location.hash) {
      document.getElementById(window.location.hash.slice(1))?.scrollIntoView({ block: 'center' });
    }
  }, [loading]);

  async function refresh() {
    await load(0);
    loadScheduled();
    loadMe();
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
      await api.del(`/api/posts/${post.id}`);
      await refresh();
    } catch (e) {
      setError(e.message);
    }
  }

  const open = (mode) => setComposer({ open: true, mode });

  return (
    <div className="social-layout">
      <div className="social-left"><ProfileCard me={me} /></div>

      <main className="social-main">
        <Alert error={error} />

        <section className="start-post">
          <div className="start-post-top">
            <Avatar name={user?.displayName} url={me?.avatarUrl} userId={user?.id} size={48} />
            <button type="button" className="start-post-input" onClick={() => open('post')}>Start a post</button>
          </div>
          <div className="start-post-actions">
            <button type="button" onClick={() => open('post')}><Icon name="video" className="sp-icon" /> Video</button>
            <button type="button" onClick={() => open('post')}><Icon name="image" className="sp-icon" /> Photo</button>
            <button type="button" onClick={() => open('certificate')}><Icon name="award" className="sp-icon" /> Certificate</button>
            <button type="button" onClick={() => open('article')}><Icon name="file-text" className="sp-icon" /> Write article</button>
          </div>
        </section>

        {user?.admin && scheduled.length > 0 && (
          <section>
            <h2 className="section-title">Scheduled</h2>
            {scheduled.map((p) => <PostCard key={p.id} initial={p} onDelete={setToDelete} onTogglePin={togglePin} />)}
          </section>
        )}

        {!loading && posts.length === 0 && (
          <div className="empty-state"><p>Nothing has been posted yet. Be the first!</p></div>
        )}
        {posts.map((p) => <PostCard key={p.id} initial={p} onDelete={setToDelete} onTogglePin={togglePin} />)}
        {hasMore && <button type="button" className="btn load-more" onClick={() => load(page + 1)}>Load more</button>}
      </main>

      <div className="social-right"><RecommendedCourses courses={courses} /></div>

      <ComposerModal open={composer.open} mode={composer.mode} me={me} courses={courses.filter((c) => c.status !== 'DRAFT')}
                     presetCourseId={params.get('promote')} onClose={() => setComposer((c) => ({ ...c, open: false }))}
                     onCreated={refresh} />
      <ConfirmDialog open={!!toDelete} title={toDelete ? (toDelete.title || toDelete.body).slice(0, 60) : ''}
                     detail="Its reactions and comments go with it"
                     onCancel={() => setToDelete(null)} onConfirm={confirmDelete} />
    </div>
  );
}
