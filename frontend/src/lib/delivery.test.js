import { describe, expect, it } from 'vitest';
import { answerDeliveryLine, deliveryMeasures, deliverySummary, deliveryTrends, formatSeconds, setupMeasures } from './delivery';

const voice = (think, speaking, wpm, pauses, longest) => ({
  mode: 'VOICE', thinkingSeconds: think, speakingSeconds: speaking, wordsPerMinute: wpm, longPauses: pauses, longestPauseSeconds: longest, audioClear: true,
});
const noisy = (think) => ({ mode: 'VOICE', thinkingSeconds: think, speakingSeconds: null, wordsPerMinute: null, longPauses: null, longestPauseSeconds: null, audioClear: false });
const typed = (think) => ({ mode: 'TYPED', thinkingSeconds: think, speakingSeconds: null, wordsPerMinute: null, longPauses: null, longestPauseSeconds: null, audioClear: null });
const withDelivery = (...deliveries) => deliveries.map((delivery) => ({ delivery }));

describe('formatSeconds', () => {
  it('writes short times in seconds and long ones in minutes', () => {
    expect(formatSeconds(6)).toBe('6 s');
    expect(formatSeconds(59.6)).toBe('1 min 0 s');
    expect(formatSeconds(65)).toBe('1 min 5 s');
  });
});

describe('deliverySummary', () => {
  it('is null when no answer has any delivery', () => {
    expect(deliverySummary(withDelivery(null, null))).toBeNull();
  });

  it('weights the pace by speaking time, so a long answer counts for more than a short one', () => {
    const summary = deliverySummary(withDelivery(voice(4, 60, 150, 2, 3), voice(8, 20, 100, 0, 0.8)));

    expect(summary.pace).toBe(138);
  });

  it('averages the thinking time over every answer, typed or spoken', () => {
    const summary = deliverySummary(withDelivery(voice(4, 60, 150, 2, 3), noisy(6), typed(12), null));

    expect(summary.thinking).toBe(7);
    expect(summary).toMatchObject({ answers: 3, spoken: 2, typed: 1, unclear: 1 });
  });

  it('leaves noisy answers out of the pace and pauses, and says how many there were', () => {
    const summary = deliverySummary(withDelivery(noisy(5), noisy(7)));

    expect(summary.pace).toBeNull();
    expect(summary.longPausesPerAnswer).toBeNull();
    expect(summary.unclear).toBe(2);
  });

  it('counts long pauses per clear spoken answer and finds the longest one with its question number', () => {
    const summary = deliverySummary([...withDelivery(voice(1, 30, 150, 2, 3.4)), ...withDelivery(voice(1, 30, 150, 0, 0.9)), ...withDelivery(voice(1, 30, 150, 4, 5.1))]);

    expect(summary.longPauses).toBe(6);
    expect(summary.longPausesPerAnswer).toBe(2);
    expect(summary.longest).toEqual({ seconds: 5.1, number: 3 });
  });

  it('does not report a "longest pause" shorter than a long pause', () => {
    expect(deliverySummary(withDelivery(voice(1, 30, 150, 0, 0.9))).longest).toBeNull();
  });
});

