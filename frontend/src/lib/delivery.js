/**
 * Turning the speech timing measured during an interview into things a learner can read: a summary, a plain note for each
 * measure, and a line for each answer. The ranges are starting guesses to tune with real recordings, and every note describes what
 * was measured rather than judging the person. None of this counts towards the score.
 */

const sum = (list) => list.reduce((total, value) => total + value, 0);

/** 6 -> "6 s", 65 -> "1 min 5 s". */
export function formatSeconds(seconds) {
  const total = Math.round(seconds);
  return total < 60 ? `${total} s` : `${Math.floor(total / 60)} min ${total % 60} s`;
}

/** What the delivery of a whole interview comes to, or null when no answer has any. */
export function deliverySummary(questions) {
  const measured = questions.map((q, i) => ({ q, number: i + 1 })).filter(({ q }) => q.delivery);
  if (measured.length === 0) return null;

  const spoken = measured.filter(({ q }) => q.delivery.mode === 'VOICE');
  const clear = spoken.filter(({ q }) => q.delivery.audioClear);
  const paced = clear.filter(({ q }) => q.delivery.wordsPerMinute != null && q.delivery.speakingSeconds > 0);
  const speakingTotal = sum(paced.map(({ q }) => q.delivery.speakingSeconds));
  // A long answer says more about someone's pace than a short one, so the average is weighted by speaking time.
  const pace = speakingTotal > 0 ? Math.round(sum(paced.map(({ q }) => q.delivery.wordsPerMinute * q.delivery.speakingSeconds)) / speakingTotal) : null;

  const thinking = measured.map(({ q }) => q.delivery.thinkingSeconds).filter((v) => v != null);
  const withPauses = clear.filter(({ q }) => q.delivery.longPauses != null);
  const longest = withPauses.reduce((best, { q, number }) => (q.delivery.longestPauseSeconds > (best?.seconds ?? 0)
    ? { seconds: q.delivery.longestPauseSeconds, number } : best), null);

  return {
    answers: measured.length,
    spoken: spoken.length,
    typed: measured.length - spoken.length,
    unclear: spoken.length - clear.length,
    pace,
    thinking: thinking.length ? Math.round(sum(thinking) / thinking.length) : null,
    longPauses: withPauses.length ? sum(withPauses.map(({ q }) => q.delivery.longPauses)) : null,
    longPausesPerAnswer: withPauses.length ? sum(withPauses.map(({ q }) => q.delivery.longPauses)) / withPauses.length : null,
    longest: longest && longest.seconds >= 1.5 ? longest : null,
  };
}

/** Comfortable speaking pace is about 120 to 170 words a minute while speaking. */
function paceNote(pace) {
  if (pace < 100) return { status: 'watch', label: 'Slow', note: 'Your pace was on the slow side. A steadier flow keeps the listener with you.' };
  if (pace < 120) return { status: 'watch', label: 'A little slow', note: 'A touch slower than everyday conversation. Fine for a hard point; pick up the pace on the easy ones.' };
  if (pace <= 170) return { status: 'good', label: 'Comfortable', note: 'A natural pace for an interview.' };
  if (pace <= 190) return { status: 'watch', label: 'A little fast', note: 'Slightly quick. Pause between ideas so each one lands.' };
  return { status: 'watch', label: 'Fast', note: 'Quite fast. Slow down so the interviewer can follow every point.' };
}

function thinkingNote(seconds) {
  if (seconds <= 1.5) return { status: 'good', label: 'Started right away', note: 'You began almost at once. For a harder question, a moment to organise your thoughts is fine.' };
  if (seconds <= 12) return { status: 'good', label: 'Natural', note: 'You took a short moment to think before starting, which sounds natural.' };
  return { status: 'watch', label: seconds <= 25 ? 'A bit long' : 'Long', note: 'You often took a while to start. Try opening with one line that answers the question, then add detail.' };
}

function pausesNote(perAnswer) {
  if (perAnswer === 0) return { status: 'good', label: 'None', note: 'No long pauses. Your answers flowed.' };
  if (perAnswer <= 1) return { status: 'good', label: 'A few', note: 'About one long pause an answer or fewer, which is normal while you think.' };
  return { status: 'watch', label: 'Several', note: 'More than one long pause an answer. A short phrase such as "let me think about that" bridges a gap.' };
}

/** The measures to show, each with its value and a plain note. Only the ones that were actually measured. */
export function deliveryMeasures(summary) {
  const measures = [];
  if (summary.pace != null) measures.push({ key: 'pace', title: 'Speaking pace', value: `${summary.pace} words a minute`, ...paceNote(summary.pace) });
  if (summary.thinking != null) measures.push({ key: 'thinking', title: 'Time before you started', value: formatSeconds(summary.thinking), ...thinkingNote(summary.thinking) });
  if (summary.longPausesPerAnswer != null) {
    const per = Math.round(summary.longPausesPerAnswer * 10) / 10;
    measures.push({
      key: 'pauses', title: 'Long pauses', value: `${summary.longPauses} in total, ${per} an answer`, ...pausesNote(summary.longPausesPerAnswer),
      extra: summary.longest ? `Longest: ${formatSeconds(summary.longest.seconds)}, on question ${summary.longest.number}.` : null,
    });
  }
  return measures;
}

/** How one answer was given, in a line: "Spoken · 148 words a minute · thought for 6 s · 2 long pauses". Null when nothing was measured. */
export function answerDeliveryLine(question) {
  const d = question?.delivery;
  if (!d) return null;
  const parts = [d.mode === 'VOICE' ? 'Spoken' : 'Typed'];
  if (d.mode === 'VOICE') {
    if (!d.audioClear) parts.push('too noisy to measure pace and pauses');
    else {
      parts.push(d.wordsPerMinute != null ? `${d.wordsPerMinute} words a minute` : 'pace not measured');
    }
  }
  if (d.thinkingSeconds != null) parts.push(`thought for ${formatSeconds(d.thinkingSeconds)}`);
  if (d.mode === 'VOICE' && d.audioClear && d.longPauses != null) parts.push(`${d.longPauses} long pause${d.longPauses === 1 ? '' : 's'}`);
  return parts.join(' · ');
}

/**
 * How the current interview compares with the learner's earlier ones, for the measures that exist in both: each earlier value in
 * order, then this one. Empty when there is nothing earlier to compare with.
 */
export function deliveryTrends(summary, trend, sessionId) {
  const earlier = (trend || []).filter((p) => p.sessionId !== sessionId);
  const rows = [];
  const add = (key, title, unit, current, pick, format = (v) => String(v)) => {
    const values = earlier.map(pick).filter((v) => v != null);
    if (current != null && values.length > 0) rows.push({ key, title, unit, values: [...values, current].map(format) });
  };
  add('pace', 'Pace', 'words a minute', summary.pace, (p) => p.wordsPerMinute);
  add('thinking', 'Time before you started', 'seconds', summary.thinking, (p) => p.thinkingSeconds);
  add('pauses', 'Long pauses an answer', '', summary.longPausesPerAnswer == null ? null : Math.round(summary.longPausesPerAnswer * 10) / 10, (p) => p.longPausesPerAnswer);
  return rows;
}
