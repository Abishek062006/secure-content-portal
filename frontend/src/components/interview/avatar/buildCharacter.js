import * as THREE from 'three';

/**
 * Builds an interviewer from code: a sculpted head (a sphere reshaped into skull, jaw, brow, cheekbones, nose, lips and chin), real
 * eyes (sclera, a painted iris under a clear cornea), lids that blink, lashes, brows, hair, clothing and a few accessories.
 *
 * Units: the head is about one unit tall and centred on the origin, facing +z. Each look can vary the face's proportions so no two
 * people share one face. The face mesh carries three morph targets (jaw open, smile, brow raise) that the animator drives.
 */

// Proportions follow the usual rule of thirds: hairline, brow, base of the nose and chin evenly spaced, the eyes at half the
// head's height, the mouth a third of the way from nose to chin. One unit is roughly 23 cm, the height of a real head.
const W = 0.37;
const H = 0.49;
const D = 0.45;
export const EYE_X = 0.13;
export const EYE_Y = 0.01;
const NOSE_Y = -0.18;
export const MOUTH_Y = -0.29;
const COLS = 200;
const ROWS = 176;
export const JAW_DROP = 0.056;

const clamp01 = (v) => Math.min(1, Math.max(0, v));
function smooth(e0, e1, x) {
  const t = clamp01((x - e0) / (e1 - e0));
  return t * t * (3 - 2 * t);
}
function gauss(dx, dy, sx, sy) {
  return Math.exp(-(dx * dx) / (2 * sx * sx) - (dy * dy) / (2 * sy * sy));
}
/** The same bump on both sides of the face. Summing two keeps the centre line smooth, where gauss(|x| - c) would crease it. */
function pair(x, c, dy, sx, sy) {
  return gauss(x - c, dy, sx, sy) + gauss(x + c, dy, sx, sy);
}
/** Repeatable noise, so a hairline has the same irregular edge every time the same person is built. */
function hash(a, b) {
  const s = Math.sin(a * 127.1 + b * 311.7) * 43758.5453;
  return s - Math.floor(s);
}

function direction(u, v) {
  const theta = v * Math.PI;
  const phi = u * Math.PI * 2 - Math.PI / 2;
  return [-Math.cos(phi) * Math.sin(theta), Math.cos(theta), Math.sin(phi) * Math.sin(theta)];
}

function features(x, y, f) {
  let d = 0;
  d -= 0.05 * pair(x, EYE_X, y - EYE_Y, 0.06, 0.038); // eye sockets
  d += 0.02 * f.ridge * pair(x, 0.12, y - (EYE_Y + 0.062), 0.075, 0.022); // brow ridge
  d += 0.01 * gauss(x, y - (EYE_Y + 0.05), 0.035, 0.03); // between the brows
  d += 0.024 * pair(x, 0.18, y - (EYE_Y - 0.09), 0.06, 0.042); // cheekbones
  d -= 0.01 * pair(x, 0.17, y - (EYE_Y - 0.2), 0.05, 0.05); // soft hollow under them

  // Nose: a ridge from the bridge (starting between the eyes, not up the forehead) to the tip, a rounded tip, and the wings.
  const bridge = smooth(EYE_Y + 0.065, EYE_Y + 0.02, y);
  const rise = smooth(EYE_Y + 0.02, NOSE_Y + 0.015, y);
  const cut = 1 - smooth(NOSE_Y - 0.005, NOSE_Y - 0.043, y);
  const width = (0.018 + 0.021 * rise) * f.nose;
  d += (0.016 * bridge + 0.08 * rise ** 1.25) * cut * Math.exp(-(x * x) / (2 * width * width));
  d += 0.02 * gauss(x, y - (NOSE_Y + 0.003), 0.022 * f.nose, 0.018);
  d += 0.03 * pair(x, 0.034 * f.nose, y - (NOSE_Y - 0.017), 0.017, 0.014);

  d += 0.012 * gauss(x, y - (MOUTH_Y + 0.055), 0.02, 0.025); // philtrum
  d += 0.018 * gauss(x, y - MOUTH_Y, 0.085, 0.05); // the rounded area around the mouth
  d -= 0.008 * gauss(x, y - (MOUTH_Y - 0.06), 0.05, 0.018); // the dip under the lower lip
  d += 0.024 * f.chin * gauss(x, y - (MOUTH_Y - 0.125), 0.07 * f.jaw, 0.045); // chin
  return d;
}

function sculpt([nx, ny, nz], f) {
  let x = nx * W * f.width;
  const y = ny * H;
  let z = nz * D;
  if (y < 0) {
    // The lower face narrows to the chin; a stronger jaw narrows less.
    x *= 1 - 0.17 * Math.min(1, -y / H) ** 2.2 * (2 - f.jaw);
  }
  if (nz < 0) z *= 1 - 0.36 * smooth(-0.1, -0.46, y); // the back of the skull curves in to the neck
  if (nz < 0 && y > -0.05) z *= 1 + 0.05 * smooth(-0.05, 0.25, y);
  const front = smooth(0.1, 0.55, nz);
  if (front > 0) {
    z += 0.045 * front * smooth(-0.2, -0.45, y) * f.chin;
    z -= 0.035 * front * smooth(0.14, 0.46, y);
    z += front * features(x, y, f);
  }
  return [x, y, z];
}

