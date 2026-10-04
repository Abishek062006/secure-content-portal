const WINDOW_MS = 5000;
const INTERVAL_MS = 100;

/**
 * A live reading of the microphone's loudness, for the setup check: the level now, and how the loudest moment of the last few
 * seconds compares with the quietest, which tells "the microphone hears you" apart from "the microphone is on". Only listened to:
 * it is never played or recorded.
 */
export class MicMeter {
  constructor(stream, onLevel) {
    this.stream = stream;
    this.onLevel = onLevel;
    this.timer = null;
    this.context = null;
    this.recent = [];
  }

  start() {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass || this.stream.getAudioTracks().length === 0) return;
    try {
      this.context = new AudioContextClass();
      this.context.resume?.();
      const analyser = this.context.createAnalyser();
      analyser.fftSize = 1024;
      this.context.createMediaStreamSource(new MediaStream(this.stream.getAudioTracks())).connect(analyser);
      const samples = new Float32Array(analyser.fftSize);
      this.timer = setInterval(() => {
        analyser.getFloatTimeDomainData(samples);
        let sum = 0;
        for (let i = 0; i < samples.length; i++) sum += samples[i] * samples[i];
        const db = 20 * Math.log10(Math.max(Math.sqrt(sum / samples.length), 1e-8));
        const now = performance.now();
        this.recent.push({ t: now, db });
        this.recent = this.recent.filter((r) => now - r.t <= WINDOW_MS);
        const levels = this.recent.map((r) => r.db).sort((a, b) => a - b);
        this.onLevel({ db, floorDb: levels[Math.floor(levels.length * 0.1)], peakDb: levels[levels.length - 1] });
      }, INTERVAL_MS);
    } catch {
      this.stop();
    }
  }

  stop() {
    clearInterval(this.timer);
    this.timer = null;
    this.context?.close?.().catch(() => {});
    this.context = null;
  }
}
