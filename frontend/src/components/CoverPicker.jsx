import { useEffect, useRef, useState } from 'react';

const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_BYTES = 5 * 1024 * 1024;

/**
 * A large 16:9 drop zone for a cover image. {@code src} is an already-saved image to show;
 * a newly picked file is previewed locally and handed to {@code onPick}.
 */
export default function CoverPicker({ src, onPick, onRemove, busy }) {
  const input = useRef(null);
  const [preview, setPreview] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => () => preview && URL.revokeObjectURL(preview), [preview]);

  function choose(file) {
    setError(null);
    if (!file) return;
    if (!ACCEPTED.includes(file.type)) {
      setError('Use a JPG, PNG or WebP image.');
      return;
    }
    if (file.size > MAX_BYTES) {
      setError('That image is over 5 MB.');
      return;
    }
    setPreview(URL.createObjectURL(file));
    onPick(file);
  }

  const shown = preview || src;

  return (
    <div className="cover-picker">
      <div
        className={`cover-zone${dragging ? ' dragging' : ''}${shown ? ' has-image' : ''}`}
        role="button"
        tabIndex={0}
        onClick={() => input.current.click()}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current.click()}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => { e.preventDefault(); setDragging(false); choose(e.dataTransfer.files[0]); }}
      >
        {shown ? (
          <>
            <img src={shown} alt="Cover preview" />
            <span className="cover-change">{busy ? 'Uploading…' : 'Change image'}</span>
          </>
        ) : (
          <div className="cover-empty">
            <strong>Add a cover image</strong>
            <span>Click to choose, or drag one here</span>
            <span className="field-hint">JPG, PNG or WebP · up to 5 MB · 16:9 looks best</span>
          </div>
        )}
      </div>
      <input ref={input} type="file" hidden accept=".jpg,.jpeg,.png,.webp"
             onChange={(e) => { choose(e.target.files[0]); e.target.value = ''; }} />
      {shown && onRemove && (
        <button type="button" className="link-button danger" onClick={() => { setPreview(null); onRemove(); }}>
          Remove image
        </button>
      )}
      {error && <p className="form-error">{error}</p>}
    </div>
  );
}
