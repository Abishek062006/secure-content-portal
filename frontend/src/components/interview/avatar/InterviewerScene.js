import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { JAW_DROP, MOUTH_Y, buildCharacter } from './buildCharacter';
import { LipSync } from './speech';

export function webglAvailable() {
  try {
    const canvas = document.createElement('canvas');
    return Boolean(canvas.getContext('webgl2') || canvas.getContext('webgl'));
  } catch {
    return false;
  }
}

const rand = (min, max) => min + Math.random() * (max - min);
const approach = (current, target, rate, dt) => current + (target - current) * (1 - Math.exp(-rate * dt));

/** Close fast, open a little slower: how a real blink looks. */
function blinkAmount(elapsed) {
  if (elapsed < 0) return 0;
  if (elapsed < 0.07) return (elapsed / 0.07) ** 2;
  if (elapsed < 0.1) return 1;
  if (elapsed < 0.22) return 1 - ((elapsed - 0.1) / 0.12) ** 0.8;
  return 0;
}

const LID_OPEN = -0.3;
const LID_CLOSED = 0.3;
const LOWER_LID = 0.44;

/**
 * One interviewer on screen: the character, lights, camera and everything that makes them look alive. Modes: `idle`, `speaking`
 * (mouth follows `lipSync`), `listening` (attentive, nods now and then) and `thinking` (glances away while an answer is assessed).
 */
export class InterviewerScene {
  constructor(container, look, { reducedMotion = false } = {}) {
    this.container = container;
    this.reducedMotion = reducedMotion;
    this.lipSync = new LipSync();
    this.mode = 'idle';

    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.02;
    // Soft shadows from the key light: the nose, chin and hair shading the face is most of what makes it read as solid.
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.domElement.className = 'interviewer-webgl';
    container.appendChild(this.renderer.domElement);

    this.scene = new THREE.Scene();
    this.pmrem = new THREE.PMREMGenerator(this.renderer);
    this.environment = this.pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
    this.scene.environment = this.environment;
    this.scene.environmentIntensity = 0.32;

    // A soft studio: warm key light, cool fill, and a rim light that separates hair and shoulders from the background.
    const key = new THREE.DirectionalLight('#fff0dd', 3.1);
    key.position.set(2.2, 1.6, 1.4);
    key.target.position.set(0, -0.2, 0);
    key.castShadow = true;
    key.shadow.mapSize.set(2048, 2048);
    Object.assign(key.shadow.camera, { left: -1, right: 1, top: 1, bottom: -1.4, near: 0.5, far: 7 });
    key.shadow.bias = -0.0004;
    key.shadow.normalBias = 0.012;
    key.shadow.radius = 3;
    this.scene.add(key.target);
    const fill = new THREE.DirectionalLight('#dde6ff', 0.38);
    fill.position.set(-1.8, 0.4, 1.4);
    const rim = new THREE.DirectionalLight('#ffffff', 1.5);
    rim.position.set(-0.8, 1.5, -1.9);
    const bounce = new THREE.HemisphereLight('#ffffff', '#8a7a70', 0.22);
    this.scene.add(key, fill, rim, bounce);

    this.camera = new THREE.PerspectiveCamera(27, 1, 0.1, 20);
    this.camera.position.set(0, 0.0, 3.35);
    this.camera.lookAt(0, -0.16, 0);

    this.state = {
      jaw: 0, wide: 0, round: 0, closed: 0, smile: 0, brow: 0,
      nextBlink: 0, blinkStart: -10, secondBlink: false,
      saccade: new THREE.Vector2(), saccadeTarget: new THREE.Vector2(), nextSaccade: 0,
      nod: null, nextListenNod: 0, browPulse: -10, quietSince: 0,
    };
    this.gaze = new THREE.Vector3();

    this.setLook(look);
    this.resize();
    this.observer = new ResizeObserver(() => this.resize());
    this.observer.observe(container);

    this.last = performance.now() / 1000;
    const tick = () => {
      if (this.disposed) return;
      this.frame = requestAnimationFrame(tick);
      const now = performance.now() / 1000;
      const dt = Math.min(0.05, now - this.last);
      this.last = now;
      this.animate(now, dt);
      this.renderer.render(this.scene, this.camera);
    };
    tick();
  }

