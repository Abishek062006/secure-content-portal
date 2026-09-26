import { useEffect, useState } from 'react';
import { api } from '../../api';
import Icon from '../Icon';
import { clock } from '../../lib/time';

/** A learner's private notes on one lesson, each pinned to a moment in the video (click the time to jump back to it). */
export default function NotesPanel({ courseId, lessonId, videoRef, canWrite, onSeek }) {
  const [notes, setNotes] = useState(null);
  const [draft, setDraft] = useState('');
  const [pinnedAt, setPinnedAt] = useState(0);
  const [editing, setEditing] = useState(null); // { id, body }
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const base = `/api/courses/${courseId}/lessons/${lessonId}/notes`;

  useEffect(() => {
    let cancelled = false;
    setNotes(null);
    api.get(base)
      .then((list) => { if (!cancelled) setNotes(list); })
      .catch((err) => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [base]);

  function now() {
    return Math.floor(videoRef.current?.currentTime || 0);
  }

  async function add(event) {
    event.preventDefault();
    if (!draft.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const saved = await api.post(base, { body: draft, seconds: pinnedAt });
      setNotes((list) => [...(list || []), saved].sort((a, b) => a.seconds - b.seconds || a.id - b.id));
      setDraft('');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function saveEdit() {
    if (!editing?.body.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const saved = await api.put(`${base}/${editing.id}`, { body: editing.body });
      setNotes((list) => list.map((n) => (n.id === saved.id ? saved : n)));
      setEditing(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function remove(id) {
    setError(null);
    try {
      await api.del(`${base}/${id}`);
      setNotes((list) => list.filter((n) => n.id !== id));
    } catch (err) {
      setError(err.message);
    }
  }

  if (!canWrite) {
    return (
      <div className="ln-empty">
        <Icon name="pencil" size={22} />
        <p>Notes are for learners. Sign in as a learner and enroll to keep your own notes on each lesson.</p>
      </div>
    );
  }

  return (
    <div className="ln-notes">
      <form className="ln-note-form" onSubmit={add}>
        <textarea
          value={draft}
          maxLength={2000}
          rows={draft ? 3 : 2}
          placeholder="Write a note. It's pinned to the moment you start typing."
          onFocus={() => { if (!draft) setPinnedAt(now()); }}
          onChange={(e) => {
            if (!draft && e.target.value) setPinnedAt(now());
            setDraft(e.target.value);
          }}
        />
        <div className="ln-note-form-row">
          <span className="ln-time-chip"><Icon name="clock" size={13} /> {clock(pinnedAt)}</span>
          <span className="ln-count">{draft.length}/2000</span>
          <button type="submit" className="ln-btn primary" disabled={busy || !draft.trim()}>Save note</button>
        </div>
      </form>
      {error && <p className="field-error">{error}</p>}

      {notes === null ? (
        <p className="ln-muted">Loading your notes…</p>
      ) : notes.length === 0 ? (
        <p className="ln-muted">No notes yet. Anything you write here is private to you.</p>
      ) : (
        <ol className="ln-note-list">
          {notes.map((note) => (
            <li key={note.id} className="ln-note">
              <button type="button" className="ln-time-chip link" onClick={() => onSeek(note.seconds)} title="Jump to this moment">
                <Icon name="play" size={11} /> {clock(note.seconds)}
              </button>
              {editing?.id === note.id ? (
                <div className="ln-note-body">
                  <textarea value={editing.body} maxLength={2000} rows={3} autoFocus onChange={(e) => setEditing({ ...editing, body: e.target.value })} />
                  <div className="ln-note-actions">
                    <button type="button" className="ln-btn" onClick={() => setEditing(null)}>Cancel</button>
                    <button type="button" className="ln-btn primary" disabled={busy || !editing.body.trim()} onClick={saveEdit}>Save</button>
                  </div>
                </div>
              ) : (
                <div className="ln-note-body">
                  <p>{note.body}</p>
                  <div className="ln-note-tools">
                    <button type="button" className="ln-icon-btn small" aria-label="Edit note" onClick={() => setEditing({ id: note.id, body: note.body })}><Icon name="pencil" size={15} /></button>
                    <button type="button" className="ln-icon-btn small danger" aria-label="Delete note" onClick={() => remove(note.id)}><Icon name="trash-2" size={15} /></button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