// ---- Morph targets (deltas at full strength) --------------------------------------------------------------------------

/** How much of the jaw's movement a point follows: all of it from just under the lips down, fading out towards the ears. */
function jawWeight(x, y, z) {
  return smooth(MOUTH_Y + 0.002, MOUTH_Y - 0.022, y) * (1 - smooth(0.17, 0.3, Math.abs(x))) * smooth(-0.1, 0.18, z);
}
function jawDelta([x, y, z]) {
  const w = jawWeight(x, y, z);
  return [0, -JAW_DROP * w, -0.018 * w];
}
function smileDelta([x, y, z]) {
  if (z < 0) return [0, 0, 0];
  const corners = pair(x, 0.06, y - (MOUTH_Y + 0.004), 0.022, 0.02);
  const cheeks = pair(x, 0.125, y - (MOUTH_Y + 0.1), 0.05, 0.045);
  const underEyes = pair(x, EYE_X, y - (EYE_Y - 0.04), 0.045, 0.02);
  // Corners move outward: the pair on each side pulls its own way, and nothing moves at the centre line.
  const outward = 0.008 * (gauss(x - 0.06, y - (MOUTH_Y + 0.004), 0.022, 0.02) - gauss(x + 0.06, y - (MOUTH_Y + 0.004), 0.022, 0.02));
  return [outward, 0.012 * corners + 0.012 * cheeks + 0.006 * underEyes, -0.004 * corners + 0.008 * cheeks];
}
function browDelta([x, y, z]) {
  if (z < 0.1) return [0, 0, 0];
  const w = pair(x, 0.12, y - (EYE_Y + 0.075), 0.08, 0.04);
  return [0, 0.02 * w, -0.002 * w];
}

/**
 * A surface over the head's grid: `point` places each vertex, `keep` decides which squares of the grid exist (hair and beard are
 * the same grid with most of it left out), and `morphs` are functions of the head position underneath.
 */
function surface(f, { point, keep, morphs = [], color, alpha }) {
  const positions = [];
  const uvs = [];
  const colors = color || alpha ? [] : null;
  const base = [];
  const dirs = [];
  for (let iy = 0; iy <= ROWS; iy++) {
    for (let ix = 0; ix <= COLS; ix++) {
      const u = ix / COLS;
      const v = iy / ROWS;
      const dir = direction(u, v);
      const b = sculpt(dir, f);
      const p = point ? point(dir, b, u, v) : b;
      positions.push(p[0], p[1], p[2]);
      uvs.push(u, 1 - v);
      base.push(b);
      dirs.push(dir);
      if (colors) {
        const c = color ? color(b, dir) : { r: 1, g: 1, b: 1 };
        colors.push(c.r, c.g, c.b);
        if (alpha) colors.push(alpha(b, dir, u));
      }
    }
  }
  const index = [];
  const at = (ix, iy) => iy * (COLS + 1) + ix;
  for (let iy = 0; iy < ROWS; iy++) {
    for (let ix = 0; ix < COLS; ix++) {
      const a = at(ix + 1, iy);
      const b = at(ix, iy);
      const c = at(ix, iy + 1);
      const d = at(ix + 1, iy + 1);
      if (keep) {
        const centre = [0, 1, 2].map((k) => (base[a][k] + base[b][k] + base[c][k] + base[d][k]) / 4);
        const dir = [0, 1, 2].map((k) => (dirs[a][k] + dirs[b][k] + dirs[c][k] + dirs[d][k]) / 4);
        if (!keep(centre, dir, ix, iy)) continue;
      }
      if (iy !== 0) index.push(a, b, d);
      if (iy !== ROWS - 1) index.push(b, c, d);
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
  if (colors) geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, alpha ? 4 : 3));
  geometry.setIndex(index);
  geometry.computeVertexNormals();
  if (morphs.length) {
    geometry.morphTargetsRelative = true;
    geometry.morphAttributes.position = morphs.map((fn) => {
      const deltas = [];
      for (const b of base) deltas.push(...fn(b));
      return new THREE.Float32BufferAttribute(deltas, 3);
    });
  }
  return { geometry, base };
}

// ---- Textures painted on a canvas ---------------------------------------------------------------------------------------

const css = (color) => `#${color.getHexString()}`;

function canvasTexture(size, paint, { srgb = true, repeat } = {}) {
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  paint(canvas.getContext('2d'), size);
  const texture = new THREE.CanvasTexture(canvas);
  if (srgb) texture.colorSpace = THREE.SRGBColorSpace;
  if (repeat) {
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.repeat.set(repeat[0], repeat[1]);
  }
  texture.anisotropy = 4;
  return texture;
}

