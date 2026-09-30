/**
 * The interviewer's voice, using the browser's own speech (no audio leaves the device), and the mouth shapes that go with it.
 *
 * Browsers report when each word starts but not the sound itself, so the mouth is driven from the letters of the word being spoken:
 * open vowels open the jaw, "o" and "u" round the lips, "m", "b" and "p" close them. Voices that don't report word timings, and
 * muted playback, get the same shapes on an estimated rhythm instead, so the face always moves with the captions.
 */

const QUALITY = /natural|neural|premium|enhanced|online/i;
/** Novelty and old-style voices some systems ship: never what a real interviewer sounds like. */
const ROBOTIC = /\b(eddy|flo|grandma|grandpa|reed|rocko|sandy|shelley|albert|bad news|bahh|bells|boing|bubbles|cellos|good news|jester|junior|kathy|organ|ralph|superstar|trinoids|whisper|wobble|zarvox|fred)\b/i;
const FEMALE = ['female', 'woman', 'samantha', 'karen', 'moira', 'tessa', 'veena', 'neerja', 'heera', 'kalpana', 'swara', 'aria', 'jenny',
  'ava', 'allison', 'zoe', 'serena', 'sonia', 'natasha', 'catherine', 'libby', 'kate', 'susan', 'victoria', 'fiona', 'zira', 'emma',
  'joanna', 'salli', 'kimberly', 'olivia', 'amy', 'martha', 'nicky', 'isha', 'lekha'];
const MALE = ['daniel', 'alex', 'fred', 'rishi', 'prabhat', 'ravi', 'ryan', 'guy', 'arthur', 'oliver', 'david', 'mark', 'george', 'thomas',
  'aaron', 'james', 'william', 'christopher', 'eric', 'tom', 'rocko', 'lee', 'aarav'];

export function speechAvailable() {
  return typeof window !== 'undefined' && 'speechSynthesis' in window && typeof window.SpeechSynthesisUtterance !== 'undefined';
}

/** The browser's voices; some browsers only list them after a moment. */
export function loadVoices() {
  if (!speechAvailable()) return Promise.resolve([]);
  const now = window.speechSynthesis.getVoices();
  if (now.length) return Promise.resolve(now);
  return new Promise((resolve) => {
    let settled = false;
    const done = () => {
      if (settled) return;
      settled = true;
      resolve(window.speechSynthesis.getVoices());
    };
    window.speechSynthesis.addEventListener('voiceschanged', done, { once: true });
    setTimeout(done, 1500);
  });
}

function genderOf(name) {
  const lower = name.toLowerCase();
  if (FEMALE.some((n) => lower.includes(n))) return 'f';
  if (/\bmale\b/.test(lower) || MALE.some((n) => lower.includes(n))) return 'm';
  return null;
}

/** The best voice on this device for the persona: one they'd sound like, in English, preferring the natural-sounding ones. */
export function pickVoice(voices, prefs) {
  let best = null;
  let bestScore = -Infinity;
  for (const voice of voices) {
    const name = voice.name.toLowerCase();
    const lang = (voice.lang || '').replace('_', '-');
    if (!lang.toLowerCase().startsWith('en')) continue;
    let score = 0;
    const named = prefs.names.findIndex((n) => name.includes(n.toLowerCase()));
    if (named >= 0) score += 30 - named;
    const langIndex = prefs.langs.findIndex((l) => l.toLowerCase() === lang.toLowerCase());
    if (langIndex >= 0) score += 8 - langIndex * 2;
    if (QUALITY.test(voice.name)) score += 5;
    if (ROBOTIC.test(voice.name)) score -= 60;
    const gender = genderOf(voice.name);
    if (gender) score += gender === (prefs.female ? 'f' : 'm') ? 6 : -40;
    if (voice.localService) score += 1;
    if (score > bestScore) {
      best = voice;
      bestScore = score;
    }
  }
  return best;
}

