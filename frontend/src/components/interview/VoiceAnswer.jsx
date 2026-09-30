import { useEffect, useRef, useState } from 'react';
import { api } from '../../api';
import Icon from '../Icon';

const CONSENT_KEY = 'gn-voice-consent';
const MAX_SECONDS = 170;
const TYPES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus'];

function supported() {
  return typeof window !== 'undefined' && Boolean(navigator.mediaDevices?.getUserMedia) && typeof window.MediaRecorder !== 'undefined';
}

function readConsent() {
  try { return localStorage.getItem(CONSENT_KEY) === 'yes'; } catch { return false; }
}

const clock = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

/**
 * Record an answer instead of typing it. The transcript is put in the answer box for the learner to read and edit; the recording
 * itself is sent once for transcription and never kept.
 */
export default function VoiceAnswer({ sessionId, onText, onError, disabled, onRecordingChange }) {
  const [state, setState] = useState('idle'); // idle | consent | recording | transcribing
  const [seconds, setSeconds] = useState(0);
  const [notes, setNotes] = useState(null);
  const recorder = useRef(null);
  const stream = useRef(null);
  const chunks = useRef([]);
  const cancelled = useRef(false);
  const timer = useRef(null);
  const startedAt = useRef(0);

  useEffect(() => () => { stopEverything(); }, []);
  // Lets the interviewer stop talking and listen while the learner records.
  useEffect(() => { onRecordingChange?.(state === 'recording'); }, [state]); // eslint-disable-line react-hooks/exhaustive-deps

  function stopEverything() {
    clearInterval(timer.current);
    if (recorder.current && recorder.current.state !== 'inactive') {
      cancelled.current = true;
      recorder.current.stop();
    }
    stream.current?.getTracks().forEach((t) => t.stop());
  }

  if (!supported()) {
    return <p className="field-hint">Voice answers need a recent Chrome, Edge, Firefox or Safari. You can type your answer instead.</p>;
  }

  async function begin() {
    onError(null);
    setNotes(null);
    try {
      // Clean, single-channel audio with the room noise and echo removed: the biggest single help to what gets heard correctly.
      stream.current = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
    } catch {
      onError('Microphone access was blocked. Allow it in your browser, or type your answer instead.');
      setState('idle');
      return;
    }
    const type = TYPES.find((t) => window.MediaRecorder.isTypeSupported?.(t));
    const options = { audioBitsPerSecond: 128000, ...(type ? { mimeType: type } : {}) };
    recorder.current = new MediaRecorder(stream.current, options);
    chunks.current = [];
    cancelled.current = false;
    recorder.current.ondataavailable = (e) => { if (e.data.size) chunks.current.push(e.data); };
    recorder.current.onstop = finish;
    recorder.current.start();
    startedAt.current = Date.now();
    setSeconds(0);
    setState('recording');
    timer.current = setInterval(() => {
      const elapsed = Math.round((Date.now() - startedAt.current) / 1000);
      setSeconds(elapsed);
      if (elapsed >= MAX_SECONDS) stop();
    }, 500);
  }

  function stop() {
    clearInterval(timer.current);
    if (recorder.current && recorder.current.state !== 'inactive') recorder.current.stop();
  }

  function cancel() {
    stopEverything();
    setState('idle');
  }

  async function finish() {
    stream.current?.getTracks().forEach((t) => t.stop());
    if (cancelled.current) return;
    const length = Math.round((Date.now() - startedAt.current) / 1000);
    const blob = new Blob(chunks.current, { type: recorder.current?.mimeType || 'audio/webm' });
    setState('transcribing');
    try {
      const result = await api.transcribeAnswer(blob, length, sessionId);
      onText(result.text);
      setNotes(result);
    } catch (err) {
      onError(err.message);
    } finally {
      setState('idle');
    }
  }

  function click() {
    if (state === 'recording') stop();
    else if (readConsent()) begin();
    else setState('consent');
  }

  function agree() {
    try { localStorage.setItem(CONSENT_KEY, 'yes'); } catch { /* the notice will simply show again next time */ }
    begin();
  }

  return (
    <div className="voice">
      {state === 'consent' && (
        <div className="voice-consent">
          <p>
            When you record an answer, the audio is sent to our speech-to-text service to be turned into words, then discarded. We keep
            only the text you choose to submit.
          </p>
          <button type="button" className="btn btn-primary btn-sm" onClick={agree}>Allow and record</button>
          <button type="button" className="btn btn-sm" onClick={() => setState('idle')}>Not now</button>
        </div>
      )}
      {state !== 'consent' && (
        <div className="voice-controls">
          <button type="button" className={`btn voice-button${state === 'recording' ? ' recording' : ''}`} onClick={click}
                  disabled={disabled || state === 'transcribing'}>
            <Icon name={state === 'recording' ? 'square' : 'mic'} size={16} />
            {state === 'recording' ? 'Stop' : state === 'transcribing' ? 'Turning your voice into text...' : 'Answer by voice'}
          </button>
          {state === 'recording' && (
            <>
              <span className="voice-clock" aria-live="off">{clock(seconds)}</span>
              <button type="button" className="link-button" onClick={cancel}>Cancel</button>
            </>
          )}
        </div>
      )}
      {notes && (
        <p className="field-hint voice-notes">
          Check the words below and fix anything that was misheard. {notes.words} words
          {notes.wordsPerMinute ? `, about ${notes.wordsPerMinute} a minute` : ''}
          {notes.fillerWords ? `, ${notes.fillerWords} filler word${notes.fillerWords === 1 ? '' : 's'} (um, you know, basically)` : ''}.
        </p>
      )}
    </div>
  );
}
