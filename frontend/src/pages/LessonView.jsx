import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import LearnShell from '../components/learn/LearnShell';
import NotesPanel from '../components/learn/NotesPanel';
import VideoViewer from './viewers/VideoViewer';
import { MATERIAL_ICON, MATERIAL_LABEL } from '../lib/materials';
import { clock } from '../lib/time';

const SAVE_EVERY_MS = 10_000;

function initials(name) {
  const parts = (name || '').trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

/** A fresh ticket is fetched on every mount — tickets are short-lived and session-bound, never cached. */
export default function LessonView() {
  const { courseId, lessonId } = useParams();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();
  const videoRef = useRef(null);
  const cuesRef = useRef(null);
  const lastSavedAt = useRef(0);
  const [data, setData] = useState(null);
  const [outline, setOutline] = useState(null);
  const [error, setError] = useState(null);
  const [activeCue, setActiveCue] = useState(-1);
  const [completed, setCompleted] = useState(false);
  const [tab, setTab] = useState('overview');

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setError(null);
    setTab('overview');
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

  const isAdmin = Boolean(user?.admin);
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

    const requested = Number(searchParams.get('t'));
    const startAt = requested > 0 ? requested : data.resumeSeconds;
    const resume = () => {
      if (startAt > 0) video.currentTime = startAt;
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, saveProgress]);

  // Keep the spoken line in view inside the transcript panel without ever scrolling the page itself.
  useEffect(() => {
    const box = cuesRef.current;
    const line = box?.querySelector('.transcript-cue.active');
    if (box && line) {
      box.scrollTo({ top: line.offsetTop - box.clientHeight / 2 + line.clientHeight / 2, behavior: 'smooth' });
    }
  }, [activeCue, tab]);

  function seekTo(seconds) {
    const video = videoRef.current;
    if (!video) return;
    video.currentTime = seconds;
    video.play().catch(() => {});
  }

  if (error) {
    return (
      <LearnShell courseId={courseId} outline={outline} current={{ lessonId }} isAdmin={isAdmin}>
        <div className="ln-stage">
          <p className="field-error">{error}</p>
          <Link className="btn" to={`/courses/${courseId}`}>Back to the course</Link>
        </div>
      </LearnShell>
    );
  }
  if (!data || !outline) {
    return (
      <LearnShell courseId={courseId} outline={null} current={{ lessonId }} isAdmin={isAdmin}>
        <div className="ln-stage"><div className="ln-video-skeleton" /></div>
      </LearnShell>
    );
  }

  const { lesson, transcript } = data;
  const module = outline.modules.find((m) => m.id === data.moduleId);
  const materials = module?.materials || [];
  const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'notes', label: 'Notes' },
    ...(transcript.length > 0 ? [{ id: 'transcript', label: 'Transcript' }] : []),
    ...(materials.length > 0 ? [{ id: 'resources', label: 'Resources', count: materials.length }] : []),
  ];

  return (
    <LearnShell
      courseId={courseId}
      outline={outline}
      current={{ lessonId }}
      doneOverride={{ [lessonId]: completed }}
      progressPercent={outline.progressPercent}
      isAdmin={isAdmin}
    >
      <div className="ln-stage">
        <VideoViewer
          key={lessonId}
          ticket={data.ticket}
          viewerEmail={user?.email}
          streamPath="/api/course-stream"
          hlsSrc={data.hlsReady ? `/api/course-stream/${data.ticket}/hls/master.m3u8` : null}
          videoRef={videoRef}
        />

        <div className="ln-title-row">
          <div>
            <p className="ln-kicker">{data.moduleTitle}</p>
            <h1 className="ln-title">{lesson.title}</h1>
          </div>
          <div className="ln-actions">
            {data.previousLessonId ? (
              <Link className="ln-icon-btn ring" to={`/courses/${courseId}/lessons/${data.previousLessonId}`} aria-label="Previous lesson" title="Previous lesson">
                <Icon name="chevron-left" size={20} />
              </Link>
            ) : (
              <span className="ln-icon-btn ring disabled" aria-hidden="true"><Icon name="chevron-left" size={20} /></span>
            )}
            {enrolled && !isAdmin && (
              <button type="button" className={`ln-btn${completed ? ' done' : ' primary'}`} disabled={completed} onClick={() => saveProgress(true)}>
                <Icon name="check" size={16} strokeWidth={2.6} /> {completed ? 'Completed' : 'Mark complete'}
              </button>
            )}
            {data.nextLessonId ? (
              <Link className="ln-btn dark" to={`/courses/${courseId}/lessons/${data.nextLessonId}`}>
                Next lesson <Icon name="chevron-right" size={16} />
              </Link>
            ) : (
              <Link className="ln-btn dark" to={`/courses/${courseId}`}>Finish <Icon name="chevron-right" size={16} /></Link>
            )}
          </div>
        </div>

        <div className="ln-tabs" role="tablist">
          {tabs.map((t) => (
            <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} className={tab === t.id ? 'active' : ''} onClick={() => setTab(t.id)}>
              {t.label}{t.count ? <span className="ln-tab-count">{t.count}</span> : null}
            </button>
          ))}
        </div>

        <div className="ln-panel" role="tabpanel" key={tab}>
          {tab === 'overview' && (
            <div className="ln-overview">
              {lesson.description && (
                <section className="ln-lesson-about">
                  <p className="ln-label">About this lesson</p>
                  <p className="ln-prose">{lesson.description}</p>
                </section>
              )}

              <section className="ln-about">
                <p className="ln-label">About this course</p>
                <h2>{outline.course.title}</h2>
                {outline.course.description && <p className="ln-prose">{outline.course.description}</p>}

                {outline.course.instructorName && (
                  <div className="ln-byline">
                    <span className="ln-byline-avatar" aria-hidden="true">{initials(outline.course.instructorName)}</span>
                    <span className="ln-byline-text">
                      <small>Created by</small>
                      <strong>{outline.course.instructorName}</strong>
                    </span>
                  </div>
                )}

                <dl className="ln-stats">
                  <div><dt>Sections</dt><dd>{outline.course.moduleCount}</dd></div>
                  <div><dt>Lessons</dt><dd>{outline.course.lessonCount}</dd></div>
                  {outline.course.category && <div><dt>Topic</dt><dd className="text">{outline.course.category}</dd></div>}
                  {enrolled && !isAdmin && <div><dt>Your progress</dt><dd>{outline.progressPercent}%</dd></div>}
                </dl>
              </section>
            </div>
          )}

          {tab === 'notes' && (
            <NotesPanel courseId={courseId} lessonId={lessonId} videoRef={videoRef} canWrite={enrolled && !isAdmin} onSeek={seekTo} />
          )}

          {tab === 'transcript' && (
            <ol className="ln-cues" ref={cuesRef}>
              {transcript.map((cue, index) => (
                <li key={`${cue.start}-${index}`}>
                  <button type="button" className={`transcript-cue${index === activeCue ? ' active' : ''}`} onClick={() => seekTo(cue.start)}>
                    <span className="transcript-time">{clock(cue.start)}</span>
                    <span>{cue.text}</span>
                  </button>
                </li>
              ))}
            </ol>
          )}

          {tab === 'resources' && (
            <ul className="ln-resource-grid">
              {materials.map((m) => (
                <li key={m.id}>
                  <Link className="ln-resource-card" to={`/courses/${courseId}/materials/${m.id}`}>
                    <span className="ln-resource-icon"><Icon name={MATERIAL_ICON[m.kind]} size={20} /></span>
                    <span className="ln-resource-text">
                      <strong>{m.title}</strong>
                      <span>{MATERIAL_LABEL[m.kind]}{m.sizeLabel ? ` · ${m.sizeLabel}` : ''}</span>
                    </span>
                    {m.downloadable && m.kind !== 'LINK' && <span className="ln-chip">Download</span>}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </LearnShell>
  );
}