function irisTexture(hex) {
  const colour = new THREE.Color(hex);
  const light = css(colour.clone().lerp(new THREE.Color('#d9c7a0'), 0.35));
  const dark = css(colour.clone().multiplyScalar(0.45));
  return canvasTexture(256, (ctx, s) => {
    const c = s / 2;
    const fill = ctx.createRadialGradient(c, c, s * 0.12, c, c, c);
    fill.addColorStop(0, light);
    fill.addColorStop(0.5, hex);
    fill.addColorStop(0.86, dark);
    fill.addColorStop(1, '#0c0907');
    ctx.fillStyle = fill;
    ctx.beginPath();
    ctx.arc(c, c, c, 0, Math.PI * 2);
    ctx.fill();
    // The fibres radiating out from the pupil are what makes an iris read as an iris.
    for (let i = 0; i < 260; i++) {
      const angle = hash(i, 1) * Math.PI * 2;
      const r0 = s * 0.17;
      const r1 = s * (0.36 + hash(i, 2) * 0.12);
      ctx.strokeStyle = hash(i, 3) > 0.5 ? 'rgba(255,245,225,0.16)' : 'rgba(0,0,0,0.22)';
      ctx.lineWidth = 0.6 + hash(i, 4) * 1.4;
      ctx.beginPath();
      ctx.moveTo(c + Math.cos(angle) * r0, c + Math.sin(angle) * r0);
      ctx.quadraticCurveTo(c + Math.cos(angle + 0.08) * (r0 + r1) / 2, c + Math.sin(angle + 0.08) * (r0 + r1) / 2,
        c + Math.cos(angle) * r1, c + Math.sin(angle) * r1);
      ctx.stroke();
    }
    ctx.fillStyle = '#050403';
    ctx.beginPath();
    ctx.arc(c, c, s * 0.19, 0, Math.PI * 2);
    ctx.fill();
  });
}

/** Fine strands running down the hair, so it catches light like hair rather than like paint. */
function strandTexture(hex, repeat) {
  return canvasTexture(256, (ctx, s) => {
    ctx.fillStyle = hex;
    ctx.fillRect(0, 0, s, s);
    for (let i = 0; i < 1600; i++) {
      const x = hash(i, 7) * s;
      ctx.strokeStyle = hash(i, 8) > 0.55 ? 'rgba(255,240,220,0.07)' : 'rgba(0,0,0,0.12)';
      ctx.lineWidth = 0.4 + hash(i, 9) * 0.9;
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.bezierCurveTo(x + (hash(i, 10) - 0.5) * 10, s * 0.35, x + (hash(i, 11) - 0.5) * 10, s * 0.7, x + (hash(i, 12) - 0.5) * 6, s);
      ctx.stroke();
    }
  }, { repeat });
}

// ---- Hair ---------------------------------------------------------------------------------------------------------------

/** Where the hair starts, going round from the forehead (0) past the ear (about 1.57) to the nape (pi). */
const HAIRLINES = {
  short: [[0, 0.3], [0.45, 0.29], [0.85, 0.2], [1.25, 0.1], [1.5, 0.07], [1.75, 0.0], [2.3, -0.2], [Math.PI, -0.26]],
  fade: [[0, 0.31], [0.45, 0.3], [0.85, 0.2], [1.25, 0.1], [1.5, 0.07], [1.75, 0.0], [2.3, -0.2], [Math.PI, -0.26]],
  bun: [[0, 0.3], [0.5, 0.275], [0.9, 0.15], [1.3, 0.05], [1.6, 0.02], [2.0, -0.08], [Math.PI, -0.22]],
  bob: [[0, 0.29], [0.5, 0.26], [0.9, 0.07], [1.2, -0.16], [1.6, -0.24], [2.2, -0.27], [Math.PI, -0.28]],
  long: [[0, 0.29], [0.55, 0.265], [0.95, 0.1], [1.25, -0.1], [1.6, -0.26], [2.2, -0.33], [Math.PI, -0.35]],
};

function hairlineAt(points, angle) {
  const a = Math.abs(angle);
  for (let i = 1; i < points.length; i++) {
    if (a <= points[i][0]) {
      const [a0, y0] = points[i - 1];
      const [a1, y1] = points[i];
      return y0 + ((a - a0) / (a1 - a0)) * (y1 - y0);
    }
  }
  return points[points.length - 1][1];
}

/** How far the hair stands off the scalp for each style: fuller on top, close at a fade's sides, flaring at a bob's ends. */
function hairOffset(style, [, y], [, ny, nz]) {
  const top = Math.max(0, ny);
  switch (style) {
    case 'fade': return 0.006 + 0.032 * smooth(0.05, 0.45, y);
    case 'short': return 0.016 + 0.045 * top + 0.014 * Math.max(0, nz) * top;
    case 'bun': return 0.014 + 0.014 * top;
    case 'bob': return 0.03 + 0.022 * top + 0.03 * smooth(0.05, -0.24, y);
    case 'long': return 0.028 + 0.022 * top + 0.02 * smooth(0.0, -0.3, y);
    default: return 0.02;
  }
}

