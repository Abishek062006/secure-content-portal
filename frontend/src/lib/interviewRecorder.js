import { KEEP_RECORDINGS, appendChunk, deleteRecording, enforceLimit, getRecording, saveRecording, updateRecording } from './localRecordings';

/** A chunk is written every few seconds, so closing the tab loses at most the last few. */
const CHUNK_MS = 4000;

// VP8 first: software-encoding VP9 is noticeably heavier, and the 3D interviewer is already using the graphics card.
const TYPES = ['video/webm;codecs=vp8,opus', 'video/webm', 'video/mp4'];

const CAMERA_AND_MIC = {
  video: { width: { ideal: 640 }, height: { ideal: 480 }, frameRate: { ideal: 24 }, facingMode: 'user' },
  audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
};

function pickType() {
  return TYPES.find((t) => window.MediaRecorder.isTypeSupported?.(t));
}

/** A plain-words reason for a camera or microphone that wouldn't start. */
export function startProblem(err) {
  switch (err?.name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return "Camera or microphone access was blocked. Allow both in your browser's address bar, or continue without recording.";
    case 'NotFoundError':
    case 'OverconstrainedError':
      return 'No camera or microphone was found. Connect one, or continue without recording.';
    case 'NotReadableError':
      return 'Your camera or microphone is being used by another app. Close it and try again, or continue without recording.';
    default:
      return "We couldn't start the recording. You can continue without it.";
  }
}

/**
 * Records one interview (camera and microphone) into this device's storage while it happens. Markers (where each question
 * and answer begins) are saved alongside so the replay can jump between them.
 *
 * `getStream` is for tests; by default the browser asks for the camera and microphone.
 */
export class InterviewRecorder {
  constructor({ sessionId, userId, title = '', onProblem = null, getStream = null }) {
    this.sessionId = sessionId;
    this.userId = userId;
    this.title = title;
    this.onProblem = onProblem;
    this.getStream = getStream || (() => navigator.mediaDevices.getUserMedia(CAMERA_AND_MIC));
    this.stream = null;
    this.recorder = null;
    this.startedAt = 0;
    this.t0 = 0;
    this.seq = 0;
    this.markers = [];
    this.writes = Promise.resolve();
    this.failed = false;
    this.stopping = null;
  }

  async start() {
    this.stream = await this.getStream();
    const type = pickType();
    try {
      this.recorder = new MediaRecorder(this.stream, { videoBitsPerSecond: 600_000, audioBitsPerSecond: 64_000, ...(type ? { mimeType: type } : {}) });
    } catch (err) {
      this.releaseTracks();
      throw err;
    }

    // Starting again for the same interview (a reload before the first answer) replaces what was there.
    if (await getRecording(this.sessionId)) await deleteRecording(this.sessionId);
    this.startedAt = Date.now();
    await saveRecording({
      sessionId: this.sessionId, userId: this.userId, title: this.title, startedAt: this.startedAt, lastChunkAt: this.startedAt,
      status: 'recording', mimeType: this.recorder.mimeType || type || 'video/webm', sizeBytes: 0, durationMs: 0, markers: [],
    });

    this.recorder.ondataavailable = (event) => {
      if (!event.data || event.data.size === 0) return;
      const seq = this.seq++;
      this.queue(() => appendChunk(this.sessionId, seq, event.data), 'storage');
    };
    this.recorder.onerror = () => this.problem('recorder', 'The recording stopped unexpectedly. What was saved so far is kept.');
    this.stream.getVideoTracks()[0]?.addEventListener('ended', () => this.problem('camera', 'Your camera stopped. The interview can continue.'));

    this.t0 = performance.now();
    this.recorder.start(CHUNK_MS);
  }

  /** One camera-and-microphone stream for the whole interview, so answers by voice share it instead of opening a second one. */
  audioForAnswer() {
    if (!this.stream || this.stopping) return null;
    const tracks = this.stream.getAudioTracks().filter((t) => t.readyState === 'live');
    // Clones, so the voice answer can stop its copy without ending the recording's microphone.
    return tracks.length ? new MediaStream(tracks.map((t) => t.clone())) : null;
  }

  elapsedMs() {
    return Math.round(performance.now() - this.t0);
  }

  /** Notes where something happened, in milliseconds from the start of the recording. Repeats are ignored. */
  mark(type, data = {}) {
    if (this.stopping) return;
    const last = this.markers[this.markers.length - 1];
    if (last && last.type === type && last.questionId === data.questionId) return;
    this.markers.push({ type, offsetMs: this.elapsedMs(), ...data });
    const markers = [...this.markers];
    this.queue(() => updateRecording(this.sessionId, { markers }), 'storage');
  }

  /** Stops, saves what's left and tidies up old recordings. Safe to call more than once. */
  stop() {
    if (!this.stopping) this.stopping = this.finish();
    return this.stopping;
  }

  async finish() {
    const durationMs = this.elapsedMs();
    if (this.recorder && this.recorder.state !== 'inactive') {
      await new Promise((resolve) => {
        this.recorder.addEventListener('stop', resolve, { once: true });
        this.recorder.stop();
      });
    }
    this.releaseTracks();
    await this.writes;
    try {
      await updateRecording(this.sessionId, {
        status: this.failed ? 'interrupted' : 'complete', durationMs, endedAt: Date.now(), markers: this.markers,
      });
      await enforceLimit(this.userId, KEEP_RECORDINGS, this.sessionId);
    } catch {
      // Nothing more can be saved; what was written already stays.
    }
  }

  releaseTracks() {
    this.stream?.getTracks().forEach((t) => t.stop());
  }

  /** Writes happen one after another, in order; a failed one (usually a full disk) ends the recording but not the interview. */
  queue(write, kind) {
    this.writes = this.writes.then(write).catch((err) => {
      if (this.failed) return;
      this.failed = true;
      this.problem(kind, err?.name === 'QuotaExceededError'
        ? "This device ran out of storage, so the recording stopped. What was saved is kept. Delete old recordings to make room."
        : 'The recording could not be saved, so it stopped. The interview can continue.');
      if (this.recorder?.state === 'recording') this.recorder.stop();
    });
  }

  problem(kind, message) {
    this.onProblem?.(kind, message);
  }
}
