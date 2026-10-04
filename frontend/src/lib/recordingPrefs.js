const KEY = 'gn-record-interviews';

/** Whether this browser can record an interview: it needs a camera and microphone API, a recorder and somewhere to keep the video. */
export function recordingSupported() {
  return typeof window !== 'undefined' && typeof indexedDB !== 'undefined' && Boolean(navigator.mediaDevices?.getUserMedia)
    && typeof window.MediaRecorder !== 'undefined';
}

/** Off unless the learner turned it on. Even then the interview asks again before the camera starts. */
export function wantsRecording() {
  try { return localStorage.getItem(KEY) === 'yes'; } catch { return false; }
}

export function setWantsRecording(on) {
  try { localStorage.setItem(KEY, on ? 'yes' : 'no'); } catch { /* the choice just won't be remembered */ }
}