function buildHair(look, f, group, disposables) {
  const style = look.hairStyle;
  const points = HAIRLINES[style];
  const edgeAt = (nx, nz, ix) => hairlineAt(points, Math.atan2(nx, nz)) + (hash(ix, 3) - 0.5) * 0.01;
  const partSide = style === 'bun' ? 0 : null;
  const map = strandTexture(look.hair, [14, 2]);
  const hairParams = {
    map, roughness: 0.62, sheen: 0.55, sheenRoughness: 0.45, sheenColor: new THREE.Color(look.hair).lerp(new THREE.Color('#ffffff'), 0.14),
    side: THREE.DoubleSide,
  };
  // The cap fades out at the hairline (per-vertex alpha), which is what makes a hairline look soft instead of cut out.
  const material = new THREE.MeshPhysicalMaterial({ ...hairParams, vertexColors: true, transparent: true });
  const solid = new THREE.MeshPhysicalMaterial(hairParams);
  disposables.push(map, material, solid);

  const { geometry } = surface(f, {
    point: (dir, b, u) => {
      const edge = edgeAt(dir[0], dir[2], Math.round(u * COLS));
      const off = hairOffset(style, b, dir) * (0.25 + 0.75 * smooth(edge, edge + 0.06, b[1]));
      // A little unevenness, strongest for the curls of a fade.
      const bump = style === 'fade' ? (hash(Math.round(b[0] * 180), Math.round(b[1] * 180)) - 0.5) * 0.01 : 0;
      return [b[0] + dir[0] * (off + bump), b[1] + dir[1] * (off + bump), b[2] + dir[2] * (off + bump)];
    },
    alpha: ([, y], [nx, , nz], u) => {
      const edge = edgeAt(nx, nz, Math.round(u * COLS));
      return smooth(edge - 0.012, edge + 0.024, y);
    },
    keep: ([x, y], [nx, , nz], ix) => {
      if (y < edgeAt(nx, nz, ix) - 0.02) return false;
      // A parting shows a thin line of scalp.
      if (partSide !== null && nz > 0 && y > 0.3 && Math.abs(x - partSide) < 0.0035) return false;
      return true;
    },
  });
  disposables.push(geometry);
  group.add(new THREE.Mesh(geometry, material));

  if (style === 'bun') {
    const bun = new THREE.SphereGeometry(0.1, 40, 28);
    bun.scale(1, 0.88, 0.85);
    disposables.push(bun);
    const mesh = new THREE.Mesh(bun, solid);
    mesh.position.set(0, 0.1, -0.43);
    group.add(mesh);
  }
  if (style === 'long') {
    // Hair falling behind the shoulders.
    const drape = new THREE.CylinderGeometry(0.29, 0.37, 0.8, 56, 12, true, Math.PI / 2, Math.PI);
    drape.scale(1, 1, 0.72);
    disposables.push(drape);
    const mesh = new THREE.Mesh(drape, solid);
    mesh.position.set(0, -0.56, -0.08);
    group.add(mesh);
  }
}

function buildBeard(look, f, group, disposables) {
  const beardColour = css(new THREE.Color(look.hair).lerp(new THREE.Color(look.skin), 0.28));
  const map = strandTexture(beardColour, [24, 6]);
  const material = new THREE.MeshPhysicalMaterial({ map, roughness: 0.9, sheen: 0.4, sheenRoughness: 0.6, sheenColor: new THREE.Color(look.hair) });
  disposables.push(map, material);
  const { geometry } = surface(f, {
    point: (dir, b) => {
      const off = 0.0035 + (hash(Math.round(b[0] * 260), Math.round(b[1] * 260)) - 0.5) * 0.002;
      return [b[0] + dir[0] * off, b[1] + dir[1] * off, b[2] + dir[2] * off];
    },
    keep: ([x, y], [nx, , nz]) => {
      const around = Math.abs(Math.atan2(nx, nz));
      if (around > 1.75) return false;
      if (around > 1.35) return y < EYE_Y - 0.02 && y > -0.22;
      if (around > 1.0) return y < -0.08;
      const cheekLine = -0.15 - 0.07 * Math.max(0, 1 - Math.abs(x) / 0.22);
      if (y > cheekLine) return false;
      if (Math.abs(x) < 0.08 && y > MOUTH_Y - 0.04 && y < MOUTH_Y + 0.035) return false;
      return y > -0.52;
    },
    morphs: [jawDelta, smileDelta],
  });
  disposables.push(geometry);
  const mesh = new THREE.Mesh(geometry, material);
  group.add(mesh);
  return mesh;
}

// ---- Small parts --------------------------------------------------------------------------------------------------------

