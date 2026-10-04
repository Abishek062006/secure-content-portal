/**
 * Interview recordings kept on this device, in the browser's IndexedDB. Nothing here ever touches the server: a recording is a
 * list of video chunks plus one small record about it (who made it, when, how big, where each question started).
 *
 * localStorage can't hold video (about 5 MB, text only); IndexedDB stores files and has room for a few hundred MB.
 */

const DB_NAME = 'gn-interview-recordings';
const DB_VERSION = 1;

/** How many recordings a learner keeps; saving a new one past this deletes the oldest. */
export const KEEP_RECORDINGS = 5;

/** 1536 -> "2 KB", 83886080 -> "80 MB". */
export function formatBytes(bytes) {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
  if (bytes >= 1024 * 1024) return `${Math.round(bytes / (1024 * 1024))} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/** 45000 -> "45 sec", 870000 -> "15 min": how long a recording runs, in words a list can show next to a date. */
export function formatLength(ms) {
  const seconds = Math.round((ms || 0) / 1000);
  return seconds < 60 ? `${seconds} sec` : `${Math.round(seconds / 60)} min`;
}

let dbPromise = null;

function openDb() {
  if (!dbPromise) {
    dbPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        db.createObjectStore('recordings', { keyPath: 'sessionId' });
        db.createObjectStore('chunks', { keyPath: ['sessionId', 'seq'] });
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => {
        dbPromise = null;
        reject(request.error);
      };
    });
  }
  return dbPromise;
}

/** Runs `work` in one transaction and resolves, once it has committed, with the request `work` returned (or its value). */
async function run(stores, mode, work) {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(stores, mode);
    let result;
    tx.oncomplete = () => resolve(result instanceof IDBRequest ? result.result : result);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error || new DOMException('The save was cancelled.', 'AbortError'));
    try {
      result = work(tx);
    } catch (err) {
      tx.abort();
      reject(err);
    }
  });
}

const chunkRange = (sessionId) => IDBKeyRange.bound([sessionId, 0], [sessionId, Infinity]);

export function saveRecording(meta) {
  return run(['recordings'], 'readwrite', (tx) => tx.objectStore('recordings').put(meta));
}

/** Adds one chunk and keeps the recording's size and last-written time up to date, all or nothing. */
export function appendChunk(sessionId, seq, blob) {
  return run(['recordings', 'chunks'], 'readwrite', (tx) => {
    tx.objectStore('chunks').put({ sessionId, seq, blob });
    const store = tx.objectStore('recordings');
    const read = store.get(sessionId);
    read.onsuccess = () => {
      const meta = read.result;
      if (meta) store.put({ ...meta, sizeBytes: meta.sizeBytes + blob.size, lastChunkAt: Date.now() });
    };
    return undefined;
  });
}

/** Merges `patch` into the recording's record and resolves with the result. */
export async function updateRecording(sessionId, patch) {
  let merged = null;
  await run(['recordings'], 'readwrite', (tx) => {
    const store = tx.objectStore('recordings');
    const read = store.get(sessionId);
    read.onsuccess = () => {
      if (!read.result) return;
      merged = { ...read.result, ...patch };
      store.put(merged);
    };
    return undefined;
  });
  return merged;
}

export function getRecording(sessionId) {
  return run(['recordings'], 'readonly', (tx) => tx.objectStore('recordings').get(sessionId)).then((meta) => meta || null);
}

/** One learner's recordings, newest first. A shared computer never shows one person another's. */
export async function listRecordings(userId) {
  const all = await run(['recordings'], 'readonly', (tx) => tx.objectStore('recordings').getAll());
  return all.filter((r) => r.userId === userId).sort((a, b) => b.startedAt - a.startedAt);
}

/** The recording as one video file, with its record; null when there are no chunks (it was deleted or never saved). */
export async function readRecording(sessionId) {
  const meta = await getRecording(sessionId);
  if (!meta) return null;
  const rows = await run(['chunks'], 'readonly', (tx) => tx.objectStore('chunks').getAll(chunkRange(sessionId)));
  if (rows.length === 0) return null;
  return { meta, blob: new Blob(rows.map((row) => row.blob), { type: meta.mimeType }) };
}

export function deleteRecording(sessionId) {
  return run(['recordings', 'chunks'], 'readwrite', (tx) => {
    tx.objectStore('recordings').delete(sessionId);
    tx.objectStore('chunks').delete(chunkRange(sessionId));
    return undefined;
  });
}

/**
 * A tab that closed mid-interview leaves a recording still marked as recording. Whatever was saved is kept: it is marked as
 * stopped early, with its length worked out from when it last wrote.
 */
export async function settleInterrupted(userId, activeSessionId = null) {
  const mine = await listRecordings(userId);
  const stale = mine.filter((r) => r.status === 'recording' && r.sessionId !== activeSessionId);
  await Promise.all(stale.map((r) => updateRecording(r.sessionId, {
    status: 'interrupted',
    durationMs: Math.max(0, (r.lastChunkAt || r.startedAt) - r.startedAt),
  })));
}

/** Deletes a learner's oldest recordings beyond `keep`, never the one named by `protectedId` (the one being recorded). */
export async function enforceLimit(userId, keep = KEEP_RECORDINGS, protectedId = null) {
  const mine = await listRecordings(userId);
  const surplus = mine.filter((r) => r.sessionId !== protectedId).slice(Math.max(0, keep - (protectedId == null ? 0 : 1)));
  await Promise.all(surplus.map((r) => deleteRecording(r.sessionId)));
}

/** What the recordings take, and what the browser says it has left, when it will say. */
export async function storageUsage(userId) {
  const mine = await listRecordings(userId);
  const appBytes = mine.reduce((sum, r) => sum + (r.sizeBytes || 0), 0);
  let browserUsage = null;
  let browserQuota = null;
  try {
    const estimate = await navigator.storage?.estimate?.();
    browserUsage = estimate?.usage ?? null;
    browserQuota = estimate?.quota ?? null;
  } catch { /* some browsers don't say */ }
  return { count: mine.length, appBytes, browserUsage, browserQuota };
}

/** Asks the browser not to clear the recordings when it is short of space. It may say no, and that's fine. */
export async function requestPersistence() {
  try {
    return (await navigator.storage?.persist?.()) ?? false;
  } catch {
    return false;
  }
}
