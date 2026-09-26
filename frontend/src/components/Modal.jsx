import { useEffect, useRef } from 'react';

/** A native <dialog> modal: closes on Escape or a click on the backdrop. */
export default function Modal({ open, title, onClose, children, wide }) {
  const ref = useRef(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog ref={ref} className={`modal${wide ? ' modal-wide' : ''}`} onCancel={(e) => { e.preventDefault(); onClose(); }}
            onClick={(e) => { if (e.target === ref.current) onClose(); }}>
      {open && (
        <>
          <header className="modal-head">
            <h2>{title}</h2>
            <button type="button" className="modal-close" aria-label="Close" onClick={onClose}>✕</button>
          </header>
          <div className="modal-body">{children}</div>
        </>
      )}
    </dialog>
  );
}