function roundedRect(shape, w, h, r) {
  const x = -w / 2;
  const y = -h / 2;
  shape.moveTo(x + r, y);
  shape.lineTo(x + w - r, y);
  shape.quadraticCurveTo(x + w, y, x + w, y + r);
  shape.lineTo(x + w, y + h - r);
  shape.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  shape.lineTo(x + r, y + h);
  shape.quadraticCurveTo(x, y + h, x, y + h - r);
  shape.lineTo(x, y + r);
  shape.quadraticCurveTo(x, y, x + r, y);
  return shape;
}

function rod(from, to, radius, material, disposables) {
  const length = from.distanceTo(to);
  const geometry = new THREE.CylinderGeometry(radius, radius, length, 8);
  geometry.translate(0, length / 2, 0);
  geometry.rotateX(Math.PI / 2);
  disposables.push(geometry);
  const mesh = new THREE.Mesh(geometry, material);
  mesh.position.copy(from);
  mesh.lookAt(to);
  return mesh;
}

/** The skin's surface height at (x, y) on the front of the face, read off the built head. */
function surfaceZ(base, x, y) {
  let best = null;
  let bestDistance = Infinity;
  for (const p of base) {
    if (p[2] < 0.1) continue;
    const d = (p[0] - x) ** 2 + (p[1] - y) ** 2;
    if (d < bestDistance) {
      bestDistance = d;
      best = p;
    }
  }
  return best ? best[2] : 0.4;
}

// ---- The whole person ---------------------------------------------------------------------------------------------------

