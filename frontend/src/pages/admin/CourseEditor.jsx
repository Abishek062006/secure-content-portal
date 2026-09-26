import { useCallback, useEffect, useState } from 'react';
import CoverPicker from '../../components/CoverPicker';
import PricingFields, { pricingFromCourse, pricingPayload } from '../../components/PricingFields';
import { Link, useLocation, useParams } from 'react-router-dom';
import { API_BASE, api } from '../../api';
import Alert from '../../components/Alert';
import ConfirmDialog from '../../components/ConfirmDialog';
import ModuleCard from './course-editor/ModuleCard';
import AssessmentPanel from './course-editor/AssessmentPanel';

/** Builds a course: its details, cover, publish state, and the outline of modules and lessons. */
export default function CourseEditor() {
  const { id } = useParams();
  const location = useLocation();
  const [outline, setOutline] = useState(null);
  const [form, setForm] = useState({ title: '', description: '', category: '' });
  const [errorMessage, setErrorMessage] = useState(location.state?.error || null);
  const [successMessage, setSuccessMessage] = useState(location.state?.success || null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [newModuleTitle, setNewModuleTitle] = useState('');
  const [sectioned, setSectioned] = useState(false);
  const [pricing, setPricing] = useState(null);

  const [questions, setQuestions] = useState([]);

  const load = useCallback(async () => {
    const [res, bank] = await Promise.all([api.get(`/api/admin/courses/${id}`), api.get(`/api/admin/courses/${id}/questions`)]);
    setOutline(res);
    setQuestions(bank);
    return res;
  }, [id]);

  useEffect(() => {
    load()
      .then((res) => {
        setForm({ title: res.course.title, description: res.course.description || '', category: res.course.category || '' });
        setPricing(pricingFromCourse(res.course));
      })
      .catch((err) => setErrorMessage(err.message));
  }, [load]);

  /** Runs one action, then reloads the outline; failures surface as an error banner. */
  const run = useCallback(async (action, success) => {
    setErrorMessage(null);
    setSuccessMessage(null);
    try {
      await action();
      await load();
      if (success) setSuccessMessage(success);
    } catch (err) {
      setErrorMessage(err.message);
      throw err;
    }
  }, [load]);

  const swallow = (promise) => promise.catch(() => {});

  if (!outline) {
    return (
      <div className="container">
        <Alert error={errorMessage} />
      </div>
    );
  }

  const { course, modules } = outline;
  const published = course.status === 'PUBLISHED';
  // Most courses are just a video or a few: hide the section concept until the admin asks for it.
  const flat = modules.length === 1 && !sectioned;

  /** Approved questions by difficulty across the given lessons: what an attempt could be drawn from. */
  function availability(lessonIds, finalOnly = false) {
    const counts = { EASY: 0, MEDIUM: 0, HARD: 0 };
    questions
      .filter((q) => q.status === 'APPROVED' && lessonIds.has(q.lessonId) && q.finalOnly === finalOnly)
      .forEach((q) => { counts[q.difficulty] += 1; });
    return counts;
  }

  function reorder(ids, from, delta) {
    const to = from + delta;
    if (to < 0 || to >= ids.length) return null;
    const next = [...ids];
    [next[from], next[to]] = [next[to], next[from]];
    return next;
  }

  const moduleActions = {
    editModule: (moduleId, body) => run(() => api.put(`/api/admin/modules/${moduleId}`, body), 'Module updated.'),
    deleteModule: (module) => setPendingDelete({ type: 'module', item: module }),
    moveModule: (moduleId, delta) => {
      const ids = modules.map((m) => m.id);
      const next = reorder(ids, ids.indexOf(moduleId), delta);
      if (next) swallow(run(() => api.put(`/api/admin/courses/${id}/modules/order`, { ids: next })));
    },
    addLesson: (moduleId, formData, onProgress) =>
      run(() => api.uploadWithProgress(`/api/admin/modules/${moduleId}/lessons`, formData, onProgress), 'Lesson added.'),
    editLesson: (lessonId, body) => run(() => api.put(`/api/admin/lessons/${lessonId}`, body), 'Lesson updated.'),
    replaceTranscript: (lessonId, file) => {
      const formData = new FormData();
      formData.set('file', file);
      swallow(run(() => api.upload(`/api/admin/lessons/${lessonId}/transcript`, formData), 'Transcript replaced.'));
    },
    deleteLesson: (lesson) => setPendingDelete({ type: 'lesson', item: lesson }),
    addMaterial: (moduleId, formData, onProgress) =>
      run(() => api.uploadWithProgress(`/api/admin/modules/${moduleId}/materials`, formData, onProgress), 'Material added.'),
    updateMaterial: (material, changes) => swallow(run(() => api.put(`/api/admin/materials/${material.id}`, {
      title: material.title, description: material.description, url: material.url, downloadable: material.downloadable, ...changes,
    }), 'Material updated.')),
    deleteMaterial: (material) => setPendingDelete({ type: 'material', item: material }),
    saveAssessment: (moduleId, body) => run(() => api.put(`/api/admin/modules/${moduleId}/assessment`, body), 'Saved.'),
    removeAssessment: (module) => setPendingDelete({ type: 'assessment', item: { ...module.assessment, kind: 'module', moduleId: module.id } }),
    moveLesson: (module, lessonId, delta) => {
      const ids = module.lessons.map((l) => l.id);
      const next = reorder(ids, ids.indexOf(lessonId), delta);
      if (next) swallow(run(() => api.put(`/api/admin/modules/${module.id}/lessons/order`, { ids: next })));
    },
  };

  async function confirmDelete() {
    const { type, item } = pendingDelete;
    setPendingDelete(null);
    let path;
    if (type === 'assessment') {
      path = item.kind === 'final' ? `/api/admin/courses/${id}/final-assessment` : `/api/admin/modules/${item.moduleId}/assessment`;
    } else {
      path = type === 'module' ? `/api/admin/modules/${item.id}` : type === 'material' ? `/api/admin/materials/${item.id}` : `/api/admin/lessons/${item.id}`;
    }
    const label = { module: 'Module', lesson: 'Lesson', material: 'Material', assessment: 'Quiz/assessment' }[type];
    swallow(run(() => api.del(path), `${label} deleted.`));
  }

  return (
    <div className="container">
      <Alert success={successMessage} error={errorMessage} />

      <div className="page-head">
        <h1>{course.title}</h1>
        <div className="row-actions">
          <span className={`badge status-${course.status.toLowerCase()}`}>{published ? 'Published' : 'Draft'}</span>
          <Link className="btn" to={`/admin/courses/${id}/questions`}>Question bank</Link>
          <Link className="btn" to={`/admin/courses/${id}/results`}>Results</Link>
          <Link className="btn" to={`/courses/${id}`}>Preview</Link>
          {published && <Link className="btn" to={`/feed?promote=${id}`}>Promote this course</Link>}
          <button type="button" className="btn btn-primary"
                  onClick={() => swallow(run(() => api.post(`/api/admin/courses/${id}/${published ? 'unpublish' : 'publish'}`),
                    published ? 'Course unpublished — learners can no longer see it.' : 'Course published — learners can now enroll.'))}>
            {published ? 'Unpublish' : 'Publish'}
          </button>
        </div>
      </div>

      <form className="form-panel" onSubmit={(e) => {
        e.preventDefault();
        swallow(run(() => api.put(`/api/admin/courses/${id}`, form), 'Details saved.'));
      }}>
        <h2>Details</h2>
        <div className="field">
          <label htmlFor="title">Title</label>
          <input id="title" type="text" maxLength={200} value={form.title} required
                 onChange={(e) => setForm({ ...form, title: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="description">Description</label>
          <textarea id="description" maxLength={2000} rows={3} value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="category">Category / tag</label>
          <input id="category" type="text" maxLength={80} value={form.category}
                 onChange={(e) => setForm({ ...form, category: e.target.value })} />
        </div>
        <div className="field">
          <label>Cover image</label>
          <CoverPicker
            src={course.thumbnailUrl ? `${API_BASE}${course.thumbnailUrl}` : null}
            onPick={(file) => {
              const formData = new FormData();
              formData.set('file', file);
              swallow(run(() => api.upload(`/api/admin/courses/${id}/thumbnail`, formData), 'Cover image replaced.'));
            }}
          />
        </div>
        <div className="form-actions">
          <button type="submit" className="btn btn-primary">Save details</button>
        </div>
      </form>

      {pricing && (
        <form className="form-panel pricing-panel" onSubmit={(e) => {
          e.preventDefault();
          swallow(run(() => api.put(`/api/admin/courses/${id}/pricing`, pricingPayload(pricing)), 'Price saved.'));
        }}>
          <PricingFields value={pricing} onChange={setPricing} />
          <div className="form-actions"><button type="submit" className="btn btn-primary">Save price</button></div>
        </form>
      )}

      <h2 className="editor-heading">{flat ? 'Videos and quiz' : 'Outline'}</h2>
      {modules.length === 0 && (
        <div className="empty-state"><p>No modules yet. Add the first one below.</p></div>
      )}
      {modules.map((module, index) => (
        <ModuleCard key={module.id} module={module} index={index} total={modules.length} actions={moduleActions} flat={flat}
                    available={availability(new Set(module.lessons.map((l) => l.id)))} />
      ))}

      {flat ? (
        <p className="sections-hint">
          Need chapters or a quiz per chapter?{' '}
          <button type="button" className="link-button" onClick={() => setSectioned(true)}>Organize into sections</button>
        </p>
      ) : (
      <form className="module-add" onSubmit={(e) => {
          e.preventDefault();
          swallow(run(() => api.post(`/api/admin/courses/${id}/modules`, { title: newModuleTitle }), 'Module added.')
            .then(() => setNewModuleTitle('')));
        }}>
          <input type="text" placeholder="New module title" maxLength={200} required value={newModuleTitle}
                 onChange={(e) => setNewModuleTitle(e.target.value)} />
          <button type="submit" className="btn btn-primary">Add module</button>
        </form>
      )}

      <h2 className="editor-heading">Final assessment{flat ? ' (optional)' : ''}</h2>
      <section className="module-card">
        <AssessmentPanel
          isFinal
          assessment={outline.finalAssessment}
          available={availability(new Set(modules.flatMap((m) => m.lessons.map((l) => l.id))))}
          availableNew={availability(new Set(modules.flatMap((m) => m.lessons.map((l) => l.id))), true)}
          questionsPath={`/admin/courses/${id}/questions`}
          onSave={(values) => run(() => api.put(`/api/admin/courses/${id}/final-assessment`, values), 'Final assessment saved.')}
          onRemove={() => setPendingDelete({ type: 'assessment', item: { ...outline.finalAssessment, kind: 'final' } })}
        />
      </section>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title={pendingDelete?.item.title}
        detail={{
          module: 'All its lessons, videos and materials will be removed too.',
          lesson: 'Its video and transcript will be removed too.',
          material: 'Its file will be removed too.',
          assessment: 'Learners\' attempts at it will be removed too.',
        }[pendingDelete?.type]}
        onCancel={() => setPendingDelete(null)}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
