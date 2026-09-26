export const TRACKS = [
  { id: 'STUDENT', label: 'Student or fresher' },
  { id: 'WORKING_PROFESSIONAL', label: 'Working professional' },
];

export const DIFFICULTIES = [
  { id: 'EASY', label: 'Easy' },
  { id: 'MEDIUM', label: 'Medium' },
  { id: 'HARD', label: 'Hard' },
];

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
  if (session.source === 'JOB') return `${session.targetRole}, from a job description`;
  if (session.source === 'COURSE') return `${session.targetRole}, from a course`;
  return session.targetRole;
}
