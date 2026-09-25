import { useState } from 'react';
import AssessmentPanel from './AssessmentPanel';

const MAX_VIDEO_BYTES = 5 * 1024 ** 3;

function MoveButtons({ index, total, onMove, label }) {
  return (
    <span className="move-buttons">
      <button type="button" className="btn btn-icon" aria-label={`Move ${label} up`} disabled={index === 0} onClick={() => onMove(-1)}>↑</button>
      <button type="button" className="btn btn-icon" aria-label={`Move ${label} down`} disabled={index === total - 1} onClick={() => onMove(1)}>↓</button>
    </span>
  );
}

function AddLessonForm({ onAdd, onCancel }) {
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState(null);

  async function submit(e) {
    e.preventDefault();
    setError(null);
    const form = e.currentTarget;
    const video = form.video.files[0];
    if (!/\.(mp4|webm)$/i.test(video.name)) {
      setError('The lesson video must be an .mp4 or .webm file.');
      return;
    }
    if (video.size > MAX_VIDEO_BYTES) {
      setError('That video is over the 5 GB limit.');
      return;
    }
    const formData = new FormData();
    formData.set('title', form.title.value);
    formData.set('description', form.description.value);
    formData.set('video', video);
    if (form.transcript.files[0]) formData.set('transcript', form.transcript.files[0]);

    setBusy(true);
    setProgress(0);
    try {
      await onAdd(formData, setProgress);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <form className="inline-form" onSubmit={submit}>
      {error && <p className="field-error">{error}</p>}
      <div className="field">
        <label>Lesson title</label>
        <input name="title" type="text" maxLength={200} required disabled={busy} />
      </div>
      <div className="field">
        <label>Description (optional)</label>
        <textarea name="description" rows={2} maxLength={2000} disabled={busy} />
      </div>
      <div className="field">
        <label>Video (.mp4 or .webm, up to 5 GB)</label>
        <input name="video" type="file" accept=".mp4,.webm" required disabled={busy} />
      </div>
      <div className="field">
        <label>Transcript (.vtt file, e.g. the one Zoom saves with the recording)</label>
        <input name="transcript" type="file" accept=".vtt" disabled={busy} />
        <p className="field-hint">Optional, but quiz and assessment questions are generated from transcripts, so lessons without one can't get AI questions.</p>
      </div>
      {busy && (
        <div className="progress" role="progressbar" aria-valuenow={Math.round(progress * 100)} aria-valuemin={0} aria-valuemax={100}>
          <div className="progress-bar" style={{ width: `${progress * 100}%` }} />
          <span className="progress-label">{progress < 1 ? `Uploading… ${Math.round(progress * 100)}%` : 'Saving…'}</span>
        </div>
      )}
      <div className="form-actions">
        <button type="button" className="btn" onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="submit" className="btn btn-primary" disabled={busy}>Add lesson</button>
      </div>
    </form>
  );
}

function LessonRow({ lesson, index, total, actions }) {
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ title: lesson.title, description: lesson.description || '' });

  async function save(e) {
    e.preventDefault();
    await actions.editLesson(lesson.id, form);
    setEditing(false);
  }

  return (
    <li className="lesson-row">
      {editing ? (
        <form className="inline-form" onSubmit={save}>
          <input type="text" maxLength={200} value={form.title} required
                 onChange={(e) => setForm({ ...form, title: e.target.value })} />
          <textarea rows={2} maxLength={2000} value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })} />
          <div className="form-actions">
            <button type="button" className="btn" onClick={() => setEditing(false)}>Cancel</button>
            <button type="submit" className="btn btn-primary">Save</button>
          </div>
        </form>
      ) : (
        <>
          <div className="lesson-row-main">
            <strong>{lesson.title}</strong>
            <span className="field-hint">
              {lesson.videoFilename} · {lesson.videoSizeLabel}
              {lesson.hasTranscript ? ` · transcript: ${lesson.transcriptFilename}` : ' · no transcript'}
            </span>
          </div>
          <div className="row-actions">
            <label className="btn file-btn">
              {lesson.hasTranscript ? 'Replace transcript' : 'Add transcript'}
              <input type="file" accept=".vtt" hidden onChange={(e) => {
                const file = e.target.files[0];
                e.target.value = '';
                if (file) actions.replaceTranscript(lesson.id, file);
              }} />
            </label>
            <button type="button" className="btn" onClick={() => setEditing(true)}>Edit</button>
            <MoveButtons index={index} total={total} label={`lesson ${lesson.title}`} onMove={(d) => actions.moveLesson(lesson.id, d)} />
            <button type="button" className="btn btn-danger-outline" onClick={() => actions.deleteLesson(lesson)}>Delete</button>
          </div>
        </>
      )}
    </li>
  );
}

export default function ModuleCard({ module, index, total, actions, available }) {
  const [editing, setEditing] = useState(false);
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ title: module.title, description: module.description || '' });

  async function save(e) {
    e.preventDefault();
    await actions.editModule(module.id, form);
    setEditing(false);
  }

  return (
    <section className="module-card">
      <header className="module-card-head">
        {editing ? (
          <form className="inline-form" onSubmit={save}>
            <input type="text" maxLength={200} value={form.title} required
                   onChange={(e) => setForm({ ...form, title: e.target.value })} />
            <textarea rows={2} maxLength={2000} value={form.description} placeholder="What this module covers (optional)"
                      onChange={(e) => setForm({ ...form, description: e.target.value })} />
            <div className="form-actions">
              <button type="button" className="btn" onClick={() => setEditing(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary">Save</button>
            </div>
          </form>
        ) : (
          <>
            <div>
              <h3>Module {index + 1}: {module.title}</h3>
              {module.description && <p className="field-hint">{module.description}</p>}
            </div>
            <div className="row-actions">
              <button type="button" className="btn" onClick={() => setEditing(true)}>Rename</button>
              <MoveButtons index={index} total={total} label={`module ${module.title}`} onMove={(d) => actions.moveModule(module.id, d)} />
              <button type="button" className="btn btn-danger-outline" onClick={() => actions.deleteModule(module)}>Delete</button>
            </div>
          </>
        )}
      </header>

      {module.lessons.length === 0 && !adding && <p className="field-hint">No lessons in this module yet.</p>}
      <ol className="lesson-rows">
        {module.lessons.map((lesson, lIndex) => (
          <LessonRow
            key={lesson.id}
            lesson={lesson}
            index={lIndex}
            total={module.lessons.length}
            actions={{
              editLesson: actions.editLesson,
              replaceTranscript: actions.replaceTranscript,
              deleteLesson: actions.deleteLesson,
              moveLesson: (lessonId, delta) => actions.moveLesson(module, lessonId, delta),
            }}
          />
        ))}
      </ol>

      <AssessmentPanel
        assessment={module.assessment}
        available={available}
        allowGate
        onSave={(values) => actions.saveAssessment(module.id, values)}
        onRemove={() => actions.removeAssessment(module)}
      />

      {adding ? (
        <AddLessonForm
          onCancel={() => setAdding(false)}
          onAdd={async (formData, onProgress) => {
            await actions.addLesson(module.id, formData, onProgress);
            setAdding(false);
          }}
        />
      ) : (
        <button type="button" className={`btn${module.lessons.length === 0 ? ' btn-primary' : ''}`} onClick={() => setAdding(true)}>
          + Add lesson (video and transcript)
        </button>
      )}
    </section>
  );
}
