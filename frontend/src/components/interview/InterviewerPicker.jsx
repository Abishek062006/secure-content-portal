import { useState } from 'react';
import Icon from '../Icon';
import InterviewerStage from './InterviewerStage';
import { PERSONAS, personaById, pick } from './avatar/personas';

/** Choose who interviews you: a live preview of the person, and a short hello in their own voice. */
export default function InterviewerPicker({ value, onChange }) {
  const [line, setLine] = useState(null);
  const persona = personaById(value);

  return (
    <div className="interviewer-picker">
      <div className="interviewer-preview">
        <InterviewerStage personaId={value} line={line} compact />
        <button type="button" className="btn btn-sm" onClick={() => setLine({ id: Date.now(), text: pick(persona.lines.hello) })}>
          <Icon name="volume-2" size={15} /> Hear {persona.name}
        </button>
      </div>
      <div className="interviewer-options" role="radiogroup" aria-label="Interviewer">
        {PERSONAS.map((p) => (
          <label key={p.id} className={p.id === value ? 'selected' : ''}>
            <input type="radio" name="interviewer" value={p.id} checked={p.id === value}
                   onChange={() => {
                     setLine(null);
                     onChange(p.id);
                   }} />
            <span className="interviewer-option-name">{p.name}</span>
            <span className="field-hint">{p.role}. {p.style}.</span>
          </label>
        ))}
      </div>
    </div>
  );
}
