/**
 * Plain rules for judging a camera picture: is the lighting usable, and is the face well placed in the frame. They work on a small
 * copy of the picture and, when a face was found, its box. Nothing here knows who the person is; it only measures brightness and
 * position, so the thresholds are starting guesses to be tuned with real recordings.
 *
 * A face box is {x, y, w, h} as fractions of the picture, from its top left corner.
 */

/** Mean brightness (0 to 255) over RGBA pixels, optionally only inside or only outside a box. */
function brightness(pixels, width, height, box, inside) {
  const x0 = box ? Math.floor(box.x * width) : 0;
  const y0 = box ? Math.floor(box.y * height) : 0;
  const x1 = box ? Math.ceil((box.x + box.w) * width) : width;
  const y1 = box ? Math.ceil((box.y + box.h) * height) : height;
  let total = 0;
  let count = 0;
  for (let y = 0; y < height; y += 2) {
    for (let x = 0; x < width; x += 2) {
      if (box) {
        const within = x >= x0 && x < x1 && y >= y0 && y < y1;
        if (within !== inside) continue;
      }
      const i = (y * width + x) * 4;
      total += 0.299 * pixels[i] + 0.587 * pixels[i + 1] + 0.114 * pixels[i + 2];
      count++;
    }
  }
  return count ? total / count : null;
}

/** How bright the whole picture is, and when there is a face, how bright the face is against what is around it. */
export function lumaStats(pixels, width, height, box = null) {
  return {
    overall: brightness(pixels, width, height, null, true),
    face: box ? brightness(pixels, width, height, box, true) : null,
    around: box ? brightness(pixels, width, height, box, false) : null,
  };
}

/** 'ok', 'dark', 'bright', or 'backlit' (a window or lamp behind the person leaves the face dark). */
export function judgeLighting({ overall, face, around }) {
  const reference = face ?? overall;
  if (reference == null) return null;
  if (reference < (face == null ? 50 : 45)) return 'dark';
  if (reference > 220) return 'bright';
  if (face != null && around != null && around - face > 60 && face < 100) return 'backlit';
  return 'ok';
}

const EDGE = 0.01;

/** 'none' (no face), 'cut' (touching the edge), 'far', 'close', 'off-centre', 'low', 'high', or 'ok'. */
export function judgeFraming(box) {
  if (!box) return 'none';
  if (box.x < EDGE || box.y < EDGE || box.x + box.w > 1 - EDGE || box.y + box.h > 1 - EDGE) return 'cut';
  if (box.w < 0.16) return 'far';
  if (box.w > 0.55) return 'close';
  const centreX = box.x + box.w / 2;
  const centreY = box.y + box.h / 2;
  if (Math.abs(centreX - 0.5) > 0.2) return 'off-centre';
  if (centreY > 0.62) return 'low';
  if (centreY < 0.25) return 'high';
  return 'ok';
}

/** What to tell the learner, in plain words, for each judgement. */
export const LIGHTING_ADVICE = {
  ok: 'Lighting looks good.',
  dark: 'Your face looks dark. Face a window or a lamp.',
  bright: 'The picture is too bright. Turn down the light on your face or move back from the lamp.',
  backlit: 'There is a bright light behind you. Face the light instead, or close the curtain behind you.',
};

export const FRAMING_ADVICE = {
  ok: 'Your face is well placed in the frame.',
  none: "We can't see your face. Check that the camera is uncovered and you are in front of it.",
  cut: 'Part of your face is cut off. Move to the middle of the frame.',
  far: 'You are far from the camera. Move a little closer.',
  close: 'You are very close to the camera. Move back a little.',
  'off-centre': 'You are off to one side. Move to the middle of the frame.',
  low: 'Your face is low in the frame. Tilt your screen forward a little, or sit up.',
  high: 'Your face is high in the frame. Tilt your screen back a little, or sit lower.',
};