describe('deliveryMeasures', () => {
  const measure = (key, summary) => deliveryMeasures(summary).find((m) => m.key === key);
  const base = { pace: null, thinking: null, longPausesPerAnswer: null, longPauses: null, longest: null };

  it('describes pace in bands, comfortable between 120 and 170', () => {
    expect(measure('pace', { ...base, pace: 90 })).toMatchObject({ label: 'Slow', status: 'watch' });
    expect(measure('pace', { ...base, pace: 110 })).toMatchObject({ label: 'A little slow', status: 'watch' });
    expect(measure('pace', { ...base, pace: 120 })).toMatchObject({ label: 'Comfortable', status: 'good' });
    expect(measure('pace', { ...base, pace: 170 })).toMatchObject({ label: 'Comfortable', status: 'good' });
    expect(measure('pace', { ...base, pace: 180 })).toMatchObject({ label: 'A little fast', status: 'watch' });
    expect(measure('pace', { ...base, pace: 200 })).toMatchObject({ label: 'Fast', status: 'watch' });
  });

  it('describes the time before starting', () => {
    expect(measure('thinking', { ...base, thinking: 1 })).toMatchObject({ label: 'Started right away', status: 'good' });
    expect(measure('thinking', { ...base, thinking: 8 })).toMatchObject({ label: 'Natural', status: 'good' });
    expect(measure('thinking', { ...base, thinking: 20 })).toMatchObject({ label: 'A bit long', status: 'watch' });
    expect(measure('thinking', { ...base, thinking: 40 })).toMatchObject({ label: 'Long', status: 'watch' });
  });

  it('describes long pauses and mentions the longest', () => {
    expect(measure('pauses', { ...base, longPausesPerAnswer: 0, longPauses: 0 })).toMatchObject({ label: 'None', status: 'good' });
    expect(measure('pauses', { ...base, longPausesPerAnswer: 1, longPauses: 3 })).toMatchObject({ label: 'A few', status: 'good' });
    const several = measure('pauses', { ...base, longPausesPerAnswer: 2.5, longPauses: 5, longest: { seconds: 4.2, number: 2 } });
    expect(several).toMatchObject({ label: 'Several', status: 'watch' });
    expect(several.extra).toBe('Longest: 4 s, on question 2.');
  });

  it('only shows what was measured', () => {
    expect(deliveryMeasures(base)).toEqual([]);
  });
});

describe('answerDeliveryLine', () => {
  it('is null when nothing was measured', () => expect(answerDeliveryLine({ delivery: null })).toBeNull());

  it('describes a spoken, a noisy and a typed answer', () => {
    expect(answerDeliveryLine({ delivery: voice(6, 58, 148, 2, 3.4) })).toBe('Spoken · 148 words a minute · thought for 6 s · 2 long pauses');
    expect(answerDeliveryLine({ delivery: voice(6, 58, 148, 1, 2) })).toContain('1 long pause');
    expect(answerDeliveryLine({ delivery: noisy(4) })).toBe('Spoken · too noisy to measure pace and pauses · thought for 4 s');
    expect(answerDeliveryLine({ delivery: typed(21) })).toBe('Typed · thought for 21 s');
  });
});

describe('deliveryTrends', () => {
  const summary = { pace: 177, thinking: 11, longPausesPerAnswer: 1.67 };
  const trend = [
    { sessionId: 2, wordsPerMinute: 201, thinkingSeconds: 21, longPausesPerAnswer: 2.5 },
    { sessionId: 3, wordsPerMinute: 192, thinkingSeconds: 14, longPausesPerAnswer: 1.7 },
    { sessionId: 5, wordsPerMinute: 180, thinkingSeconds: 11, longPausesPerAnswer: 1.4 },
  ];

  it('lists the earlier values in order and then this interview, leaving this interview out of the earlier ones', () => {
    const rows = deliveryTrends(summary, trend, 5);

    expect(rows.map((r) => r.key)).toEqual(['pace', 'thinking', 'pauses']);
    expect(rows[0].values).toEqual(['201', '192', '177']);
    expect(rows[1].values).toEqual(['21', '14', '11']);
    expect(rows[2].values).toEqual(['2.5', '1.7', '1.7']);
  });

  it('is empty when there is nothing earlier to compare with', () => {
    expect(deliveryTrends(summary, [{ sessionId: 5, wordsPerMinute: 180 }], 5)).toEqual([]);
    expect(deliveryTrends(summary, [], 5)).toEqual([]);
  });

  it('skips a measure that is missing now or was missing before', () => {
    const rows = deliveryTrends({ pace: null, thinking: 11, longPausesPerAnswer: null }, trend, 5);

    expect(rows.map((r) => r.key)).toEqual(['thinking']);
  });
});

describe('setupMeasures', () => {
  it('is empty when the camera was not used', () => {
    expect(setupMeasures({ faceVisiblePercent: null, lightingGoodPercent: null })).toEqual([]);
    expect(setupMeasures(undefined)).toEqual([]);
  });

  it('judges the share of the time the face was in frame and the lighting good', () => {
    const [face, light] = setupMeasures({ faceVisiblePercent: 96, lightingGoodPercent: 40 });

    expect(face).toMatchObject({ key: 'face', status: 'good', value: '96% of the time' });
    expect(light).toMatchObject({ key: 'lighting', status: 'watch' });
    expect(setupMeasures({ faceVisiblePercent: 75 })[0].status).toBe('watch');
  });
});
