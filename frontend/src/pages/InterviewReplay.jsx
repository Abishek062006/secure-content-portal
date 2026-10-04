import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import fixWebmDuration from 'fix-webm-duration';
import { api } from '../api';
import Alert from '../components/Alert';
import ConfirmDialog from '../components/ConfirmDialog';
import Icon from '../components/Icon';
import { personaById } from '../components/interview/avatar/personas';
import { useAuth } from '../context/AuthContext';
import { answerDeliveryLine } from '../lib/delivery';
import { deleteRecording, formatBytes, formatLength, readRecording } from '../lib/localRecordings';
import { buildChapters, chapterAt } from '../lib/replayChapters';
import { clock } from '../lib/time';

const SPEEDS = [0.75, 1, 1.25, 1.5, 2];

/**
 * Watching an interview back. The video is read from this device's storage (it was never uploaded), with a chapter for every
 * question to jump between, and the questions and answers as text. If it isn't here, the page says why.
 */
export default function InterviewReplay() {
  const { id } = useParams();
  const sessionId = Number(id);
  const navigate = useNavigate();
  const { user } = useAuth();
  const video = useRef(null);
  const [recording, setRecording] = useState({ status: 'loading' });
  const [detail, setDetail] = useState(null);
  const [currentMs, setCurrentMs] = useState(0);
  const [speed, setSpeed] = useState(1);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (user?.id == null) return undefined;
    let live = true;
    let url = null;
    (async () => {
      try {
        const found = await readRecording(sessionId);
        if (!live) return;
        if (!found || found.meta.userId !== user.id) {
          setRecording({ status: 'missing' });
          return;
        }
        let blob = found.blob;
        // A browser's own WebM recording doesn't say how long it is, which leaves the seek bar unusable: fill that in.
        if (found.meta.mimeType.startsWith('video/webm') && found.meta.durationMs > 0) {
          blob = await fixWebmDuration(blob, found.meta.durationMs, { logger: false });
        }
        if (!live) return;
        url = URL.createObjectURL(blob);
        setRecording({ status: 'ready', meta: found.meta, url });
      } catch {
        if (live) setRecording({ status: 'error' });
      }
    })();
    return () => {
      live = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [sessionId, user?.id]);

  // The questions and answers as text; the replay still works without them.
  useEffect(() => {
    api.getInterview(id).then(setDetail).catch(() => setDetail(null));
  }, [id]);

  const meta = recording.status === 'ready' ? recording.meta : null;
  const chapters = useMemo(() => (meta ? buildChapters(meta.markers, meta.durationMs) : []), [meta]);
  const questions = useMemo(() => new Map((detail?.questions || []).map((q) => [q.id, q])), [detail]);
  const active = chapterAt(chapters, currentMs);
  const persona = personaById(detail?.session.interviewer);
  // Without the interview's details we don't know who asked, so the captions don't name anyone.
  const asker = detail ? persona.name : null;
  const questionText = (chapter) => questions.get(chapter.questionId)?.questionText || chapter.text;

  function jumpTo(ms) {
    const el = video.current;
    if (!el) return;
    el.currentTime = ms / 1000;
    el.play().catch(() => {});
  }

  function changeSpeed(value) {
    setSpeed(value);
    if (video.current) video.current.playbackRate = value;
  }

  async function confirmDelete() {
    setConfirming(false);
    try {
      await deleteRecording(sessionId);
      navigate(`/interview/${id}`);
    } catch {
      setError('That recording could not be deleted. Try again.');
    }
  }

  const back = <p className="breadcrumb"><Link to={`/interview/${id}`}>← Interview report</Link></p>;

  if (recording.status === 'loading') {
    return <div className="container">{back}<p className="pdf-loading">Loading your recording...</p></div>;
  }
  if (recording.status !== 'ready') {
    return (
      <div className="container">
        {back}
        <div className="empty-state">
          <p>
            {recording.status === 'error'
              ? "We couldn't open this recording."
              : "This recording isn't on this device."}
          </p>
          <p className="field-hint">
            Recordings are saved only in the browser where the interview was done. They're gone if they were deleted, if the browser's
            site data was cleared, or if the interview was recorded somewhere else.
          </p>
          <Link className="btn btn-primary" to={`/interview/${id}`}>Back to the report</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container-wide">
      <Alert error={error} />
      {back}
      <h1 className="page-title">Watch your interview</h1>
      <p className="field-hint">
        {meta.title || 'Interview'}{asker ? ` · with ${asker}` : ''}
        {' · '}{new Date(meta.startedAt).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}
        {' · '}{formatLength(meta.durationMs)}{' · '}{formatBytes(meta.sizeBytes)}
        {meta.status === 'interrupted' && ' · stopped early'}
      </p>

      <div className="replay-layout">
        <div className="replay-player">
          <video ref={video} className="replay-video" src={recording.url} controls playsInline preload="metadata"
                 aria-label="Your recorded interview"
                 onLoadedMetadata={(e) => { e.currentTarget.playbackRate = speed; }}
                 onTimeUpdate={(e) => setCurrentMs(e.currentTarget.currentTime * 1000)} />
          <p className="replay-caption" aria-live="off">
            {active ? <><strong>{asker ? `${asker} asked:` : 'Question:'}</strong> {questionText(active)}</> : 'Press play to watch from the start.'}
          </p>
          <div className="replay-tools">
            <label className="replay-speed">
              Speed
              <select value={speed} onChange={(e) => changeSpeed(Number(e.target.value))}>
                {SPEEDS.map((s) => <option key={s} value={s}>{s}x</option>)}
              </select>
            </label>
            <button type="button" className="btn btn-sm btn-danger-outline" onClick={() => setConfirming(true)}>
              <Icon name="trash-2" size={14} /> Delete this recording
            </button>
          </div>
          <p className="field-hint">
            Saved only on this device and never uploaded. {asker ? `${asker}'s` : "The interviewer's"} voice isn't recorded, so
            their questions show as captions.
          </p>
        </div>

        <div className="replay-side">
          <h2 className="replay-side-title">Questions</h2>
          {chapters.length === 0 ? (
            <p className="field-hint">No question markers were saved for this recording, so there are no chapters to jump between.</p>
          ) : (
            <ol className="replay-chapters">
              {chapters.map((chapter, i) => {
                const q = questions.get(chapter.questionId);
                const isActive = active?.questionId === chapter.questionId && active?.startMs === chapter.startMs;
                return (
                  <li key={`${chapter.questionId}-${chapter.startMs}`} className={isActive ? 'active' : ''}>
                    <button type="button" className="replay-chapter" aria-current={isActive ? 'true' : undefined}
                            onClick={() => jumpTo(chapter.startMs)}>
                      <span className="replay-chapter-time">{clock(chapter.startMs / 1000)}</span>
                      <span className="replay-chapter-text">{i + 1}. {questionText(chapter)}</span>
                      {q?.answeredAt && <span className="replay-chapter-score">{q.score}/10</span>}
                    </button>
                    {answerDeliveryLine(q) && <p className="replay-delivery">{answerDeliveryLine(q)}</p>}
                    {q?.learnerAnswer && (
                      <details className="replay-answer">
                        <summary>Your answer</summary>
                        <p>{q.learnerAnswer}</p>
                      </details>
                    )}
                  </li>
                );
              })}
            </ol>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={confirming}
        title={`Recording of ${meta.title || 'this interview'}`}
        detail="The video is deleted from this device. Your interview results are not affected."
        onCancel={() => setConfirming(false)}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
