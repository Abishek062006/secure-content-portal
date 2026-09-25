import { useState } from 'react';
import QuestionForm from './QuestionForm';

const SOURCE_LABEL = { AI: 'AI-generated', MANUAL: 'Written by hand', IMPORT: 'Imported' };
const LETTERS = ['A', 'B', 'C', 'D'];

function clock(seconds) {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

export default function QuestionCard({ question, actions }) {
  const [editing, setEditing] = useState(false);

  if (editing) {
    return (
      <article className="question-card">
        <QuestionForm
          initial={question}
          submitLabel="Save changes"
          onCancel={() => setEditing(false)}
          onSubmit={async (values) => {
            await actions.edit(question, values);
            setEditing(false);
          }}
        />
      </article>
    );
  }

  const approved = question.status === 'APPROVED';
  return (
    <article className="question-card">
      <header className="question-card-head">
        <div className="question-badges">
          <span className={`badge difficulty-${question.difficulty.toLowerCase()}`}>{question.difficulty}</span>
          <span className={`badge status-${approved ? 'published' : 'draft'}`}>{approved ? 'Approved' : 'Draft'}</span>
          <span className="badge">{SOURCE_LABEL[question.source]}</span>
        </div>
        <span className="field-hint">
          {question.lessonTitle}{question.sourceSeconds != null ? ` · at ${clock(question.sourceSeconds)}` : ''}
        </span>
      </header>

      <p className="question-text">{question.text}</p>
      <ol className="question-options">
        {question.options.map((option, i) => (
          <li key={LETTERS[i]} className={option.correct ? 'correct' : ''}>
            <span className="option-letter">{LETTERS[i]}</span>
            {option.text}
            {option.correct && <span className="option-tick" aria-label="correct answer">✓</span>}
          </li>
        ))}
      </ol>
      {question.explanation && <p className="field-hint">Why: {question.explanation}</p>}

      <div className="row-actions">
        <button type="button" className={approved ? 'btn' : 'btn btn-primary'} onClick={() => actions.setApproved(question, !approved)}>
          {approved ? 'Move to drafts' : 'Approve'}
        </button>
        <button type="button" className="btn" onClick={() => setEditing(true)}>Edit</button>
        <button type="button" className="btn btn-danger-outline" onClick={() => actions.remove(question)}>Delete</button>
      </div>
    </article>
  );
}
