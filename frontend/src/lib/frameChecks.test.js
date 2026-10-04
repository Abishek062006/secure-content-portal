import { describe, expect, it } from 'vitest';
import { judgeFraming, judgeLighting, lumaStats } from './frameChecks';

/** A grey frame with a different grey where a face would be. */
function frame(width, height, background, box, faceGrey) {
  const data = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const inside = box && x >= box.x * width && x < (box.x + box.w) * width && y >= box.y * height && y < (box.y + box.h) * height;
      const v = inside ? faceGrey : background;
      const i = (y * width + x) * 4;
      data[i] = data[i + 1] = data[i + 2] = v;
      data[i + 3] = 255;
    }
  }
  return data;
}

const BOX = { x: 0.35, y: 0.25, w: 0.3, h: 0.4 };
const lighting = (background, face, box = BOX) => judgeLighting(lumaStats(frame(160, 120, background, box, face), 160, 120, box));

describe('judgeLighting', () => {
  it('accepts even, comfortable light', () => expect(lighting(120, 140)).toBe('ok'));
  it('calls a dark face dark', () => expect(lighting(40, 30)).toBe('dark'));
  it('calls a washed-out picture too bright', () => expect(lighting(240, 235)).toBe('bright'));
  it('calls a dark face against a bright window backlit', () => expect(lighting(220, 70)).toBe('backlit'));
  it('does not call a darker skin tone in good light dark', () => expect(lighting(130, 70)).toBe('ok'));
  it('judges the whole picture when there is no face', () => {
    expect(judgeLighting(lumaStats(frame(160, 120, 30, null), 160, 120))).toBe('dark');
  });
});

describe('judgeFraming', () => {
  const f = (x, y, w, h) => judgeFraming({ x, y, w, h });

  it('accepts a centred face', () => expect(f(0.35, 0.25, 0.3, 0.4)).toBe('ok'));
  it('says none when there is no face', () => expect(judgeFraming(null)).toBe('none'));
  it('calls a tiny face far', () => expect(f(0.45, 0.35, 0.1, 0.13)).toBe('far'));
  it('calls a huge face close', () => expect(f(0.2, 0.1, 0.6, 0.7)).toBe('close'));
  it('calls a face against the edge cut off', () => expect(f(0.0, 0.3, 0.3, 0.4)).toBe('cut'));
  it('calls a face to one side off-centre', () => expect(f(0.6, 0.3, 0.25, 0.35)).toBe('off-centre'));
  it('calls a face low in the frame low', () => expect(f(0.35, 0.55, 0.3, 0.4)).toBe('low'));
  it('calls a face high in the frame high', () => expect(f(0.35, 0.02, 0.3, 0.2)).toBe('high'));
});
