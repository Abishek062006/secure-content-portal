import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import VideoViewer from './viewers/VideoViewer';

const SAVE_EVERY_MS = 10_000;

function clock(seconds) {
  const total = Math.floor(seconds);
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
}

/** A fresh ticket is fetched on every mount — tickets are short-lived and session-bound, never cached. */
export default function LessonView() {
  const { courseId, lessonId } = useParams();
  const { user } = useAuth();
  const videoRef = useRef(null);
  const lastSavedAt = useRef(0);
  const [data, setData] = useState(null);
  const [outline, setOutline] = useState(null);
  const [error, setError] = useState(null);
  const [activeCue, setActiveCue] = useState(-1);
  const [completed, setCompleted] = useState(false);
  const [tab, setTab] = useState('lessons');

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setError(null);
    Promise.all([api.get(`/api/courses/${courseId}/lessons/${lessonId}`), api.get(`/api/courses/${courseId}`)])
      .then(([lesson, course]) => {
        if (cancelled) return;
        setData(lesson);
        setOutline(course);
        setCompleted(lesson.completed);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [courseId, lessonId]);

  const enrolled = Boolean(outline?.enrolled);

  const saveProgress = useCallback((done) => {
    const video = videoRef.current;
    if (!video || !enrolled) return;
    lastSavedAt.current = Date.now();
    api.put(`/api/courses/${courseId}/lessons/${lessonId}/progress`, {
      positionSeconds: Math.floor(video.currentTime),
      completed: done,
    }).then((res) => {
      if (res.completed) setCompleted(true);
      setOutline((prev) => (prev ? { ...prev, progressPercent: res.progressPercent } : prev));
    }).catch(() => {});
  }, [courseId, lessonId, enrolled]);

  useEffect(() => {
    const video = videoRef.current;
    if (!data || !video) return undefined;

    const resume = () => {
      if (data.resumeSeconds > 0) video.currentTime = data.resumeSeconds;
    };
    if (video.readyState >= 1) resume();
    const onTimeUpdate = () => {
      setActiveCue(data.transcript.findIndex((cue) => video.currentTime >= cue.start && video.currentTime < cue.end));
      if (Date.now() - lastSavedAt.current > SAVE_EVERY_MS) saveProgress(false);
    };
    const onPause = () => saveProgress(false);
    const onEnded = () => saveProgress(true);

    video.addEventListener('loadedmetadata', resume);
    video.addEventListener('timeupdate', onTimeUpdate);
    video.addEventListener('pause', onPause);
    video.addEventListener('ended', onEnded);
    return () => {
      video.removeEventListener('loadedmetadata', resume);
      video.removeEventListener('timeupdate', onTimeUpdate);
      video.removeEventListener('pause', onPause);
      video.removeEventListener('ended', onEnded);
      saveProgress(false);
    };
  }, [data, saveProgress]);

  function seekTo(cue) {
    const video = videoRef.current;
    if (!video) return;
    video.currentTime = cue.start;
    video.play().catch(() => {});
  }

  if (error) {
    return (
      <div className="container-wide">
        <p className="field-error">{error}</p>
        <Link className="btn" to={`/courses/${courseId}`}>Back to the course</Link>
      </div>
    );
  }
  if (!data || !outline) {
    return (
      <div className="container-wide">
        <p className="pdf-loading">Loading…</p>
      </div>
    );
  }

  const { lesson, transcript } = data;

  return (
    <div className="container-wide">
      <p className="breadcrumb">
        <Link to={`/courses/${courseId}`}>{data.courseTitle}</Link> › {data.moduleTitle}
      </p>
      <h1>{lesson.title}</h1>

      <div className="course-layout lesson-layout">
        <div>
          <VideoViewer
            key={lessonId}
            ticket={data.ticket}
            viewerEmail={user?.email}
            streamPath="/api/course-stream"
            videoRef={videoRef}
          />
          {lesson.description && <p className="field-hint">{lesson.description}</p>}

          <div className="lesson-nav">
            {data.previousLessonId
              ? <Link className="btn" to={`/courses/${courseId}/lessons/${data.previousLessonId}`}>← Previous</Link>
              : <span />}
            {enrolled && (
              <button type="button" className="btn" disabled={completed} onClick={() => saveProgress(true)}>
                {completed ? '✓ Completed' : 'Mark as complete'}
              </button>
            )}
            {data.nextLessonId
              ? <Link className="btn btn-primary" to={`/courses/${courseId}/lessons/${data.nextLessonId}`}>Next →</Link>
              : <Link className="btn btn-primary" to={`/courses/${courseId}`}>Finish</Link>}
          </div>
        </div>

        <aside className="transcript-panel">
          <div className="side-tabs" role="tablist">
            <button type="button" role="tab" aria-selected={tab === 'lessons'} className={tab === 'lessons' ? 'active' : ''} onClick={() => setTab('lessons')}>Lessons</button>
            {transcript.length > 0 && (
              <button type="button" role="tab" aria-selected={tab === 'transcript'} className={tab === 'transcript' ? 'active' : ''} onClick={() => setTab('transcript')}>Transcript</button>
            )}
          </div>

          {tab === 'lessons' && (
            <>
              {enrolled && <p className="field-hint">{outline.progressPercent}% complete</p>}
              {outline.modules.map((module, mIndex) => (
                <div key={module.id} className="side-module">
                  <h3>{mIndex + 1}. {module.title}</h3>
                  <ol className="outline-lessons">
                    {module.lessons.map((l) => (
                      <li key={l.id} className={l.id === lessonId ? 'current' : ''}>
                        <Link to={`/courses/${courseId}/lessons/${l.id}`}>
                          <span className={`lesson-check${(l.id === lessonId ? completed : l.completed) ? ' done' : ''}`} aria-hidden="true">
                            {(l.id === lessonId ? completed : l.completed) ? '✓' : ''}
                          </span>
                          {l.title}
                        </Link>
                      </li>
                    ))}
                  </ol>
                </div>
              ))}
            </>
          )}

          {tab === 'transcript' && (
            <ol>
              {transcript.map((cue, index) => (
                <li key={`${cue.start}-${index}`}>
                  <button type="button" className={`transcript-cue${index === activeCue ? ' active' : ''}`} onClick={() => seekTo(cue)}>
                    <span className="transcript-time">{clock(cue.start)}</span>
                    <span>{cue.text}</span>
                  </button>
                </li>
              ))}
            </ol>
          )}
        </aside>
      </div>
    </div>
  );
}