// ---- Mouth shapes ------------------------------------------------------------------------------------------------------

const REST = { jaw: 0, wide: 0, round: 0, closed: 0 };

function shapeFor(ch, next) {
  switch (ch) {
    case 'a': return { jaw: 0.62, wide: 0.3, round: 0, closed: 0 };
    case 'e': return { jaw: 0.42, wide: 0.55, round: 0, closed: 0 };
    case 'i': case 'y': return { jaw: 0.26, wide: 0.72, round: 0, closed: 0 };
    case 'o': return { jaw: 0.5, wide: 0, round: 0.78, closed: 0 };
    case 'u': return { jaw: 0.26, wide: 0, round: 0.92, closed: 0 };
    case 'w': return { jaw: 0.12, wide: 0, round: 0.72, closed: 0 };
    case 'm': case 'b': case 'p': return { jaw: 0, wide: 0.05, round: 0, closed: 1 };
    case 'f': case 'v': return { jaw: 0.08, wide: 0.15, round: 0, closed: 0.55 };
    case 't': return next === 'h' ? { jaw: 0.16, wide: 0.2, round: 0, closed: 0 } : { jaw: 0.18, wide: 0.25, round: 0, closed: 0 };
    case 's': case 'z': case 'c': case 'x': case 'j': return { jaw: 0.1, wide: 0.38, round: 0, closed: 0 };
    case 'r': return { jaw: 0.2, wide: 0, round: 0.35, closed: 0 };
    case 'l': case 'n': case 'd': return { jaw: 0.22, wide: 0.2, round: 0, closed: 0 };
    case 'k': case 'g': case 'q': case 'h': return { jaw: 0.3, wide: 0.1, round: 0, closed: 0 };
    default: return null;
  }
}

const VOWEL = /[aeiouy]/;

/** A timeline of mouth shapes; the renderer samples it every frame. */
export class LipSync {
  constructor() {
    this.frames = [];
  }

  clear() {
    this.frames = [];
  }

  /** Lays out one word starting at `at` (seconds, performance.now based) and returns when it ends. */
  pushWord(word, at, rate = 1) {
    const letters = word.toLowerCase().replace(/[^a-z]/g, '');
    if (!letters) return at;
    const total = Math.min(0.7, Math.max(0.14, (0.062 * letters.length) / rate));
    const shapes = [];
    for (let i = 0; i < letters.length; i++) {
      const shape = shapeFor(letters[i], letters[i + 1]);
      if (!shape) continue;
      const previous = shapes[shapes.length - 1];
      // Runs of consonants blur together when spoken; only the vowels and lip closures each need their own moment.
      if (previous && !VOWEL.test(letters[i]) && !VOWEL.test(previous.ch) && !shape.closed) continue;
      shapes.push({ ch: letters[i], shape, weight: VOWEL.test(letters[i]) ? 1.5 : 1 });
    }
    const weights = shapes.reduce((sum, s) => sum + s.weight, 0) || 1;
    let t = at;
    for (const s of shapes) {
      this.frames.push({ t, ...s.shape });
      t += (total * s.weight) / weights;
    }
    this.frames.push({ t: at + total, ...REST });
    // Keep the timeline short: anything more than a few seconds old will never be sampled again.
    const cutoff = at - 3;
    if (this.frames.length > 200) this.frames = this.frames.filter((f) => f.t >= cutoff);
    return at + total;
  }

  /** Lays out a whole line on an estimated rhythm, for voices without word timings and for muted playback. */
  pushLine(text, at, rate = 1) {
    let t = at;
    for (const word of text.split(/\s+/)) {
      if (!word) continue;
      t = this.pushWord(word, t, rate) + 0.07 / rate;
      if (/[,;:]$/.test(word)) t += 0.18 / rate;
      if (/[.!?]$/.test(word)) t += 0.34 / rate;
    }
    return t;
  }

