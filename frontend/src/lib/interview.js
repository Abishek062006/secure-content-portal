export const TRACKS = [
  { id: 'STUDENT', label: 'Student or fresher' },
  { id: 'WORKING_PROFESSIONAL', label: 'Working professional' },
];

export const DIFFICULTIES = [
  { id: 'EASY', label: 'Easy' },
  { id: 'MEDIUM', label: 'Medium' },
  { id: 'HARD', label: 'Hard' },
];

export const INTERVIEW_TYPES = [
  { id: 'TECHNICAL', label: 'Technical', hint: 'Questions on the skills and projects on your resume, or the skills you add.' },
  { id: 'HR', label: 'HR', hint: 'Behavioural questions drawn at random: teamwork, pressure, conflict, goals and more.' },
];

export const QUESTION_COUNTS = [3, 4, 5, 6, 7, 8, 9, 10];
export const MAX_SKILLS = 12;

export const READINESS = {
  EXCELLENT: 'Interview ready',
  GOOD: 'Nearly there',
  NEEDS_PRACTICE: 'Needs more practice',
  PENDING: 'In progress',
};

export const CATEGORY = {
  TECHNICAL: 'Technical',
  SYSTEM_DESIGN: 'System design',
  BEHAVIORAL: 'Behavioural',
  PROBLEM_SOLVING: 'Problem solving',
};

export const RUBRIC = [
  { key: 'relevance', label: 'Relevance' },
  { key: 'depth', label: 'Depth' },
  { key: 'structure', label: 'Structure' },
  { key: 'communication', label: 'Communication' },
];

/** The average of each rubric part over the answered questions, on the 1 to 10 scale. */
export function rubricAverages(questions) {
  const answered = questions.filter((q) => q.answeredAt);
  return RUBRIC.map(({ key, label }) => {
    const values = answered.map((q) => q[key]).filter((v) => typeof v === 'number');
    const average = values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
    return { key, label, average };
  });
}

/** What the interview was built from, in a few words. */
export function goalLabel(session) {
  const type = session.interviewType === 'HR' ? 'HR' : 'Technical';
  if (session.source === 'JOB') return `${type} · ${session.targetRole}, from a job description`;
  if (session.source === 'RESUME') return `${type} · ${session.targetRole}, from my resume`;
  if (session.source === 'COURSE') return `${type} · ${session.targetRole}, from a course`;
  return `${type} · ${session.targetRole}`;
}

const shorten = (text, max = 90) => (text.length > max ? `${text.slice(0, max - 1).trimEnd()}…` : text);

/**
 * What the learner did well and what to work on, from the rubric and their own answers. There is always at least one of each when
 * two or more rubric parts were scored, so the report never says "nothing to improve" by accident.
 */
export function strengthsAndGaps(questions) {
  const rubric = rubricAverages(questions).filter((r) => r.average != null).sort((a, b) => b.average - a.average);
  const good = rubric.filter((r) => r.average >= 7);
  if (!good.length && rubric.length) good.push(rubric[0]);
  const weak = rubric.filter((r) => r.average < 6 && !good.includes(r));
  if (!weak.length && rubric.length > 1 && !good.includes(rubric[rubric.length - 1])) weak.push(rubric[rubric.length - 1]);

  const answered = questions.filter((q) => q.answeredAt).sort((a, b) => b.score - a.score);
  const bestAnswers = answered.filter((q) => q.score >= 7 && q.keyStrengths).slice(0, 2);
  const weakAnswers = answered.filter((q) => q.score < 7 && q.areasToImprove).reverse().slice(0, 2);

  return {
    good: [
      ...good.map((r) => ({ key: `r-${r.key}`, title: r.label, note: `${r.average.toFixed(1)} out of 10 on average` })),
      ...bestAnswers.map((q) => ({ key: `q-${q.id}`, title: shorten(q.questionText), note: q.keyStrengths })),
    ],
    work: [
      ...weak.map((r) => ({ key: `r-${r.key}`, title: r.label, note: `${r.average.toFixed(1)} out of 10 on average` })),
      ...weakAnswers.map((q) => ({ key: `q-${q.id}`, title: shorten(q.questionText), note: q.areasToImprove })),
    ],
  };
}

/** Average score per question type, highest first; only worth showing when the interview mixed types. */
export function categoryAverages(questions) {
  const groups = {};
  questions.filter((q) => q.answeredAt).forEach((q) => {
    (groups[q.category] ||= []).push(q.score);
  });
  return Object.entries(groups)
    .map(([category, scores]) => ({ category, label: CATEGORY[category] || category, average: scores.reduce((a, b) => a + b, 0) / scores.length }))
    .sort((a, b) => b.average - a.average);
}
