import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { API_BASE, api } from '../api';
import { useAuth } from '../context/AuthContext';
import PriceTag from './PriceTag';

/** Emoji artwork: Twemoji (CC-BY 4.0), stored in /public/reactions. */
const REACTIONS = [
  { type: 'LIKE', label: 'Like', color: '#378fe9' },
  { type: 'CELEBRATE', label: 'Celebrate', color: '#6dae4f' },
  { type: 'SUPPORT', label: 'Support', color: '#9b6dc6' },
  { type: 'LOVE', label: 'Love', color: '#df704d' },
  { type: 'INSIGHTFUL', label: 'Insightful', color: '#f5b84c' },
  { type: 'FUNNY', label: 'Funny', color: '#33aebb' },
];
const BY_TYPE = Object.fromEntries(REACTIONS.map((r) => [r.type, r]));
const icon = (type) => `/reactions/${type.toLowerCase()}.svg`;

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

const Svg = ({ children }) => (
  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8"
       strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{children}</svg>
);
const ThumbIcon = () => <Svg><path d="M7 11v9H4v-9h3zM7 11l4-8c1.5 0 2.5 1 2.5 2.5V9H19a2 2 0 0 1 2 2.3l-1.2 7A2 2 0 0 1 17.8 20H7" /></Svg>;
const CommentIcon = () => <Svg><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.4-4.6A8 8 0 1 1 21 12z" /></Svg>;
const ShareIcon = () => <Svg><path d="M22 3 11 14M22 3l-7 19-4-8-8-4 19-7z" /></Svg>;

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

/** The Like button: click to like or undo; hover, focus or press-and-hold opens the row of reactions. */
function ReactionButton({ mine, disabled, onPick }) {
  const [open, setOpen] = useState(false);
  const closeTimer = useRef(null);
  const holdTimer = useRef(null);
  const held = useRef(false);

  const show = () => { clearTimeout(closeTimer.current); setOpen(true); };
  const hide = () => { clearTimeout(closeTimer.current); closeTimer.current = setTimeout(() => setOpen(false), 250); };
  useEffect(() => () => { clearTimeout(closeTimer.current); clearTimeout(holdTimer.current); }, []);

  const current = mine ? BY_TYPE[mine] : null;
  return (
    <div className="react-wrap" onMouseEnter={disabled ? undefined : show} onMouseLeave={hide} onFocus={disabled ? undefined : show} onBlur={hide}>
      {open && (
        <div className="reaction-picker" role="menu">
          {REACTIONS.map((r) => (
            <button key={r.type} type="button" role="menuitem" className="reaction-option" aria-label={r.label}
                    onClick={() => { setOpen(false); onPick(r.type); }}>
              <img src={icon(r.type)} alt="" />
              <span className="reaction-tip">{r.label}</span>
            </button>
          ))}
        </div>
      )}
      <button type="button" className={`action-btn${current ? ' active' : ''}`} disabled={disabled}
              style={current ? { color: current.color } : undefined}
              onTouchStart={() => { held.current = false; holdTimer.current = setTimeout(() => { held.current = true; setOpen(true); }, 400); }}
              onTouchEnd={() => clearTimeout(holdTimer.current)}
              onClick={(e) => { if (held.current) { held.current = false; e.preventDefault(); return; } onPick(current ? current.type : 'LIKE'); }}>
        {current ? <img className="action-emoji" src={icon(current.type)} alt="" /> : <ThumbIcon />}
        {current ? current.label : 'Like'}
      </button>
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

  /** Picking your current reaction again removes it; picking another switches to it. */
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

  const topReactions = REACTIONS.filter((r) => post.reactions[r.type]).sort((a, b) => post.reactions[b.type] - post.reactions[a.type]).slice(0, 3);

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
      {post.videoUrl && (
        <video className="post-video" controls playsInline preload="metadata" controlsList="nodownload"
               onContextMenu={(e) => e.preventDefault()} src={`${API_BASE}${post.videoUrl}`} />
      )}

      {post.course && (
        <div className="promo-card">
          {post.course.thumbnailUrl && <img src={`${API_BASE}${post.course.thumbnailUrl}`} alt="" loading="lazy" />}
          <div className="promo-info">
            {post.course.category && <span className="badge">{post.course.category}</span>}
            <h3>{post.course.title}</h3>
            {post.course.description && <p>{post.course.description}</p>}
            <PriceTag pricing={post.course.pricing} />
            {post.course.enrolled ? (
              <Link className="btn" to={`/courses/${post.course.id}`}>Go to course</Link>
            ) : (
              <button type="button" className="btn btn-primary" onClick={enroll} disabled={enrolling}>
                {enrolling ? 'Enrolling…' : post.course.pricing?.free === false ? 'Enroll' : 'Enroll for free'}
              </button>
            )}
          </div>
        </div>
      )}

      {(post.reactionTotal > 0 || post.commentCount > 0) && (
        <div className="post-stats">
          <span className="stat-reactions">
            <span className="emoji-stack">
              {topReactions.map((r) => <img key={r.type} src={icon(r.type)} alt={r.label} title={r.label} />)}
            </span>
            {post.reactionTotal > 0 && <span>{post.reactionTotal}</span>}
          </span>
          {post.commentCount > 0 && (
            <button type="button" className="link-button muted-link" onClick={() => setShowComments(true)}>
              {post.commentCount} comment{post.commentCount === 1 ? '' : 's'}
            </button>
          )}
        </div>
      )}

      <div className="post-actions">
        <ReactionButton mine={post.myReaction} disabled={post.scheduled} onPick={react} />
        <button type="button" className="action-btn" onClick={() => setShowComments((v) => !v)} disabled={post.scheduled}>
          <CommentIcon /> Comment
        </button>
        <button type="button" className="action-btn" onClick={share} disabled={post.scheduled}>
          <ShareIcon /> {copied ? 'Link copied' : 'Share'}
        </button>
      </div>

      {showComments && (
        <Comments postId={post.id} onCount={(d) => setPost((p) => ({ ...p, commentCount: p.commentCount + d }))} />
      )}
    </article>
  );
}
