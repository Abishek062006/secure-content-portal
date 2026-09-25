import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import VideoViewer from './viewers/VideoViewer';

function clock(seconds) {
  const total = Math.floor(seconds);
  const m = Math.floor(total / 60);
  const s = String(total % 60).padStart(2, '0');
  return `${m}:${s}`;
}

/** A fresh ticket is fetched on every mount — tickets are short-lived and session-bound, never cached. */
export default function CourseView() {
  const { id } = useParams();
  const { user } = useAuth();
  const videoRef = useRef(null);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [activeCue, setActiveCue] = useState(-1);

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setError(null);
    api.get(`/api/courses/${id}`)
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  useEffect(() => {
    const video = videoRef.current;
    if (!data || !video) return undefined;
    const onTimeUpdate = () => {
      const t = video.currentTime;
      setActiveCue(data.transcript.findIndex((cue) => t >= cue.start && t < cue.end));
    };
    video.addEventListener('timeupdate', onTimeUpdate);
    return () => video.removeEventListener('timeupdate', onTimeUpdate);
  }, [data]);

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
      </div>
    );
  }

  if (!data) {
    return (
      <div className="container-wide">
        <p className="pdf-loading">Loading…</p>
      </div>
    );
  }

  const { course, ticket, transcript } = data;

  return (
    <div className="container-wide">
      <h1>{course.title}</h1>
      {course.category && <span className="badge">{course.category}</span>}
      {course.description && <p className="field-hint">{course.description}</p>}

      <div className={`course-layout${transcript.length === 0 ? ' no-transcript' : ''}`}>
        <VideoViewer
          ticket={ticket}
          viewerEmail={user?.email}
          streamPath="/api/course-stream"
          videoRef={videoRef}
        />

        {transcript.length > 0 && (
          <aside className="transcript-panel" aria-label="Transcript">
            <h2>Transcript</h2>
            <ol>
              {transcript.map((cue, index) => (
                <li key={`${cue.start}-${index}`}>
                  <button
                    type="button"
                    className={`transcript-cue${index === activeCue ? ' active' : ''}`}
                    onClick={() => seekTo(cue)}
                  >
                    <span className="transcript-time">{clock(cue.start)}</span>
                    <span>{cue.text}</span>
                  </button>
                </li>
              ))}
            </ol>
          </aside>
        )}
      </div>
    </div>
  );
}
