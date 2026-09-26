import { useState } from 'react';
import Icon from '../Icon';

const MAX_SKILLS = 12;

/** Skills as chips: type one and press Enter or a comma. */
export default function SkillsInput({ skills, onChange }) {
  const [draft, setDraft] = useState('');

  function add(raw) {
    const skill = raw.trim().replace(/,$/, '').trim();
    if (!skill || skills.length >= MAX_SKILLS || skills.some((s) => s.toLowerCase() === skill.toLowerCase())) {
      setDraft('');
      return;
    }
    onChange([...skills, skill]);
    setDraft('');
  }

  function onKeyDown(e) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      add(draft);
    } else if (e.key === 'Backspace' && !draft && skills.length) {
      onChange(skills.slice(0, -1));
    }
  }

  return (
    <div className="chips">
      {skills.map((skill) => (
        <span className="chip" key={skill}>
          {skill}
          <button type="button" aria-label={`Remove ${skill}`} onClick={() => onChange(skills.filter((s) => s !== skill))}>
            <Icon name="x" size={12} />
          </button>
        </span>
      ))}
      {skills.length < MAX_SKILLS && (
        <input type="text" value={draft} maxLength={40} onChange={(e) => setDraft(e.target.value)} onKeyDown={onKeyDown}
               onBlur={() => add(draft)} placeholder={skills.length ? 'Add another' : 'e.g. Java, SQL, React'} aria-label="Add a skill" />
      )}
    </div>
  );
}
