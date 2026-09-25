import { useState } from 'react';

const LETTERS = ['A', 'B', 'C', 'D'];

/** Used both to type in a new question and to edit an existing one. */
export default function QuestionForm({ initial, submitLabel, onSubmit, onCancel }) {
  const [text, setText] = useState(initial?.text || '');
  const [difficulty, setDifficulty] = useState(initial?.difficulty || 'EASY');
  const [explanation, setExplanation] = useState(initial?.explanation || '');
  const [options, setOptions] = useState(initial ? initial.options.map((o) => o.text) : ['', '', '', '']);
  const [correctIndex, setCorrectIndex] = useState(initial ? Math.max(0, initial.options.findIndex((o) => o.correct)) : 0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function submit(e) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await onSubmit({ text, difficulty, explanation, options, correctIndex });
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <form className="inline-form question-form" onSubmit={submit}>
      {error && <p className="field-error">{error}</p>}
      <div className="field">
        <label>Question</label>
        <textarea rows={2} maxLength={1000} value={text} required onChange={(e) => setText(e.target.value)} />
      </div>
      <div className="field">
        <label>Options — select the correct one</label>
        {options.map((option, i) => (
          <div className="option-row" key={LETTERS[i]}>
            <input type="radio" name="correct" aria-label={`Option ${LETTERS[i]} is correct`} checked={correctIndex === i}
                   onChange={() => setCorrectIndex(i)} />
            <input type="text" maxLength={500} required placeholder={`Option ${LETTERS[i]}`} value={option}
                   onChange={(e) => setOptions(options.map((o, j) => (j === i ? e.target.value : o)))} />
          </div>
        ))}
      </div>
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
