import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import {
  appendChunk, deleteAllRecordings, deleteRecording, enforceLimit, formatBytes, formatLength, getRecording, listRecordings, readRecording, saveRecording,
  settleInterrupted, updateRecording,
} from './localRecordings';

const meta = (sessionId, userId, extra = {}) => ({
  sessionId, userId, title: `Interview ${sessionId}`, startedAt: 1000 * sessionId, lastChunkAt: 1000 * sessionId, status: 'complete',
  mimeType: 'video/webm', sizeBytes: 0, durationMs: 0, markers: [], ...extra,
});

async function clearAll() {
  for (const userId of [7, 8]) {
    for (const r of await listRecordings(userId)) await deleteRecording(r.sessionId);
  }
}

beforeEach(clearAll);

describe('formatting', () => {
  it('writes sizes and lengths in plain units', () => {
    expect(formatBytes(1536)).toBe('2 KB');
    expect(formatBytes(83886080)).toBe('80 MB');
    expect(formatBytes(3 * 1024 * 1024 * 1024)).toBe('3.0 GB');
    expect(formatLength(40000)).toBe('40 sec');
    expect(formatLength(870000)).toBe('15 min');
    expect(formatLength(0)).toBe('0 sec');
  });
});

describe('saving a recording in chunks', () => {
  it('keeps the size up to date and reads the chunks back in order as one file', async () => {
    await saveRecording(meta(1, 7, { status: 'recording' }));
    await appendChunk(1, 0, new Blob(['aaa']));
    await appendChunk(1, 1, new Blob(['bbbb']));
    await appendChunk(1, 2, new Blob(['cc']));

    expect((await getRecording(1)).sizeBytes).toBe(9);
    const { meta: found, blob } = await readRecording(1);
    expect(found.sizeBytes).toBe(9);
    expect(blob.size).toBe(9);
    expect(await blob.text()).toBe('aaabbbbcc');
  });

  it('merges changes into the record without losing the rest', async () => {
    await saveRecording(meta(1, 7, { markers: [{ type: 'question', offsetMs: 5 }] }));

    const merged = await updateRecording(1, { status: 'complete', durationMs: 4000 });

    expect(merged).toMatchObject({ status: 'complete', durationMs: 4000, title: 'Interview 1' });
    expect(merged.markers).toHaveLength(1);
    expect(await updateRecording(99, { status: 'x' })).toBeNull();
  });

  it('has nothing to read for a recording with no chunks or none at all', async () => {
    await saveRecording(meta(1, 7));
    expect(await readRecording(1)).toBeNull();
    expect(await readRecording(404)).toBeNull();
  });

  it('removes the record and every chunk when deleted', async () => {
    await saveRecording(meta(1, 7));
    await appendChunk(1, 0, new Blob(['x']));
    await appendChunk(2, 0, new Blob(['someone else'])); // a chunk of another recording stays

    await deleteRecording(1);

    expect(await getRecording(1)).toBeNull();
    expect(await readRecording(1)).toBeNull();
    await saveRecording(meta(2, 7));
    expect((await readRecording(2)).blob.size).toBe(12);
  });
});

describe('who can see a recording', () => {
  it('lists only the signed-in learner\'s recordings, newest first', async () => {
    await saveRecording(meta(1, 7));
    await saveRecording(meta(2, 8));
    await saveRecording(meta(3, 7));

    expect((await listRecordings(7)).map((r) => r.sessionId)).toEqual([3, 1]);
    expect((await listRecordings(8)).map((r) => r.sessionId)).toEqual([2]);
    expect(await listRecordings(99)).toEqual([]);
  });
});

describe('deleting everything', () => {
  it('removes all of one learner\'s recordings and chunks, and nobody else\'s', async () => {
    await saveRecording(meta(1, 7));
    await appendChunk(1, 0, new Blob(['x']));
    await saveRecording(meta(2, 7));
    await saveRecording(meta(3, 8));
    await appendChunk(3, 0, new Blob(['keep me']));

    expect(await deleteAllRecordings(7)).toBe(2);

    expect(await listRecordings(7)).toEqual([]);
    expect(await readRecording(1)).toBeNull();
    expect((await readRecording(3)).blob.size).toBe(7);
    expect(await deleteAllRecordings(7)).toBe(0);
  });
});

describe('keeping only the latest few', () => {
  it('deletes the oldest beyond the limit', async () => {
    for (let i = 1; i <= 7; i++) await saveRecording(meta(i, 7));

    await enforceLimit(7, 5);

    expect((await listRecordings(7)).map((r) => r.sessionId)).toEqual([7, 6, 5, 4, 3]);
  });

  it('counts the recording in progress in the limit and never deletes it', async () => {
    for (let i = 1; i <= 6; i++) await saveRecording(meta(i, 7));
    await saveRecording(meta(0, 7, { startedAt: 1, status: 'recording' })); // the oldest by date, but being recorded now

    await enforceLimit(7, 5, 0);

    const kept = (await listRecordings(7)).map((r) => r.sessionId);
    expect(kept).toHaveLength(5);
    expect(kept).toContain(0);
  });

  it('never touches another learner\'s recordings', async () => {
    for (let i = 1; i <= 6; i++) await saveRecording(meta(i, 7));
    await saveRecording(meta(50, 8));

    await enforceLimit(7, 5);

    expect((await listRecordings(8)).map((r) => r.sessionId)).toEqual([50]);
  });
});

describe('a tab closed in the middle of a recording', () => {
  it('is kept and marked stopped early, with a length worked out from the last write', async () => {
    await saveRecording(meta(1, 7, { status: 'recording', startedAt: 100_000, lastChunkAt: 100_000 }));
    await updateRecording(1, { lastChunkAt: 110_000, sizeBytes: 5000 });

    await settleInterrupted(7);

    expect(await getRecording(1)).toMatchObject({ status: 'interrupted', durationMs: 10_000 });
  });

  it('leaves the recording in progress alone, and recordings already finished', async () => {
    await saveRecording(meta(1, 7, { status: 'recording' }));
    await saveRecording(meta(2, 7, { status: 'complete', durationMs: 777 }));

    await settleInterrupted(7, 1);

    expect((await getRecording(1)).status).toBe('recording');
    expect(await getRecording(2)).toMatchObject({ status: 'complete', durationMs: 777 });
  });
});
