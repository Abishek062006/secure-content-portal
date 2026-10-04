import { detectFace, loadFaceDetector } from './faceDetector';
import { judgeFraming, judgeLighting, lumaStats } from './frameChecks';

const SIZE = { width: 160, height: 120 };

/**
 * Watches the camera while the learner is in front of it: once every interval it looks at one small frame, judges the lighting, and
 * (when the face detector is ready) whether a face is in frame and how it is placed. It keeps counts so the share of the time
 * the face was in frame and the lighting good can be reported afterwards. Nothing is stored or sent from here but those counts.
 */
export class FrameMonitor {
  constructor(stream, { intervalMs = 1000, onUpdate = null } = {}) {
    this.stream = stream;
    this.intervalMs = intervalMs;
    this.onUpdate = onUpdate;
    this.video = null;
    this.canvas = null;
    this.timer = null;
    this.detector = null;
    this.faceCheck = 'loading'; // loading | ready | unavailable
    this.samples = 0;
    this.lightingSamples = 0;
    this.lightingGood = 0;
    this.faceSamples = 0;
    this.faceSeen = 0;
    this.faceMissingRun = 0;
    this.lightingBadRun = 0;
    this.state = this.snapshot(null, null);
  }

  async start() {
    const tracks = this.stream.getVideoTracks();
    if (tracks.length === 0) return;
    // Kept in the page (invisible) rather than loose in memory: some browsers won't play a video that isn't in the document.
    this.video = document.createElement('video');
    Object.assign(this.video.style, { position: 'fixed', width: '1px', height: '1px', opacity: '0', pointerEvents: 'none', left: '0', top: '0' });
    this.video.muted = true;
    this.video.playsInline = true;
    this.video.setAttribute('aria-hidden', 'true');
    this.video.srcObject = new MediaStream(tracks);
    document.body.appendChild(this.video);
    this.canvas = document.createElement('canvas');
    this.canvas.width = SIZE.width;
    this.canvas.height = SIZE.height;
    try {
      await this.video.play();
    } catch {
      /* a frame can still arrive; sampling waits for one */
    }

    loadFaceDetector()
      .then((detector) => { this.detector = detector; this.faceCheck = 'ready'; })
      .catch(() => { this.faceCheck = 'unavailable'; });
    this.timer = setInterval(() => this.sample(), this.intervalMs);
  }

  snapshot(lighting, framing) {
    return {
      lighting, framing, faceCheck: this.faceCheck,
      faceMissingSeconds: (this.faceMissingRun * this.intervalMs) / 1000,
      lightingBadSeconds: (this.lightingBadRun * this.intervalMs) / 1000,
    };
  }

  sample() {
    // A tab in the background gets no fresh pictures and would count as "face missing".
    if (document.visibilityState !== 'visible' || !this.video || this.video.readyState < 2) return;
    const context = this.canvas.getContext('2d', { willReadFrequently: true });
    context.drawImage(this.video, 0, 0, SIZE.width, SIZE.height);

    let box = null;
    let detected = false;
    if (this.detector) {
      try {
        box = detectFace(this.detector, this.video);
        detected = true;
      } catch {
        this.faceCheck = 'unavailable';
        this.detector = null;
      }
    }
    const pixels = context.getImageData(0, 0, SIZE.width, SIZE.height).data;
    const lighting = judgeLighting(lumaStats(pixels, SIZE.width, SIZE.height, box));
    const framing = detected ? judgeFraming(box) : null;

    this.samples++;
    if (lighting) {
      this.lightingSamples++;
      if (lighting === 'ok') this.lightingGood++;
      this.lightingBadRun = lighting === 'ok' ? 0 : this.lightingBadRun + 1;
    }
    if (detected) {
      this.faceSamples++;
      if (box) this.faceSeen++;
      this.faceMissingRun = box ? 0 : this.faceMissingRun + 1;
    }
    this.state = this.snapshot(lighting, framing);
    this.onUpdate?.(this.state);
  }

  /** The share of readings with the face in frame and with good lighting; null for whichever couldn't be read. */
  summary() {
    return {
      faceVisiblePercent: this.faceSamples > 0 ? (100 * this.faceSeen) / this.faceSamples : null,
      lightingGoodPercent: this.lightingSamples > 0 ? (100 * this.lightingGood) / this.lightingSamples : null,
      samples: this.samples,
    };
  }

  stop() {
    clearInterval(this.timer);
    this.timer = null;
    this.video?.pause();
    if (this.video) {
      this.video.srcObject = null;
      this.video.remove();
    }
    this.video = null;
    this.onUpdate = null;
  }
}
