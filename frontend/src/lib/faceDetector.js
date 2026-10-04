/**
 * Finding a face in a camera picture, with MediaPipe's small face detector. It runs entirely in the browser: the picture never
 * leaves the device. The detector's code and model are fetched when first needed (the setup check), not with the rest of the app.
 *
 * The files come from public CDNs by default; set the two VITE_ variables to host them yourselves. The version in the address
 * must match the installed package.
 */

const VERSION = '1.0.1';
const WASM_URL = import.meta.env.VITE_MEDIAPIPE_WASM_URL || `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${VERSION}/wasm`;
const MODEL_URL = import.meta.env.VITE_FACE_MODEL_URL
  || 'https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite';

let loading = null;

/** Loads the detector once; later calls share it. Rejects when the files can't be fetched (offline, blocked). */
export function loadFaceDetector() {
  if (!loading) {
    loading = (async () => {
      const { FaceDetector, FilesetResolver } = await import('@mediapipe/tasks-vision');
      const vision = await FilesetResolver.forVisionTasks(WASM_URL);
      const make = (delegate) => FaceDetector.createFromOptions(vision, {
        baseOptions: { modelAssetPath: MODEL_URL, delegate },
        runningMode: 'VIDEO',
        minDetectionConfidence: 0.5,
      });
      // The graphics card is faster where it works; fall back to the processor where it doesn't.
      try {
        return await make('GPU');
      } catch {
        return make('CPU');
      }
    })().catch((err) => {
      loading = null;
      throw err;
    });
  }
  return loading;
}

/** The largest face in the video's current frame as a box (fractions of the picture), or null. */
export function detectFace(detector, video) {
  const width = video.videoWidth;
  const height = video.videoHeight;
  if (!width || !height) return null;
  const { detections } = detector.detectForVideo(video, performance.now());
  let best = null;
  for (const d of detections) {
    const b = d.boundingBox;
    if (b && (!best || b.width * b.height > best.width * best.height)) best = b;
  }
  if (!best) return null;
  return { x: best.originX / width, y: best.originY / height, w: best.width / width, h: best.height / height };
}
