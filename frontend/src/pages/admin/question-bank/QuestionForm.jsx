import { useState } from 'react';

const LETTERS = ['A', 'B', 'C', 'D'];

const TYPES = [
  { id: 'MULTIPLE_CHOICE', label: 'Multiple choice (concept question)' },
  { id: 'FILL_CODE', label: 'Fill in the code' },
  { id: 'PREDICT_OUTPUT', label: 'Predict the output' },
];

/** Used both to type in a new question and to edit an existing one. */
export default function QuestionForm({ initial, submitLabel, onSubmit, onCancel }) {
  const [type, setType] = useState(initial?.type || 'MULTIPLE_CHOICE');
  const [text, setText] = useState(initial?.text || '');
  const [codeSnippet, setCodeSnippet] = useState(initial?.codeSnippet || '');
  // For the coding types: have learners type the answer (the default for new ones) or pick it from the options.
  const [typed, setTyped] = useState(initial ? initial.type !== 'MULTIPLE_CHOICE' && !initial.showOptions : true);
  const [accepted, setAccepted] = useState((initial?.acceptedAnswers || []).join('\n'));
  const [difficulty, setDifficulty] = useState(initial?.difficulty || 'EASY');
  const [explanation, setExplanation] = useState(initial?.explanation || '');
  const [options, setOptions] = useState(initial ? initial.options.map((o) => o.text) : ['', '', '', '']);
  const [correctIndex, setCorrectIndex] = useState(initial ? Math.max(0, initial.options.findIndex((o) => o.correct)) : 0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const coding = type !== 'MULTIPLE_CHOICE';
  const fill = type === 'FILL_CODE';

  async function submit(e) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await onSubmit({
        text, difficulty, explanation, options, correctIndex, type,
        codeSnippet: coding ? codeSnippet : null,
        showOptions: !(coding && typed),
        acceptedAnswers: coding ? accepted.split('\n').map((a) => a.trim()).filter(Boolean) : [],
      });
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <form className="inline-form question-form" onSubmit={submit}>
      {error && <p className="field-error">{error}</p>}
      <div className="field">
        <label htmlFor="question-type">Question type</label>
        <select id="question-type" value={type} onChange={(e) => setType(e.target.value)}>
          {TYPES.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
        </select>
      </div>
      <div className="field">
        <label>Question</label>
        <textarea rows={2} maxLength={1000} value={text} required onChange={(e) => setText(e.target.value)} />
      </div>
      {coding && (
        <>
          <div className="field">
            <label htmlFor="question-code">Code</label>
            <textarea id="question-code" className="code-input" rows={6} maxLength={4000} value={codeSnippet} required
                      spellCheck={false} onChange={(e) => setCodeSnippet(e.target.value)} />
            <p className="field-hint">
              {fill
                ? 'Mark the missing code with ____ (four underscores). Use exactly one blank.'
                : 'Learners are asked what this code prints or returns.'}
            </p>
          </div>
          <div className="field">
            <label>How learners answer</label>
            <div className="type-choice">
              <label>
                <input type="radio" name="answer-mode" checked={typed} onChange={() => setTyped(true)} />
                They type the answer <span className="field-hint">(options stay hidden)</span>
              </label>
              <label>
                <input type="radio" name="answer-mode" checked={!typed} onChange={() => setTyped(false)} />
                They pick from the options
              </label>
            </div>
          </div>
        </>
      )}
      <div className="field">
        <label>Options — select the correct one</label>
        {coding && typed && (
          <p className="field-hint">The correct option is the answer a typed response is checked against. The others are only shown if you switch options on later.</p>
        )}
        {options.map((option, i) => (
          <div className="option-row" key={LETTERS[i]}>
            <input type="radio" name="correct" aria-label={`Option ${LETTERS[i]} is correct`} checked={correctIndex === i}
                   onChange={() => setCorrectIndex(i)} />
            <input type="text" maxLength={500} required placeholder={`Option ${LETTERS[i]}`} value={option}
                   onChange={(e) => setOptions(options.map((o, j) => (j === i ? e.target.value : o)))} />
          </div>
        ))}
      </div>
      {coding && (
        <div className="field">
          <label htmlFor="question-accepted">Other answers to accept (optional)</label>
          <textarea id="question-accepted" className="code-input" rows={2} maxLength={2000} value={accepted}
                    spellCheck={false} onChange={(e) => setAccepted(e.target.value)} />
          <p className="field-hint">One per line. Extra spaces and quote style don't matter{fill ? ', but capital letters do' : ''}.</p>
        </div>
      )}
      <div className="field">
        <label>Difficulty</label>
        <select value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
          <option value="EASY">Easy</option>
          <option value="MEDIUM">Medium</option>
          <option value="HARD">Hard</option>
        </select>
      </div>
      <div className="field">
        <label>Explanation (optional)</label>
        <textarea rows={2} maxLength={1000} value={explanation} onChange={(e) => setExplanation(e.target.value)} />
      </div>
      <div className="form-actions">
        <button type="button" className="btn" onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : submitLabel}</button>
      </div>
    </form>
  );
}
