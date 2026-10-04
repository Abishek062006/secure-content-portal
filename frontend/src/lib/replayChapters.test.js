import { describe, expect, it } from 'vitest';
import { buildChapters, chapterAt } from './replayChapters';

const markers = [
  { type: 'question', offsetMs: 20, questionId: 11, text: 'First?' },
  { type: 'answer', offsetMs: 9800, questionId: 11 },
  { type: 'question', offsetMs: 19900, questionId: 12, text: 'Second?' },
  { type: 'answer', offsetMs: 25000, questionId: 12 },
];

describe('buildChapters', () => {
  it('makes one chapter per question, each ending where the next begins', () => {
    const chapters = buildChapters(markers, 26500);

    expect(chapters).toEqual([
      { questionId: 11, text: 'First?', startMs: 20, endMs: 19900, answeredMs: 9800 },
      { questionId: 12, text: 'Second?', startMs: 19900, endMs: 26500, answeredMs: 25000 },
    ]);
  });

  it('leaves answeredMs empty for a question that was never answered', () => {
    expect(buildChapters(markers.slice(0, 1), 5000)[0].answeredMs).toBeNull();
  });

  it('copes with no markers at all', () => {
    expect(buildChapters([], 5000)).toEqual([]);
    expect(buildChapters(undefined, 5000)).toEqual([]);
  });
});

describe('chapterAt', () => {
  const chapters = buildChapters(markers, 26500);

  it('is empty before the first question', () => expect(chapterAt(chapters, 0)).toBeNull());
  it('finds the chapter playing', () => {
    expect(chapterAt(chapters, 5000).questionId).toBe(11);
    expect(chapterAt(chapters, 19900).questionId).toBe(12);
    expect(chapterAt(chapters, 26000).questionId).toBe(12);
  });
});
