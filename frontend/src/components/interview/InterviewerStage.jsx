import { useEffect, useRef, useState } from 'react';
import Icon from '../Icon';
import { InterviewerScene, webglAvailable } from './avatar/InterviewerScene';
import { personaById } from './avatar/personas';
import { LipSync, loadVoices, pickVoice, speak, speechAvailable } from './avatar/speech';

const prefersReducedMotion = () => typeof window !== 'undefined' && Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);

/**
 * The interviewer on a video-call style stage: the 3D character, their name, captions of what they're saying, and replay and mute.
 * Whenever `line.id` changes, `line.text` is spoken (by the browser; no audio leaves the device) with the lips moving along.
 * `hush` stops them mid-sentence, for when the learner starts recording an answer.
 */
export default function InterviewerStage({
  personaId, line, mode = 'idle', muted = false, hush = false, onToggleMute, onSpeakingChange, compact = false,
}) {
  const persona = personaById(personaId);
  const holder = useRef(null);
  const scene = useRef(null);
  const spareLipSync = useRef(new LipSync());
  const voice = useRef(null);
  const handle = useRef(null);
  const [supported] = useState(webglAvailable);
  const [speaking, setSpeaking] = useState(false);
  const [caption, setCaption] = useState('');
  const [blocked, setBlocked] = useState(false);

  useEffect(() => {
    if (!supported || !holder.current) return undefined;
    scene.current = new InterviewerScene(holder.current, persona.look, { reducedMotion: prefersReducedMotion() });
    return () => {
      handle.current?.cancel();
      scene.current?.dispose();
      scene.current = null;
    };
  }, [supported]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    scene.current?.setLook(persona.look);
    voice.current = null;
    let live = true;
    loadVoices().then((voices) => {
      if (live) voice.current = pickVoice(voices, persona.voice);
    });
    return () => {
      live = false;
    };
  }, [persona]);

  useEffect(() => {
    scene.current?.setMode(speaking ? 'speaking' : mode);
  }, [mode, speaking]);

  function stopped() {
    setSpeaking(false);
    onSpeakingChange?.(false);
  }

  function say(text) {
    handle.current?.cancel();
    setBlocked(false);
    setCaption(text);
    const lipSync = scene.current?.lipSync || spareLipSync.current;
    const go = () => {
      handle.current = speak(text, {
        voice: voice.current, prefs: persona.voice, muted, lipSync,
        onStart: () => {
          setSpeaking(true);
          onSpeakingChange?.(true);
        },
        onEnd: stopped,
        onBlocked: () => {
          stopped();
          setBlocked(true);
        },
      });
    };
    // The first line can arrive before the browser has listed its voices.
    if (voice.current || muted || !speechAvailable()) go();
    else loadVoices().then((voices) => {
      voice.current = pickVoice(voices, persona.voice);
      go();
    });
  }

  useEffect(() => {
    if (line?.text) say(line.text);
  }, [line?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // Muting, or the learner starting to speak, stops the interviewer at once.
  useEffect(() => {
    if (hush || muted) {
      handle.current?.cancel();
      stopped();
    }
  }, [hush, muted]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className={`interviewer-stage${compact ? ' compact' : ''}`}>
      <div className="interviewer-view" ref={holder}>
        {!supported && <div className="interviewer-fallback" aria-hidden="true">{persona.name[0]}</div>}
      </div>
      <div className="interviewer-bar">
        <span className="interviewer-name">
          <strong>{persona.name}</strong>
          <span>{persona.role}</span>
          {speaking && <span className="interviewer-speaking" aria-hidden="true"><i /><i /><i /></span>}
        </span>
        {!compact && (
          <span className="interviewer-controls">
            <button type="button" onClick={() => line?.text && say(line.text)} disabled={!line?.text || hush}
                    aria-label="Hear that again" title="Hear that again">
              <Icon name="rotate-ccw" size={16} />
            </button>
            <button type="button" onClick={onToggleMute} aria-pressed={muted}
                    aria-label={muted ? 'Turn the voice on' : 'Mute the voice'} title={muted ? 'Turn the voice on' : 'Mute the voice'}>
              <Icon name={muted ? 'volume-x' : 'volume-2'} size={16} />
            </button>
          </span>
        )}
      </div>
      {caption && !compact && <p className={`interviewer-caption${speaking ? '' : ' done'}`} aria-live="polite">{caption}</p>}
      {blocked && line?.text && (
        <button type="button" className="btn btn-primary interviewer-play" onClick={() => say(line.text)}>
          <Icon name="play" size={16} /> Hear {persona.name}
        </button>
      )}
    </div>
  );
}