  /** The shape due now: the latest frame at or before `now` (word timings and estimates can arrive out of order). */
  sample(now) {
    let current = REST;
    let latest = -Infinity;
    for (const frame of this.frames) {
      if (frame.t <= now && frame.t >= latest) {
        current = frame;
        latest = frame.t;
      }
    }
    return current;
  }
}

/** Sentences, so long lines don't hit the cut-off some browsers apply to a single long utterance. */
function sentences(text) {
  const parts = text.match(/[^.!?]+[.!?]*\s*/g) || [text];
  let offset = 0;
  return parts.map((part) => {
    const piece = { text: part, offset };
    offset += part.length;
    return piece;
  }).filter((p) => p.text.trim());
}

const seconds = () => performance.now() / 1000;

/**
 * Says `text` in the persona's voice and drives `lipSync` along with it. Returns a handle whose `cancel()` stops both. `onBlocked`
 * is called when the browser refuses to speak before the learner has interacted with the page, so a play button can be shown.
 */
export function speak(text, { voice, prefs, muted, lipSync, onStart, onEnd, onBlocked }) {
  const handle = { cancelled: false, cancel() {} };
  const finish = () => {
    if (handle.cancelled) return;
    handle.cancelled = true;
    onEnd?.();
  };

  if (muted || !speechAvailable()) {
    // Silent: the face still talks, in time with the captions.
    lipSync.clear();
    const end = lipSync.pushLine(text, seconds() + 0.05, prefs.rate);
    onStart?.();
    const timer = setTimeout(finish, (end - seconds()) * 1000 + 150);
    handle.cancel = () => {
      clearTimeout(timer);
      handle.cancelled = true;
      lipSync.clear();
    };
    return handle;
  }

  const synth = window.speechSynthesis;
  synth.cancel();
  lipSync.clear();
  let gotWordTimings = false;
  let fallbackTimer = null;
  let started = false;
  const parts = sentences(text);

  parts.forEach((part, index) => {
    const utterance = new window.SpeechSynthesisUtterance(part.text);
    if (voice) {
      utterance.voice = voice;
      utterance.lang = voice.lang;
    }
    utterance.rate = prefs.rate;
    // A persona whose preferred voice wasn't found keeps a hint of their own pitch on whatever voice is used instead.
    utterance.pitch = prefs.pitch;
    utterance.onstart = () => {
      if (handle.cancelled) return;
      if (!started) {
        started = true;
        onStart?.();
      }
      // Voices that never report words: fall back to the estimated rhythm for this sentence.
      clearTimeout(fallbackTimer);
      fallbackTimer = setTimeout(() => {
        if (!gotWordTimings && !handle.cancelled) lipSync.pushLine(part.text, seconds(), prefs.rate);
      }, 350);
    };
    utterance.onboundary = (event) => {
      if (handle.cancelled || (event.name && event.name !== 'word')) return;
      gotWordTimings = true;
      const from = event.charIndex;
      const length = event.charLength || (part.text.slice(from).match(/^\S+/) || [''])[0].length;
      lipSync.pushWord(part.text.slice(from, from + length), seconds(), prefs.rate);
    };
    utterance.onend = () => {
      if (index === parts.length - 1) {
        clearTimeout(fallbackTimer);
        lipSync.clear();
        finish();
      }
    };
    utterance.onerror = (event) => {
      clearTimeout(fallbackTimer);
      if (event.error === 'not-allowed' && !handle.cancelled) {
        handle.cancelled = true;
        synth.cancel();
        lipSync.clear();
        onBlocked?.();
        return;
      }
      if (event.error !== 'interrupted' && event.error !== 'canceled' && index === parts.length - 1) finish();
    };
    synth.speak(utterance);
  });

  handle.cancel = () => {
    handle.cancelled = true;
    clearTimeout(fallbackTimer);
    synth.cancel();
    lipSync.clear();
  };
  return handle;
}
