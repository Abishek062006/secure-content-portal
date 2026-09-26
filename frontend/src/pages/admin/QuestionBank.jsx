import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';
import ConfirmDialog from '../../components/ConfirmDialog';
import QuestionCard from './question-bank/QuestionCard';
import QuestionForm from './question-bank/QuestionForm';

const LEVELS = [
  { key: null, label: 'Mixed', hint: 'A mix of easy, medium and hard.' },
  { key: 'EASY', label: 'Easy', hint: 'All questions test recall of stated facts.' },
  { key: 'MEDIUM', label: 'Medium', hint: 'All questions test understanding.' },
  { key: 'HARD', label: 'Hard', hint: 'All questions test applying ideas.' },
];

const TEMPLATE = 'question,difficulty,option1,option2,option3,option4,correct,explanation\n'
  + '"What does HMAC stand for?",easy,Hash-based Message Authentication Code,Hyper Media Access Control,'
  + 'High-speed Memory Access Cache,Host Machine Address Check,1,"It signs data with a secret key"\n';

/** Where an admin builds a course's question bank: generate with AI, write or import questions, review and approve. */
export default function QuestionBank() {
  const { id } = useParams();
  const [outline, setOutline] = useState(null);
  const [questions, setQuestions] = useState([]);
  const [lessonId, setLessonId] = useState('');
  const [count, setCount] = useState(10);
  const [level, setLevel] = useState(0);
  const [finalOnly, setFinalOnly] = useState(false);
  const [filters, setFilters] = useState({ lesson: '', difficulty: '', status: '', source: '' });
  const [adding, setAdding] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [importErrors, setImportErrors] = useState([]);
  const [pendingDelete, setPendingDelete] = useState(null);

  useEffect(() => {
    Promise.all([api.get(`/api/admin/courses/${id}`), api.get(`/api/admin/courses/${id}/questions`)])
      .then(([course, list]) => {
        setOutline(course);
        setQuestions(list);
        const first = course.modules.flatMap((m) => m.lessons).find((l) => l.hasTranscript) || course.modules[0]?.lessons[0];
        if (first) setLessonId(first.id);
      })
      .catch((err) => setErrorMessage(err.message));
  }, [id]);

  const lessons = useMemo(
    () => (outline ? outline.modules.flatMap((m, mi) => m.lessons.map((l) => ({ ...l, label: `${mi + 1}. ${m.title} › ${l.title}` }))) : []),
    [outline],
  );
  const selected = lessons.find((l) => l.id === lessonId);

  const visible = questions.filter((q) =>
    (!filters.lesson || q.lessonId === filters.lesson)
    && (!filters.difficulty || q.difficulty === filters.difficulty)
    && (!filters.status || q.status === filters.status)
    && (!filters.source || q.source === filters.source));
  const drafts = visible.filter((q) => q.status === 'DRAFT');

  function replace(updated) {
    setQuestions((prev) => prev.map((q) => (q.id === updated.id ? updated : q)));
  }

  function notify(message) {
    setErrorMessage(null);
    setSuccessMessage(message);
  }

  async function run(action) {
    setErrorMessage(null);
    setSuccessMessage(null);
    try {
      await action();
    } catch (err) {
      setErrorMessage(err.message);
    }
  }

  const actions = {
    edit: async (question, values) => {
      const body = { text: values.text, difficulty: values.difficulty, explanation: values.explanation,
        options: values.options, correctIndex: values.correctIndex };
      replace(await api.put(`/api/admin/questions/${question.id}`, body));
      notify('Question updated.');
    },
    setApproved: (question, approve) => run(async () => {
      replace(await api.post(`/api/admin/questions/${question.id}/${approve ? 'approve' : 'unapprove'}`));
    }),
    setScope: (question, toFinal) => run(async () => {
      replace(await api.put(`/api/admin/questions/${question.id}/scope`, { finalOnly: toFinal }));
    }),
    remove: (question) => setPendingDelete(question),
  };

  async function generate() {
    setGenerating(true);
    await run(async () => {
      const created = await api.post(`/api/admin/lessons/${lessonId}/questions/generate`, { count, difficulty: LEVELS[level].key, finalOnly });
      setQuestions((prev) => [...prev, ...created]);
      notify(`${created.length} draft question${created.length === 1 ? '' : 's'} generated — review and approve the ones you want.`);
    });
    setGenerating(false);
  }

  async function importCsv(file) {
    setImportErrors([]);
    await run(async () => {
      const formData = new FormData();
      formData.set('file', file);
      formData.set('finalOnly', finalOnly);
      const result = await api.upload(`/api/admin/lessons/${lessonId}/questions/import`, formData);
      setQuestions(await api.get(`/api/admin/courses/${id}/questions`));
      setImportErrors(result.errors);
      notify(`Imported ${result.imported} question${result.imported === 1 ? '' : 's'}${result.errors.length ? `; ${result.errors.length} row(s) skipped.` : '.'}`);
    });
  }

  async function approveAllDrafts() {
    await run(async () => {
      for (const question of drafts) {
        replace(await api.post(`/api/admin/questions/${question.id}/approve`));
      }
      notify(`Approved ${drafts.length} question${drafts.length === 1 ? '' : 's'}.`);
    });
  }

  async function confirmDelete() {
    const question = pendingDelete;
    setPendingDelete(null);
    await run(async () => {
      await api.del(`/api/admin/questions/${question.id}`);
      setQuestions((prev) => prev.filter((q) => q.id !== question.id));
    });
  }

  if (!outline) {
    return (
      <div className="container">
        <Alert error={errorMessage} />
      </div>
    );
  }

  const templateUrl = `data:text/csv;charset=utf-8,${encodeURIComponent(TEMPLATE)}`;

  return (
    <div className="container-wide">
      <Alert success={successMessage} error={errorMessage} />
      {importErrors.length > 0 && (
        <div className="alert alert-error">
          <strong>Skipped rows</strong>
          <ul className="import-errors">
            {importErrors.map((e) => <li key={e.line}>Line {e.line}: {e.message}</li>)}
          </ul>
        </div>
      )}

      <div className="page-head">
        <div>
          <p className="breadcrumb"><Link to={`/admin/courses/${id}/edit`}>← {outline.course.title}</Link></p>
          <h1>Question bank</h1>
        </div>
        <span className="field-hint">
          {questions.length} question{questions.length === 1 ? '' : 's'} · {questions.filter((q) => q.status === 'APPROVED').length} approved
        </span>
      </div>

      {lessons.length === 0 ? (
        <div className="empty-state"><p>Add a lesson to this course first — questions belong to lessons.</p></div>
      ) : (
        <div className="form-panel qb-toolbar">
          <div className="field">
            <label htmlFor="lesson">Lesson</label>
            <select id="lesson" value={lessonId} onChange={(e) => setLessonId(e.target.value)}>
              {lessons.map((l) => <option key={l.id} value={l.id}>{l.label}{l.hasTranscript ? '' : ' (no transcript)'}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="difficulty">Difficulty of generated questions: <strong>{LEVELS[level].label}</strong></label>
            <input id="difficulty" className="level-slider" type="range" min={0} max={3} step={1} value={level}
                   onChange={(e) => setLevel(Number(e.target.value))} />
            <div className="level-ticks" aria-hidden="true">
              {LEVELS.map((l) => <span key={l.label}>{l.label}</span>)}
            </div>
            <p className="field-hint">
              {LEVELS[level].hint}{level > 0 && ` All ${count} will be ${LEVELS[level].label.toLowerCase()}.`}
            </p>
          </div>
          <div className="field">
            <label htmlFor="count">How many questions: <strong>{count}</strong></label>
            <div className="count-row">
              <input id="count-range" type="range" min={1} max={100} value={count} aria-label="Number of questions"
                     onChange={(e) => setCount(Number(e.target.value))} />
              <input id="count" type="number" min={1} max={100} value={count}
                     onChange={(e) => setCount(Math.min(100, Math.max(1, Number(e.target.value) || 1)))} />
            </div>
          </div>
          <div className="field">
            <label>Use these questions for</label>
            <div className="type-choice">
              <label><input type="radio" name="pool" checked={!finalOnly} onChange={() => setFinalOnly(false)} />
                Module quizzes and assessments <span className="field-hint">(also reusable in the final)</span></label>
              <label><input type="radio" name="pool" checked={finalOnly} onChange={() => setFinalOnly(true)} />
                Final assessment only <span className="field-hint">(new questions learners haven't seen in module quizzes)</span></label>
            </div>
          </div>
          <div className="qb-actions">

            <button type="button" className="btn btn-primary" onClick={generate} disabled={generating || !selected?.hasTranscript}
                    title={selected?.hasTranscript ? '' : 'This lesson needs a transcript (.vtt) first'}>
              {generating ? (count > 20 ? 'Generating… this can take a minute or two' : 'Generating…') : `Generate ${count} with AI`}
            </button>
            <button type="button" className="btn" onClick={() => setAdding(!adding)}>{adding ? 'Close form' : 'Add a question'}</button>
            <label className="btn file-btn">
              Import CSV
              <input type="file" accept=".csv" hidden onChange={(e) => {
                const file = e.target.files[0];
                e.target.value = '';
                if (file) importCsv(file);
              }} />
            </label>
            <a className="btn" href={templateUrl} download="questions-template.csv">CSV template</a>
          </div>
          {!selected?.hasTranscript && (
            <p className="field-hint">AI generation reads the lesson's transcript, so add a .vtt transcript to it in the course editor.</p>
          )}
          {adding && (
            <QuestionForm
              submitLabel="Add question"
              onCancel={() => setAdding(false)}
              onSubmit={async (values) => {
                const created = await api.post(`/api/admin/lessons/${lessonId}/questions`, {
                  text: values.text, difficulty: values.difficulty, explanation: values.explanation,
                  options: values.options, correctIndex: values.correctIndex, finalOnly,
                });
                setQuestions((prev) => [...prev, created]);
                setAdding(false);
                notify('Question added and approved.');
              }}
            />
          )}
        </div>
      )}

      <div className="qb-filters">
        <select aria-label="Filter by lesson" value={filters.lesson} onChange={(e) => setFilters({ ...filters, lesson: e.target.value })}>
          <option value="">All lessons</option>
          {lessons.map((l) => <option key={l.id} value={l.id}>{l.label}</option>)}
        </select>
        <select aria-label="Filter by difficulty" value={filters.difficulty} onChange={(e) => setFilters({ ...filters, difficulty: e.target.value })}>
          <option value="">Any difficulty</option>
          <option value="EASY">Easy</option>
          <option value="MEDIUM">Medium</option>
          <option value="HARD">Hard</option>
        </select>
        <select aria-label="Filter by status" value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
          <option value="">Any status</option>
          <option value="DRAFT">Drafts</option>
          <option value="APPROVED">Approved</option>
        </select>
        <select aria-label="Filter by source" value={filters.source} onChange={(e) => setFilters({ ...filters, source: e.target.value })}>
          <option value="">Any source</option>
          <option value="AI">AI-generated</option>
          <option value="MANUAL">Written by hand</option>
          <option value="IMPORT">Imported</option>
        </select>
        {drafts.length > 0 && (
          <button type="button" className="btn btn-primary" onClick={approveAllDrafts}>Approve {drafts.length} draft{drafts.length === 1 ? '' : 's'}</button>
        )}
      </div>

      {visible.length === 0 && (
        <div className="empty-state"><p>{questions.length === 0 ? 'No questions yet. Generate some with AI, add one, or import a CSV.' : 'No questions match these filters.'}</p></div>
      )}
      {visible.map((question) => <QuestionCard key={question.id} question={question} actions={actions} />)}

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title={pendingDelete ? (pendingDelete.text.length > 70 ? `${pendingDelete.text.slice(0, 70)}…` : pendingDelete.text) : ''}
        detail="The question will be removed from the bank."
        onCancel={() => setPendingDelete(null)}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
