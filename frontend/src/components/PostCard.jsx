import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { API_BASE, api } from '../api';
import { useAuth } from '../context/AuthContext';

const REACTIONS = [
  { type: 'LIKE', label: 'Like', emoji: '👍' },
  { type: 'CELEBRATE', label: 'Celebrate', emoji: '🎉' },
  { type: 'INSIGHTFUL', label: 'Insightful', emoji: '💡' },
];

function ago(iso) {
  const seconds = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  if (seconds < 86400 * 30) return `${Math.floor(seconds / 86400)}d ago`;
  return new Date(iso).toLocaleDateString();
}

function Avatar({ name, url }) {
  return url ? <img className="avatar" src={url} alt="" /> : <span className="avatar">{(name || '?').slice(0, 1).toUpperCase()}</span>;
}

function Comments({ postId, onCount }) {
  const [comments, setComments] = useState(null);
  const [text, setText] = useState('');
  const [error, setError] = useState(null);

  useEffect(() => {
    api.get(`/api/posts/${postId}/comments`).then(setComments).catch((e) => setError(e.message));
  }, [postId]);

  async function add(e) {
    e.preventDefault();
    setError(null);
    try {
      const created = await api.post(`/api/posts/${postId}/comments`, { body: text });
      setComments((prev) => [...(prev || []), created]);
      setText('');
      onCount(1);
    } catch (err) {
      setError(err.message);
    }
  }

  async function remove(id) {
    try {
      await api.del(`/api/comments/${id}`);
      setComments((prev) => prev.filter((c) => c.id !== id));
      onCount(-1);
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="comments">
      {(comments || []).map((c) => (
        <div className="comment" key={c.id}>
          <Avatar name={c.userName} url={c.userPictureUrl} />
          <div className="comment-body">
            <strong>{c.userName}</strong> <span className="muted">{ago(c.createdAt)}</span>
            <p>{c.body}</p>
          </div>
          {c.canDelete && (
            <button type="button" className="link-button" onClick={() => remove(c.id)}>Delete</button>
          )}
        </div>
      ))}
      <form className="comment-form" onSubmit={add}>
        <input value={text} onChange={(e) => setText(e.target.value)} maxLength={1000} placeholder="Add a comment…" />
        <button type="submit" className="btn" disabled={!text.trim()}>Post</button>
      </form>
      {error && <p className="form-error">{error}</p>}
    </div>
  );
}

export default function PostCard({ initial, onDelete, onTogglePin }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [post, setPost] = useState(initial);
  const [showComments, setShowComments] = useState(false);
  const [copied, setCopied] = useState(false);
  const [enrolling, setEnrolling] = useState(false);

  async function react(type) {
    const updated = post.myReaction === type
      ? await api.del(`/api/posts/${post.id}/reaction`)
      : await api.put(`/api/posts/${post.id}/reaction`, { type });
    setPost(updated);
  }

  async function enroll() {
    setEnrolling(true);
    try {
      await api.post(`/api/courses/${post.course.id}/enroll`);
      navigate(`/courses/${post.course.id}`);
    } finally {
      setEnrolling(false);
    }
  }

  function share() {
    const url = `${window.location.origin}/feed#post-${post.id}`;
    navigator.clipboard?.writeText(url).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }

  return (
    <article className={`post-card${post.pinned ? ' pinned' : ''}`} id={`post-${post.id}`}>
      <header className="post-head">
        <Avatar name={post.authorName} url={post.authorPictureUrl} />
        <div>
          <strong>{post.authorName}</strong>
          <div className="muted">
            {post.scheduled ? `Scheduled for ${new Date(post.publishAt).toLocaleString()}` : ago(post.publishAt)}
            {post.pinned && ' · 📌 Pinned'}
          </div>
        </div>
        {user?.admin && (
          <div className="post-admin">
            <button type="button" className="link-button" onClick={() => onTogglePin(post, setPost)}>
              {post.pinned ? 'Unpin' : 'Pin'}
            </button>
            <button type="button" className="link-button danger" onClick={() => onDelete(post)}>Delete</button>
          </div>
        )}
      </header>

      <p className="post-body">{post.body}</p>
      {post.imageUrl && <img className="post-image" src={`${API_BASE}${post.imageUrl}`} alt="" loading="lazy" />}

      {post.course && (
        <div className="promo-card">
          {post.course.thumbnailUrl && <img src={`${API_BASE}${post.course.thumbnailUrl}`} alt="" loading="lazy" />}
          <div className="promo-info">
            {post.course.category && <span className="badge">{post.course.category}</span>}
            <h3>{post.course.title}</h3>
            {post.course.description && <p>{post.course.description}</p>}
            {post.course.enrolled ? (
              <Link className="btn" to={`/courses/${post.course.id}`}>Go to course</Link>
            ) : (
              <button type="button" className="btn btn-primary" onClick={enroll} disabled={enrolling}>
                {enrolling ? 'Enrolling…' : 'Enroll for free'}
              </button>
            )}
          </div>
        </div>
      )}

      {(post.reactionTotal > 0 || post.commentCount > 0) && (
        <div className="post-stats muted">
          <span>
            {REACTIONS.filter((r) => post.reactions[r.type]).map((r) => r.emoji).join(' ')}
            {post.reactionTotal > 0 && ` ${post.reactionTotal}`}
          </span>
          {post.commentCount > 0 && (
            <button type="button" className="link-button" onClick={() => setShowComments(true)}>
              {post.commentCount} comment{post.commentCount === 1 ? '' : 's'}
            </button>
          )}
        </div>
      )}

      <div className="post-actions">
        {REACTIONS.map((r) => (
          <button key={r.type} type="button" className={`react-btn${post.myReaction === r.type ? ' active' : ''}`}
                  onClick={() => react(r.type)} disabled={post.scheduled}>
            {r.emoji} {r.label}
          </button>
        ))}
        <button type="button" className="react-btn" onClick={() => setShowComments((v) => !v)} disabled={post.scheduled}>
          💬 Comment
        </button>
        <button type="button" className="react-btn" onClick={share} disabled={post.scheduled}>
          {copied ? '✓ Link copied' : '🔗 Share'}
        </button>
      </div>

      {showComments && (
        <Comments postId={post.id} onCount={(d) => setPost((p) => ({ ...p, commentCount: p.commentCount + d }))} />
      )}
    </article>
  );
}