  setLook(look) {
    if (this.character) {
      this.scene.remove(this.character.root);
      this.character.dispose();
    }
    this.look = look;
    this.character = buildCharacter(look);
    this.scene.add(this.character.root);
    this.state.smile = look.smile || 0.2;
  }

  setMode(mode) {
    if (mode === this.mode) return;
    this.mode = mode;
    if (mode === 'listening') this.state.nextListenNod = performance.now() / 1000 + rand(1.5, 3);
  }

  resize() {
    const { clientWidth: width, clientHeight: height } = this.container;
    if (!width || !height) return;
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    // Narrow frames (phones) pull back a little so the shoulders still fit.
    this.camera.fov = width / height < 1.1 ? 31 : 27;
    this.camera.updateProjectionMatrix();
  }

  animate(t, dt) {
    const c = this.character;
    const s = this.state;
    const motion = this.reducedMotion ? 0 : 1;
    const mode = this.mode;

    // Breathing.
    const breath = Math.sin(t * 1.35);
    c.torso.scale.y = 1 + 0.005 * breath * motion;
    c.root.position.y = 0.002 * breath * motion;

    // The mouth, from the speech timeline.
    const shape = mode === 'speaking' ? this.lipSync.sample(t) : { jaw: 0, wide: 0, round: 0, closed: 0 };
    s.jaw = approach(s.jaw, shape.jaw, 24, dt);
    s.wide = approach(s.wide, shape.wide, 20, dt);
    s.round = approach(s.round, shape.round, 20, dt);
    s.closed = approach(s.closed, shape.closed, 26, dt);

    // A new phrase after a pause: people lift their brows and move their head a little as they start talking.
    const talking = shape.jaw + shape.wide + shape.round + shape.closed > 0.05;
    if (!talking) {
      if (!s.quietSince) s.quietSince = t;
    } else {
      if (s.quietSince && t - s.quietSince > 0.35) {
        s.browPulse = t;
        if (Math.random() < 0.45) s.nod = { start: t, duration: 0.55, amount: rand(0.018, 0.035) };
      }
      s.quietSince = 0;
    }

    // Head: slow drift, plus nods.
    let pitch = motion * (0.022 * Math.sin(t * 0.41) + 0.01 * Math.sin(t * 1.17 + 0.6));
    let yaw = motion * (0.04 * Math.sin(t * 0.27 + 1.1) + 0.014 * Math.sin(t * 0.93));
    let roll = motion * 0.014 * Math.sin(t * 0.33 + 2.3);
    if (mode === 'listening') {
      roll += 0.035 * motion;
      if (t > s.nextListenNod) {
        s.nod = { start: t, duration: 0.75, amount: 0.05 };
        s.nextListenNod = t + rand(3.5, 6.5);
      }
    } else if (mode === 'thinking') {
      roll -= 0.03 * motion;
      pitch -= 0.025 * motion;
    }
    if (s.nod) {
      const k = (t - s.nod.start) / s.nod.duration;
      if (k >= 1) s.nod = null;
      else pitch += motion * s.nod.amount * Math.sin(Math.PI * k);
    }
    c.headPivot.rotation.set(pitch, yaw, roll);

    // Expression.
    const baseSmile = this.look.smile || 0.2;
    const smileTarget = mode === 'speaking' ? baseSmile * 0.55 : mode === 'listening' ? baseSmile + 0.12 : baseSmile;
    s.smile = approach(s.smile, smileTarget, 3.5, dt);
    const pulse = Math.max(0, 1 - Math.abs(t - s.browPulse - 0.25) / 0.35);
    const browTarget = (mode === 'thinking' ? 0.45 : 0) + 0.55 * pulse;
    s.brow = approach(s.brow, browTarget, 9, dt);

    c.face.morphTargetInfluences[0] = s.jaw;
    c.face.morphTargetInfluences[1] = s.smile;
    c.face.morphTargetInfluences[2] = s.brow;
    if (c.beard) {
      c.beard.morphTargetInfluences[0] = s.jaw;
      c.beard.morphTargetInfluences[1] = s.smile;
    }
    c.brows.position.y = 0.016 * s.brow;

    const m = c.mouth;
    const drop = JAW_DROP * s.jaw * m.lowerLipWeight;
    const widthScale = 1 + 0.16 * s.wide - 0.3 * s.round + 0.05 * s.smile;
    const heightScale = 1 + 0.22 * s.round - 0.1 * s.closed;
    m.upperLip.position.set(m.upperBase.x, m.upperBase.y - 0.002 * s.closed, m.upperBase.z + 0.012 * s.round);
    m.upperLip.scale.set(widthScale, heightScale, 1);
    m.lowerLip.position.set(m.lowerBase.x, m.lowerBase.y - drop + 0.003 * s.closed, m.lowerBase.z + 0.012 * s.round - 0.3 * drop);
    m.lowerLip.scale.set(widthScale * (1 - 0.06 * s.jaw), heightScale, 1);
    m.inside.position.y = MOUTH_Y - drop / 2 - 0.001;
    m.inside.scale.set(m.lipWidth * 0.86 * widthScale, Math.max(0.0005, drop / 2 + 0.002), 1);
    m.inside.visible = drop > 0.002;
    m.teeth.visible = drop > 0.006;
    m.teeth.scale.x = widthScale * 0.95;

    // Blinks: every few seconds, sometimes twice.
    if (t > s.nextBlink) {
      s.blinkStart = t;
      s.secondBlink = Math.random() < 0.12;
      s.nextBlink = t + rand(2.4, 5.5) * (mode === 'speaking' ? 0.8 : 1);
    }
    if (s.secondBlink && t - s.blinkStart > 0.32) {
      s.blinkStart = t;
      s.secondBlink = false;
    }
    const blink = blinkAmount(t - s.blinkStart);

    // Eyes: on the viewer, with the small quick glances real eyes make, and away when thinking.
    if (t > s.nextSaccade) {
      s.saccadeTarget.set(rand(-1, 1), rand(-0.6, 0.6));
      s.nextSaccade = t + rand(0.7, 2.4);
    }
    s.saccade.x = approach(s.saccade.x, s.saccadeTarget.x, 35, dt);
    s.saccade.y = approach(s.saccade.y, s.saccadeTarget.y, 35, dt);
    this.gaze.copy(this.camera.position);
    this.gaze.x += s.saccade.x * 0.07;
    this.gaze.y += s.saccade.y * 0.05 - 0.02;
    if (mode === 'thinking') {
      this.gaze.x -= 0.9;
      this.gaze.y += 0.55;
    }
    c.root.updateMatrixWorld(true);
    let eyePitch = 0;
    for (const eye of c.eyes) {
      eye.lookAt(this.gaze);
      eyePitch = eye.rotation.x;
    }
    // Upper lids follow where the eyes look, as real ones do.
    const follow = Math.max(-0.3, Math.min(0.3, eyePitch)) * 0.6;
    for (const lid of c.upperLids) {
      lid.rotation.x = THREE.MathUtils.lerp(LID_OPEN + follow - 0.04 * s.brow, LID_CLOSED, blink);
    }
    for (const lid of c.lowerLids) {
      lid.rotation.x = LOWER_LID - 0.12 * s.smile - 0.06 * blink;
    }
  }

  dispose() {
    this.disposed = true;
    cancelAnimationFrame(this.frame);
    this.observer.disconnect();
    this.lipSync.clear();
    if (this.character) this.character.dispose();
    this.environment.dispose();
    this.pmrem.dispose();
    this.renderer.dispose();
    this.renderer.forceContextLoss();
    this.renderer.domElement.remove();
  }
}
