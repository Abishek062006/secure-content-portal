import { api } from '../api';

const PARALLEL_PARTS = 3;
const ATTEMPTS = 3;

let configPromise = null;

/** Whether the server can take uploads straight to the storage bucket; asked once per page load. */
export function uploadConfig() {
  if (!configPromise) {
    configPromise = api.get('/api/admin/uploads/config').catch(() => ({ direct: false }));
  }
  return configPromise;
}

/** PUTs one part to its signed URL and resolves with the ETag the bucket answers with. */
function putPart(url, blob, onBytes) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    xhr.upload.onprogress = (e) => { if (e.lengthComputable) onBytes(e.loaded); };
    xhr.onload = () => {
      const etag = xhr.getResponseHeader('ETag');
      if (xhr.status >= 200 && xhr.status < 300 && etag) resolve(etag);
      else reject(new Error(xhr.status >= 200 && xhr.status < 300
        ? 'The storage service did not return a part receipt (check its CORS settings).' : `Upload of a part failed (${xhr.status}).`));
    };
    xhr.onerror = () => reject(new Error('Network error while uploading a part.'));
    xhr.send(blob);
  });
}

/**
 * Uploads a video in parts straight to the bucket, a few at a time, retrying a part if it fails, then asks the
 * server to assemble and check it. Resolves with the new lesson.
 */
export async function uploadVideoDirect({ file, moduleId, title, description }, onProgress) {
  const { id, partSizeBytes, partCount } = await api.post(`/api/admin/modules/${moduleId}/uploads`, {
    title, description, filename: file.name, sizeBytes: file.size,
  });

  try {
    const receipts = new Map();
    (await api.get(`/api/admin/uploads/${id}/parts`)).forEach((p) => receipts.set(p.partNumber, p.etag));

    const sent = new Array(partCount + 1).fill(0);
    receipts.forEach((_, n) => { sent[n] = Math.min(partSizeBytes, file.size - (n - 1) * partSizeBytes); });
    const report = () => onProgress(Math.min(1, sent.reduce((a, b) => a + b, 0) / file.size));
    report();

    const pending = [];
    for (let n = 1; n <= partCount; n += 1) if (!receipts.has(n)) pending.push(n);

    async function worker() {
      while (pending.length) {
        const n = pending.shift();
        const blob = file.slice((n - 1) * partSizeBytes, Math.min(file.size, n * partSizeBytes));
        let lastError;
        for (let attempt = 1; attempt <= ATTEMPTS; attempt += 1) {
          try {
            // A fresh URL each try, so a slow retry never uses one that is about to expire.
            // eslint-disable-next-line no-await-in-loop
            const [{ url }] = await api.post(`/api/admin/uploads/${id}/parts`, { partNumbers: [n] });
            // eslint-disable-next-line no-await-in-loop
            const etag = await putPart(url, blob, (bytes) => { sent[n] = bytes; report(); });
            receipts.set(n, etag);
            sent[n] = blob.size;
            report();
            lastError = null;
            break;
          } catch (err) {
            lastError = err;
            sent[n] = 0;
            report();
          }
        }
        if (lastError) throw lastError;
      }
    }
    await Promise.all(Array.from({ length: Math.min(PARALLEL_PARTS, pending.length || 1) }, worker));

    return await api.post(`/api/admin/uploads/${id}/complete`, {
      parts: [...receipts].map(([partNumber, etag]) => ({ partNumber, etag })),
    });
  } catch (err) {
    api.del(`/api/admin/uploads/${id}`).catch(() => {});
    throw err;
  }
}

/**
 * Adds a lesson from the "add lesson" form: straight to the bucket when the server supports it (the transcript, a
 * small text file, still goes through the server), otherwise through the server as before.
 */
export async function addLessonWithVideo(moduleId, formData, onProgress) {
  const config = await uploadConfig();
  if (!config.direct) {
    return api.uploadWithProgress(`/api/admin/modules/${moduleId}/lessons`, formData, onProgress);
  }
  const lesson = await uploadVideoDirect({
    file: formData.get('video'), moduleId, title: formData.get('title'), description: formData.get('description') || null,
  }, onProgress);
  const transcript = formData.get('transcript');
  if (transcript && transcript.size) {
    const body = new FormData();
    body.set('file', transcript);
    await api.upload(`/api/admin/lessons/${lesson.id}/transcript`, body);
  }
  return lesson;
}
