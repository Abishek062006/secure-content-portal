import { describe, expect, it } from 'vitest';
import { analyzeLevels } from './speechTiming';

/** Loudness readings every 50 ms: `quiet` dB everywhere except inside the [start, end] ms bursts, which are `loud` dB. */
function frames(totalMs, bursts, { quiet = -60, loud = -25, step = 50, jitter = 2 } = {}) {
  const out = [];
  let seed = 7;
  const rand = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647 - 0.5;
  };
  for (let t = 0; t <= totalMs; t += step) {
    const on = bursts.some(([a, b]) => t >= a && t < b);
    out.push({ t, db: (on ? loud : quiet) + rand() * 2 * jitter });
  }
  return out;
}

/** Words: `count` bursts of `on` ms with `off` ms between, from `from` ms. */
const words = (from, count, on = 400, off = 120) => Array.from({ length: count }, (_, i) => [from + i * (on + off), from + i * (on + off) + on]);

describe('analyzeLevels', () => {
  it('finds the time before speaking, a long pause, and the speaking time without it', () => {
    const first = words(1200, 6);
    const second = words(first.at(-1)[1] + 2500, 7);
    const totalMs = second.at(-1)[1] + 1000;

    const r = analyzeLevels(frames(totalMs, [...first, ...second]), totalMs);

    const pause = (second[0][0] - first.at(-1)[1]) / 1000;
    expect(r.clear).toBe(true);
    expect(r.leadingSilenceSeconds).toBeCloseTo(1.2, 0);
    expect(Math.abs(r.leadingSilenceSeconds - 1.2)).toBeLessThan(0.12);
    expect(r.longPauses).toBe(1);
    expect(Math.abs(r.longestPauseSeconds - pause)).toBeLessThan(0.15);
    expect(Math.abs(r.speakingSeconds - ((second.at(-1)[1] - first[0][0]) / 1000 - pause))).toBeLessThan(0.3);
  });

  it('does not count a breath between words as a pause', () => {
    const breaths = words(500, 12, 400, 250);
    const totalMs = breaths.at(-1)[1] + 500;

    const r = analyzeLevels(frames(totalMs, breaths), totalMs);

    expect(r.clear).toBe(true);
    expect(r.longPauses).toBe(0);
    expect(r.longestPauseSeconds).toBeLessThan(0.4);
  });

  it('notices a 1.2 second silence but does not call it a long pause', () => {
    const speech = [...words(500, 5), ...words(500 + 5 * 520 + 1200, 5)];
    const totalMs = speech.at(-1)[1] + 500;

    const r = analyzeLevels(frames(totalMs, speech), totalMs);

    expect(r.clear).toBe(true);
    expect(r.longPauses).toBe(0);
    expect(Math.abs(r.longestPauseSeconds - 1.2)).toBeLessThan(0.2);
  });

  it('says a noisy room is not clear', () => {
    const noise = Array.from({ length: 300 }, (_, i) => ({ t: i * 50, db: -42 + ((i * 37) % 7) - 3 }));
    expect(analyzeLevels(noise, 15000).clear).toBe(false);
  });

  it('says a room as loud as the voice is not clear', () => {
    expect(analyzeLevels(frames(12000, words(1000, 15), { quiet: -22, loud: -12 }), 12000).clear).toBe(false);
  });

  it('says talking with no pause at all is not clear, since there is nothing to compare against', () => {
    expect(analyzeLevels(frames(12000, [[0, 12000]]), 12000).clear).toBe(false);
  });

  it('says almost no speech is not clear', () => {
    expect(analyzeLevels(frames(8000, words(2000, 2)), 8000).clear).toBe(false);
  });

  it('says a very short recording is not clear', () => {
    expect(analyzeLevels(frames(500, []), 500)).toMatchObject({ clear: false, reason: 'too-short' });
  });

  it('says readings from a slowed-down background tab are not clear', () => {
    expect(analyzeLevels(frames(20000, words(1000, 20), { step: 1000 }), 20000)).toMatchObject({ clear: false, reason: 'sampling' });
  });

  it('does not take clicks and bumps for speech', () => {
    expect(analyzeLevels(frames(10000, [[1000, 1060], [3000, 3050], [5000, 5080]]), 10000).clear).toBe(false);
  });
});