export function buildCharacter(look) {
  const f = { width: 1, jaw: 1, chin: 1, nose: 1, lips: 1, eyes: 1, brow: 1, ridge: 1, ...look.face };
  const disposables = [];
  const root = new THREE.Group();
  const headPivot = new THREE.Group();
  headPivot.position.set(0, -0.3, -0.03);
  const head = new THREE.Group();
  head.position.set(0, 0.3, 0.03);
  headPivot.add(head);
  root.add(headPivot);

  const skin = new THREE.Color(look.skin);
  const blush = skin.clone().multiply(new THREE.Color(1.14, 0.84, 0.82));
  const shade = skin.clone().multiplyScalar(0.72);
  const lipColour = new THREE.Color(look.lip);
  const nostril = skin.clone().multiplyScalar(0.3);
  const skinParams = { roughness: 0.58, sheen: 0.45, sheenRoughness: 0.55, sheenColor: blush.clone(), clearcoat: 0.06, clearcoatRoughness: 0.5 };

  // Face and scalp, coloured where real skin changes: warmer cheeks and nose tip, deeper around the eyes, the nostrils, the lip line.
  const faceMaterial = new THREE.MeshPhysicalMaterial({ vertexColors: true, ...skinParams });
  const { geometry: faceGeometry, base } = surface(f, {
    color: ([x, y], [, , nz]) => {
      const front = smooth(0.1, 0.55, nz);
      const c = skin.clone();
      c.lerp(blush, (0.12 * pair(x, 0.145, y - (EYE_Y - 0.09), 0.06, 0.05) + 0.08 * gauss(x, y - NOSE_Y, 0.03, 0.03)
        + 0.05 * gauss(x, y - (MOUTH_Y - 0.125), 0.05, 0.05)) * front);
      c.lerp(shade, 0.3 * pair(x, EYE_X, y - (EYE_Y - 0.015), 0.05, 0.038) * front);
      c.lerp(nostril, 0.75 * pair(x, 0.024 * f.nose, y - (NOSE_Y - 0.03), 0.0075, 0.0055) * front);
      c.lerp(lipColour, 0.35 * gauss(x, y - MOUTH_Y, 0.05, 0.02) * front);
      return c;
    },
    morphs: [jawDelta, smileDelta, browDelta],
  });
  disposables.push(faceMaterial, faceGeometry);
  const face = new THREE.Mesh(faceGeometry, faceMaterial);
  head.add(face);

  const skinMaterial = new THREE.MeshPhysicalMaterial({ color: skin, ...skinParams });
  const earMaterial = new THREE.MeshPhysicalMaterial({ color: skin.clone().lerp(blush, 0.4), ...skinParams });
  const earInnerMaterial = new THREE.MeshPhysicalMaterial({ color: shade.clone().lerp(blush, 0.3), ...skinParams });
  disposables.push(skinMaterial, earMaterial, earInnerMaterial);

  // Ears
  const earGeometry = new THREE.SphereGeometry(1, 24, 16);
  earGeometry.scale(0.024, 0.095, 0.058);
  const earInnerGeometry = new THREE.SphereGeometry(1, 20, 12);
  earInnerGeometry.scale(0.012, 0.064, 0.036);
  disposables.push(earGeometry, earInnerGeometry);
  for (const side of [-1, 1]) {
    const ear = new THREE.Group();
    ear.position.set(side * W * f.width * 0.985, EYE_Y - 0.04, -0.035);
    ear.rotation.set(0, side * 0.3, side * -0.08);
    ear.add(new THREE.Mesh(earGeometry, earMaterial));
    const inner = new THREE.Mesh(earInnerGeometry, earInnerMaterial);
    inner.position.set(side * 0.014, 0.004, 0.004);
    ear.add(inner);
    head.add(ear);
    if (look.earrings) {
      const stud = new THREE.SphereGeometry(0.011, 16, 12);
      const metal = new THREE.MeshStandardMaterial({ color: look.earrings, metalness: 1, roughness: 0.22 });
      disposables.push(stud, metal);
      const earring = new THREE.Mesh(stud, metal);
      earring.position.set(side * (W * f.width * 0.985 + 0.012), EYE_Y - 0.13, -0.012);
      head.add(earring);
    }
  }

  // Eyes: sclera, iris and pupil under a clear cornea, in a group that turns to look.
  const eyeRadius = 0.052 * f.eyes;
  const scleraMaterial = new THREE.MeshPhysicalMaterial({ color: '#f1ece5', roughness: 0.3, clearcoat: 0.5 });
  const irisMap = irisTexture(look.eyes);
  const irisMaterial = new THREE.MeshStandardMaterial({ map: irisMap, roughness: 0.45 });
  const corneaMaterial = new THREE.MeshPhysicalMaterial({
    color: '#ffffff', transmission: 1, thickness: 0.002, roughness: 0.02, ior: 1.376, clearcoat: 1, clearcoatRoughness: 0.02,
  });
  const scleraGeometry = new THREE.SphereGeometry(eyeRadius, 40, 28);
  const irisGeometry = new THREE.SphereGeometry(eyeRadius * 1.004, 48, 12, 0, Math.PI * 2, 0, 0.53);
  irisGeometry.rotateX(Math.PI / 2);
  {
    // Flat projection, so the round iris painting lands the right way up on the curved cap.
    const extent = eyeRadius * 1.004 * Math.sin(0.53);
    const position = irisGeometry.attributes.position;
    const uv = irisGeometry.attributes.uv;
    for (let i = 0; i < position.count; i++) uv.setXY(i, position.getX(i) / (2 * extent) + 0.5, position.getY(i) / (2 * extent) + 0.5);
  }
  const corneaGeometry = new THREE.SphereGeometry(eyeRadius * 1.02, 40, 16, 0, Math.PI * 2, 0, 0.62);
  corneaGeometry.rotateX(Math.PI / 2);
  disposables.push(scleraMaterial, irisMap, irisMaterial, corneaMaterial, scleraGeometry, irisGeometry, corneaGeometry);

  const upperLidGeometry = new THREE.SphereGeometry(eyeRadius * 1.07, 40, 16, 0, Math.PI * 2, 0, Math.PI / 2);
  const lowerLidGeometry = new THREE.SphereGeometry(eyeRadius * 1.05, 40, 12, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2);
  const lashGeometry = new THREE.TorusGeometry(eyeRadius * 1.075, eyeRadius * (look.female ? 0.075 : 0.055), 6, 48, Math.PI);
  lashGeometry.rotateX(Math.PI / 2);
  const lashMaterial = new THREE.MeshStandardMaterial({ color: '#16100d', roughness: 0.6 });
  disposables.push(upperLidGeometry, lowerLidGeometry, lashGeometry, lashMaterial);

  const eyes = [];
  const upperLids = [];
  const lowerLids = [];
  for (const side of [-1, 1]) {
    const x = side * EYE_X * Math.min(1.02, f.width);
    const centre = new THREE.Vector3(x, EYE_Y, surfaceZ(base, x, EYE_Y) - eyeRadius * 0.35);
    const eye = new THREE.Group();
    eye.position.copy(centre);
    eye.add(new THREE.Mesh(scleraGeometry, scleraMaterial));
    eye.add(new THREE.Mesh(irisGeometry, irisMaterial));
    eye.add(new THREE.Mesh(corneaGeometry, corneaMaterial));
    head.add(eye);
    eyes.push(eye);

    const upper = new THREE.Mesh(upperLidGeometry, skinMaterial);
    upper.position.copy(centre);
    const lash = new THREE.Mesh(lashGeometry, lashMaterial);
    upper.add(lash);
    head.add(upper);
    upperLids.push(upper);
    const lower = new THREE.Mesh(lowerLidGeometry, skinMaterial);
    lower.position.copy(centre);
    head.add(lower);
    lowerLids.push(lower);
  }

  // Brows follow the brow ridge, just proud of the skin.
  const browMaterial = new THREE.MeshStandardMaterial({ color: new THREE.Color(look.hair).multiplyScalar(0.8), roughness: 0.92 });
  disposables.push(browMaterial);
  const brows = new THREE.Group();
  const browArch = (look.female ? [0.052, 0.067, 0.074, 0.07, 0.055] : [0.055, 0.063, 0.066, 0.064, 0.055]).map((d) => EYE_Y + d);
  for (const side of [-1, 1]) {
    const points = [0.064, 0.1, 0.136, 0.17, 0.198].map((bx, i) => {
      const px = side * bx;
      return new THREE.Vector3(px, browArch[i], surfaceZ(base, px, browArch[i]) + 0.005);
    });
    const tube = new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points), 24, 0.0098 * f.brow, 8, false);
    tube.computeBoundingBox();
    const centre = tube.boundingBox.getCenter(new THREE.Vector3());
    tube.translate(-centre.x, -centre.y, -centre.z);
    tube.scale(1, 0.55, 0.8);
    tube.translate(centre.x, centre.y, centre.z);
    disposables.push(tube);
    brows.add(new THREE.Mesh(tube, browMaterial));
  }
  head.add(brows);

  // Mouth: lips on the face, with a dark mouth, and teeth, that show as the jaw opens.
  const mouthZ = surfaceZ(base, 0, MOUTH_Y);
  const lipWidth = 0.062 * f.width;
  const lipMaterial = new THREE.MeshPhysicalMaterial({
    color: skin.clone().lerp(lipColour, 0.72), roughness: 0.42, clearcoat: 0.18, clearcoatRoughness: 0.4, sheen: 0.35, sheenColor: blush.clone(),
  });
  const wrapLip = (geometry, width) => {
    const position = geometry.attributes.position;
    for (let i = 0; i < position.count; i++) {
      const lx = position.getX(i);
      position.setZ(i, position.getZ(i) - (lx / width) ** 2 * 0.022);
    }
    geometry.computeVertexNormals();
    return geometry;
  };
  const upperLipGeometry = new THREE.SphereGeometry(1, 40, 20);
  upperLipGeometry.scale(lipWidth, 0.013 * f.lips, 0.0145);
  wrapLip(upperLipGeometry, lipWidth);
  const lowerLipGeometry = new THREE.SphereGeometry(1, 40, 20);
  lowerLipGeometry.scale(lipWidth * 0.93, 0.0165 * f.lips, 0.016);
  wrapLip(lowerLipGeometry, lipWidth);
  const insideGeometry = new THREE.CircleGeometry(1, 40);
  const insideMaterial = new THREE.MeshStandardMaterial({ color: '#2c0f10', roughness: 1 });
  const teethGeometry = new THREE.ShapeGeometry(roundedRect(new THREE.Shape(), 0.074, 0.012, 0.004));
  const teethMaterial = new THREE.MeshStandardMaterial({ color: '#efe9de', roughness: 0.32 });
  disposables.push(lipMaterial, upperLipGeometry, lowerLipGeometry, insideGeometry, insideMaterial, teethGeometry, teethMaterial);

  const upperLip = new THREE.Mesh(upperLipGeometry, lipMaterial);
  upperLip.position.set(0, MOUTH_Y + 0.0105, mouthZ - 0.006);
  const lowerLip = new THREE.Mesh(lowerLipGeometry, lipMaterial);
  lowerLip.position.set(0, MOUTH_Y - 0.013, mouthZ - 0.004);
  const inside = new THREE.Mesh(insideGeometry, insideMaterial);
  inside.position.set(0, MOUTH_Y, mouthZ + 0.003);
  inside.scale.set(lipWidth * 0.85, 0.001, 1);
  const teeth = new THREE.Mesh(teethGeometry, teethMaterial);
  teeth.position.set(0, MOUTH_Y - 0.0055, mouthZ + 0.005);
  head.add(inside, teeth, upperLip, lowerLip);

  let beard = null;
  if (look.hairStyle) buildHair(look, f, head, disposables);
  if (look.beard) beard = buildBeard(look, f, head, disposables);

  if (look.glasses) {
    const frameMaterial = new THREE.MeshStandardMaterial({ color: '#17181b', roughness: 0.32, metalness: 0.25 });
    const lensMaterial = new THREE.MeshPhysicalMaterial({
      color: '#ffffff', transparent: true, opacity: 0.08, roughness: 0.05, clearcoat: 1, clearcoatRoughness: 0.03, depthWrite: false,
    });
    const outline = roundedRect(new THREE.Shape(), 0.112, 0.072, 0.019);
    outline.holes.push(roundedRect(new THREE.Path(), 0.096, 0.056, 0.014));
    const frameGeometry = new THREE.ExtrudeGeometry(outline, { depth: 0.005, bevelEnabled: false, curveSegments: 10 });
    const lensGeometry = new THREE.ShapeGeometry(roundedRect(new THREE.Shape(), 0.096, 0.056, 0.014));
    disposables.push(frameMaterial, lensMaterial, frameGeometry, lensGeometry);
    const z = eyes[0].position.z + eyeRadius + 0.03;
    for (const side of [-1, 1]) {
      const frame = new THREE.Mesh(frameGeometry, frameMaterial);
      frame.position.set(side * EYE_X, EYE_Y - 0.004, z);
      frame.rotation.y = side * 0.12;
      const lens = new THREE.Mesh(lensGeometry, lensMaterial);
      lens.position.z = 0.0025;
      frame.add(lens);
      head.add(frame);
      head.add(rod(new THREE.Vector3(side * (EYE_X + 0.055), EYE_Y + 0.014, z - 0.006),
        new THREE.Vector3(side * W * f.width * 0.98, 0.045, -0.03), 0.0032, frameMaterial, disposables));
    }
    const bridge = new THREE.TubeGeometry(new THREE.QuadraticBezierCurve3(
      new THREE.Vector3(-EYE_X + 0.056, EYE_Y + 0.012, z + 0.002), new THREE.Vector3(0, EYE_Y + 0.022, z + 0.012),
      new THREE.Vector3(EYE_X - 0.056, EYE_Y + 0.012, z + 0.002)), 12, 0.0032, 6, false);
    disposables.push(bridge);
    head.add(new THREE.Mesh(bridge, frameMaterial));
  }

  // Neck and shoulders. Lathe profiles run bottom to top so their faces point outwards.
  const neckSize = look.female ? 0.9 : 1;
  const neckGeometry = new THREE.LatheGeometry([
    new THREE.Vector2(0.27 * neckSize, -0.72), new THREE.Vector2(0.215 * neckSize, -0.62), new THREE.Vector2(0.195 * neckSize, -0.5),
    new THREE.Vector2(0.19 * neckSize, -0.36), new THREE.Vector2(0.185 * neckSize, -0.2),
  ], 64);
  neckGeometry.scale(1, 1, 0.9);
  disposables.push(neckGeometry);
  const neck = new THREE.Mesh(neckGeometry, skinMaterial);
  neck.position.z = -0.07; // the throat sits well behind the chin
  root.add(neck);

  const profile = new THREE.SplineCurve([
    new THREE.Vector2(0.26, -0.6), new THREE.Vector2(0.4, -0.66), new THREE.Vector2(0.58, -0.72), new THREE.Vector2(0.74, -0.8),
    new THREE.Vector2(0.82, -0.93), new THREE.Vector2(0.85, -1.12), new THREE.Vector2(0.86, -1.5), new THREE.Vector2(0.86, -2.0),
  ]).getPoints(120).reverse();
  const torsoGeometry = new THREE.LatheGeometry(profile, 256);
  torsoGeometry.scale(1, 1, 0.45);
  const top = new THREE.Color(look.top);
  const shirt = new THREE.Color(look.shirt);
  const lapel = top.clone().multiplyScalar(1.12);
  const torsoColours = [];
  const tp = torsoGeometry.attributes.position;
  for (let i = 0; i < tp.count; i++) {
    const x = tp.getX(i);
    const y = tp.getY(i);
    const z = tp.getZ(i);
    const c = top.clone();
    if (look.outfit === 'blazer' && z > 0) {
      const open = 0.15 * clamp01(1 - (-0.62 - y) / 0.42);
      const inLapel = smooth(open + 0.08, open + 0.06, Math.abs(x)) * smooth(-1.12, -1.08, y);
      c.lerp(lapel, inLapel);
      c.lerp(shirt, smooth(open + 0.014, open - 0.014, Math.abs(x)));
    }
    torsoColours.push(c.r, c.g, c.b);
  }
  torsoGeometry.setAttribute('color', new THREE.Float32BufferAttribute(torsoColours, 3));
  const clothMaterial = new THREE.MeshPhysicalMaterial({
    vertexColors: true, roughness: 0.9, sheen: 0.35, sheenRoughness: 0.8, sheenColor: top.clone().lerp(new THREE.Color('#ffffff'), 0.25),
  });
  disposables.push(torsoGeometry, clothMaterial);
  const torso = new THREE.Mesh(torsoGeometry, clothMaterial);
  torso.position.z = -0.04;
  root.add(torso);

  if (look.outfit === 'sweater') {
    const collar = new THREE.TorusGeometry(0.265, 0.028, 14, 72);
    collar.rotateX(Math.PI / 2);
    collar.scale(1, 1, 0.78);
    const ribMaterial = new THREE.MeshPhysicalMaterial({ color: top.clone().multiplyScalar(0.85), roughness: 0.85, sheen: 0.6 });
    disposables.push(collar, ribMaterial);
    const rib = new THREE.Mesh(collar, ribMaterial);
    rib.position.set(0, -0.63, -0.035);
    root.add(rib);
  }

  root.traverse((object) => {
    if (!object.isMesh) return;
    const clear = object.material.transmission > 0 || (object.material.transparent && !object.material.vertexColors);
    object.castShadow = !clear;
    object.receiveShadow = true;
  });

  const lowerLipWeight = 1;
  return {
    root, headPivot, head, torso, face, beard, eyes, upperLids, lowerLids, brows, eyeRadius,
    mouth: {
      upperLip, lowerLip, inside, teeth, lipWidth, lowerLipWeight,
      upperBase: upperLip.position.clone(), lowerBase: lowerLip.position.clone(), teethBase: teeth.position.clone(),
    },
    dispose() {
      for (const item of disposables) item.dispose();
    },
  };
}
