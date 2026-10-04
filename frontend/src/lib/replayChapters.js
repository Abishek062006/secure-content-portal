/**
 * Chapters for the replay, from the markers saved while recording: one per question asked, running from when it was asked until
 * the next one begins (or the recording ends). `answeredMs` is when the learner submitted their answer, when they did.
 */
export function buildChapters(markers, durationMs) {
  const asked = (markers || []).filter((m) => m.type === 'question');
  return asked.map((m, i) => {
    const endMs = i + 1 < asked.length ? asked[i + 1].offsetMs : Math.max(durationMs || 0, m.offsetMs);
    const answer = markers.find((a) => a.type === 'answer' && a.questionId === m.questionId && a.offsetMs >= m.offsetMs);
    return { questionId: m.questionId, text: m.text || '', startMs: m.offsetMs, endMs, answeredMs: answer ? answer.offsetMs : null };
  });
}

/** The chapter playing at `ms`: the latest one that has begun. Null before the first question. */
export function chapterAt(chapters, ms) {
  let current = null;
  for (const chapter of chapters) {
    if (chapter.startMs <= ms) current = chapter;
  }
  return current;
}
