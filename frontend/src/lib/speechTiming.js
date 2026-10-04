/**
 * Speech timing from the microphone's loudness alone: when the learner is speaking, when they pause, and how long they took to
 * begin. The words come from the transcript; this only listens for the gaps between them. Nothing is recorded or sent from here.
 *
 * It is honest about its limits: if the room is too noisy, the voice too faint, or there is too little speech to judge, it says the
 * audio wasn't clear enough and reports no numbers rather than guessing.
 */

/** A silence at least this long inside an answer counts as a long pause. */
export const LONG_PAUSE_MS = 1500;
/** Gaps shorter than this are just the breath between words and don't split speech. */
const BRIDGE_MS = 300;
/** A burst of sound shorter than this is a click or a bump, not speech. */
const MIN_SPEECH_MS = 100;
/** Below this much speech in total there is nothing to measure. */
const MIN_TOTAL_SPEECH_MS = 3000;
/** Speech has to stand out from the room by at least this many dB. */
const MIN_RANGE_DB = 12;
/** A room louder than this (dB below full scale) can't be told apart from a voice. */
const MAX_NOISE_DB = -30;
const SAMPLE_MS = 50;

function percentile(sorted, p) {
  return sorted[Math.min(sorted.length - 1, Math.floor(p * sorted.length))];
}

const unclear = (reason, totalSeconds) => ({ clear: false, reason, totalSeconds });

/**
 * @param frames one loudness reading (dB, 0 is full scale) per entry, `t` in ms from the start of the recording
 * @param totalMs how long the recording ran
 */
export function analyzeLevels(frames, totalMs = frames.length ? frames[frames.length - 1].t : 0) {
  const totalSeconds = totalMs / 1000;
  if (frames.length < 20) return unclear('too-short', totalSeconds);

  // A tab in the background gets its timers slowed to a crawl, which would smear every gap.
  const gaps = frames.slice(1).map((f, i) => f.t - frames[i].t);
  const slow = gaps.filter((g) => g > 250).length / gaps.length;
  if (slow > 0.1) return unclear('sampling', totalSeconds);

  const levels = frames.map((f) => f.db).sort((a, b) => a - b);
  const noise = percentile(levels, 0.1);
  const loud = percentile(levels, 0.9);
  if (loud - noise < MIN_RANGE_DB || noise > MAX_NOISE_DB) return unclear('noisy', totalSeconds);

  const threshold = noise + Math.max(6, 0.35 * (loud - noise));
  const end = (i) => (i + 1 < frames.length ? frames[i + 1].t : Math.max(frames[i].t + SAMPLE_MS, totalMs));

  // Runs of frames above the threshold, as [start, end] in ms.
  let runs = [];
  let from = null;
  frames.forEach((f, i) => {
    if (f.db >= threshold) {
      if (from === null) from = f.t;
    } else if (from !== null) {
      runs.push([from, f.t]);
      from = null;
    }
    if (i === frames.length - 1 && from !== null) runs.push([from, end(i)]);
  });
  runs = runs.filter(([a, b]) => b - a >= MIN_SPEECH_MS);

  // Join the runs separated only by a breath.
  const speech = [];
  for (const run of runs) {
    const last = speech[speech.length - 1];
    if (last && run[0] - last[1] < BRIDGE_MS) last[1] = run[1];
    else speech.push([...run]);
  }
  const spoken = speech.reduce((sum, [a, b]) => sum + (b - a), 0);
  if (spoken < MIN_TOTAL_SPEECH_MS) return unclear('too-little-speech', totalSeconds);

  const between = speech.slice(1).map(([a], i) => a - speech[i][1]);
  const longPauses = between.filter((g) => g >= LONG_PAUSE_MS);
  const first = speech[0][0];
  const last = speech[speech.length - 1][1];
  const speakingMs = last - first - longPauses.reduce((sum, g) => sum + g, 0);

  return {
    clear: true,
    reason: 'ok',
    totalSeconds,
    leadingSilenceSeconds: first / 1000,
    speakingSeconds: speakingMs / 1000,
    longPauses: longPauses.length,
    longestPauseSeconds: between.length ? Math.max(...between) / 1000 : 0,
  };
}

/** Listens to a microphone stream while an answer is spoken, and sums it up when asked. */
export class SpeechTimer {
  constructor(stream) {
    this.stream = stream;
    this.frames = [];
    this.timer = null;
    this.context = null;
    this.startedAt = 0;
  }

  start() {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return;
    try {
      this.context = new AudioContextClass();
      this.context.resume?.();
      const analyser = this.context.createAnalyser();
      analyser.fftSize = 2048;
      // Only listened to: never connected to the speakers.
      this.context.createMediaStreamSource(this.stream).connect(analyser);
      const samples = new Float32Array(analyser.fftSize);
      this.startedAt = performance.now();
      this.timer = setInterval(() => {
        analyser.getFloatTimeDomainData(samples);
        let sum = 0;
        for (let i = 0; i < samples.length; i++) sum += samples[i] * samples[i];
        const rms = Math.sqrt(sum / samples.length);
        this.frames.push({ t: performance.now() - this.startedAt, db: 20 * Math.log10(Math.max(rms, 1e-8)) });
      }, SAMPLE_MS);
    } catch {
      this.dispose();
    }
  }

  /** Stops listening and returns what was heard; see {@link analyzeLevels}. */
  finish() {
    const totalMs = this.startedAt ? performance.now() - this.startedAt : 0;
    const frames = this.frames;
    this.dispose();
    if (frames.length === 0) return unclear('unsupported', totalMs / 1000);
    return analyzeLevels(frames, totalMs);
  }

  dispose() {
    clearInterval(this.timer);
    this.timer = null;
    this.context?.close?.().catch(() => {});
    this.context = null;
  }
}
