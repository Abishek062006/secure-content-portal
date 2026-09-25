import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';
import CoverPicker from '../../components/CoverPicker';

const MAX_VIDEO_BYTES = 5 * 1024 ** 3;

function titleFromFilename(name) {
  return name.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ').trim();
}

function formatSize(bytes) {
  return bytes >= 1024 ** 3 ? `${(bytes / 1024 ** 3).toFixed(1)} GB` : `${Math.max(1, Math.round(bytes / 1024 ** 2))} MB`;
}

/**
 * One page to create a whole course: details, cover, and its videos. The videos go into a single
 * default section; the editor can split them into sections later if the course needs it.
 */
export default function NewCourse() {
  const navigate = useNavigate();
  const videoInput = useRef(null);
  const [errorMessage, setErrorMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [status, setStatus] = useState('');
  const [progress, setProgress] = useState(0);
  const [coverFile, setCoverFile] = useState(null);
  const [videos, setVideos] = useState([]);

  function addVideos(fileList) {
    setErrorMessage(null);
    const added = [];
    for (const file of fileList) {
      if (!/\.(mp4|webm)$/i.test(file.name)) {
        setErrorMessage(`${file.name} isn't an .mp4 or .webm video.`);
        continue;
      }
      if (file.size > MAX_VIDEO_BYTES) {
        setErrorMessage(`${file.name} is over the 5 GB limit.`);
        continue;
      }
      added.push({ key: `${file.name}-${file.size}-${Math.random()}`, file, title: titleFromFilename(file.name), transcript: null });
    }
    setVideos((prev) => [...prev, ...added]);
  }

  const updateVideo = (key, patch) => setVideos((prev) => prev.map((v) => (v.key === key ? { ...v, ...patch } : v)));

  function move(index, delta) {
    setVideos((prev) => {
      const next = [...prev];
      const to = index + delta;
      if (to < 0 || to >= next.length) return prev;
      [next[index], next[to]] = [next[to], next[index]];
      return next;
    });
  }

  async function onSubmit(e) {
    e.preventDefault();
    setErrorMessage(null);
    setSubmitting(true);

    const form = e.currentTarget;
    const details = new FormData();
    details.set('title', form.title.value);
    details.set('description', form.description.value);
    details.set('category', form.category.value);
    if (coverFile) details.set('thumbnail', coverFile);

    let course;
    try {
      setStatus('Creating course…');
      course = await api.upload('/api/admin/courses', details);
    } catch (err) {
      setErrorMessage(err.message);
      setSubmitting(false);
      return;
    }

    const failed = [];
    if (videos.length > 0) {
      try {
        const module = await api.post(`/api/admin/courses/${course.id}/modules`, { title: 'Course content' });
        for (let i = 0; i < videos.length; i += 1) {
          const video = videos[i];
          setStatus(`Uploading video ${i + 1} of ${videos.length}: ${video.title}`);
          setProgress(0);
          const data = new FormData();
          data.set('title', video.title || titleFromFilename(video.file.name));
          data.set('video', video.file);
          if (video.transcript) data.set('transcript', video.transcript);
          try {
            // eslint-disable-next-line no-await-in-loop
            await api.uploadWithProgress(`/api/admin/modules/${module.id}/lessons`, data, setProgress);
          } catch (err) {
            failed.push(`${video.title}: ${err.message}`);
          }
        }
      } catch (err) {
        failed.push(err.message);
      }
    }

    navigate(`/admin/courses/${course.id}/edit`, {
      state: failed.length
        ? { error: `Course created, but some videos didn't upload — add them again below. ${failed.join(' · ')}` }
        : { success: videos.length ? 'Course created with your videos.' : 'Course created. Add your videos below.' },
    });
  }

  return (
    <div className="container container-course-form">
      <Alert error={errorMessage} />
      <h1>New course</h1>
      <p className="page-sub">Add the details and your video(s) here, then set up quizzes on the next screen.</p>

      <form className="form-panel course-form" onSubmit={onSubmit}>
        <div className="course-form-cover">
          <label>Cover image</label>
          <CoverPicker onPick={setCoverFile} onRemove={() => setCoverFile(null)} />
        </div>

        <div className="course-form-fields">
          <div className="field">
            <label htmlFor="title">Title</label>
            <input id="title" name="title" type="text" maxLength={200} required disabled={submitting} />
          </div>
          <div className="field">
            <label htmlFor="description">Description</label>
            <textarea id="description" name="description" maxLength={2000} rows={4} disabled={submitting} />
          </div>
          <div className="field">
            <label htmlFor="category">Category</label>
            <input id="category" name="category" type="text" maxLength={80} placeholder="e.g. Programming" disabled={submitting} />
          </div>
        </div>

        <div className="course-form-videos">
          <div className="videos-head">
            <div>
              <label>Videos</label>
              <p className="field-hint">One video or many. Add a .vtt transcript to a video to generate quiz questions from it.</p>
            </div>
            <button type="button" className="btn" onClick={() => videoInput.current.click()} disabled={submitting}>
              + Add video{videos.length ? 's' : ''}
            </button>
          </div>
          <input ref={videoInput} type="file" hidden multiple accept=".mp4,.webm"
                 onChange={(e) => { addVideos(Array.from(e.target.files)); e.target.value = ''; }} />

          {videos.length === 0 ? (
            <button type="button" className="video-drop" onClick={() => videoInput.current.click()} disabled={submitting}>
              <strong>Choose your video file(s)</strong>
              <span>.mp4 or .webm, up to 5 GB each. You can also skip this and add videos later.</span>
            </button>
          ) : (
            <ol className="video-list">
              {videos.map((v, i) => (
                <li key={v.key}>
                  <span className="video-num">{i + 1}</span>
                  <div className="video-fields">
                    <input type="text" value={v.title} maxLength={200} required disabled={submitting}
                           aria-label={`Title of video ${i + 1}`} onChange={(e) => updateVideo(v.key, { title: e.target.value })} />
                    <span className="field-hint">{v.file.name} · {formatSize(v.file.size)}</span>
                    <label className="transcript-pick">
                      {v.transcript ? `Transcript: ${v.transcript.name}` : '+ Add transcript (.vtt)'}
                      <input type="file" hidden accept=".vtt" disabled={submitting}
                             onChange={(e) => { updateVideo(v.key, { transcript: e.target.files[0] || null }); e.target.value = ''; }} />
                    </label>
                  </div>
                  <div className="video-actions">
                    <button type="button" className="btn btn-icon" aria-label="Move up" disabled={submitting || i === 0} onClick={() => move(i, -1)}>↑</button>
                    <button type="button" className="btn btn-icon" aria-label="Move down" disabled={submitting || i === videos.length - 1} onClick={() => move(i, 1)}>↓</button>
                    <button type="button" className="btn btn-icon" aria-label="Remove video" disabled={submitting}
                            onClick={() => setVideos((prev) => prev.filter((x) => x.key !== v.key))}>✕</button>
                  </div>
                </li>
              ))}
            </ol>
          )}
        </div>

        {submitting && (
          <div className="course-form-progress">
            <div className="progress" role="progressbar" aria-valuenow={Math.round(progress * 100)} aria-valuemin={0} aria-valuemax={100}>
              <div className="progress-bar" style={{ width: `${progress * 100}%` }} />
              <span className="progress-label">{status}{progress > 0 && progress < 1 ? ` ${Math.round(progress * 100)}%` : ''}</span>
            </div>
            <p className="field-hint">Keep this page open until the uploads finish.</p>
          </div>
        )}

        <div className="form-actions">
          <Link className="btn" to="/admin/courses" aria-disabled={submitting}>Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Working…' : 'Create course'}
          </button>
        </div>
      </form>
    </div>
  );
}
