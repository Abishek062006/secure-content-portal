import { useEffect, useRef } from 'react';

/** Mirrors the old Thymeleaf confirm-modal fragment: a native <dialog> guarding deletes. */
export default function ConfirmDialog({ open, title, onCancel, onConfirm }) {
  const ref = useRef(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog ref={ref} className="confirm-dialog" onCancel={onCancel}>
      <h2>Delete this item?</h2>
      <p>"<strong>{title}</strong>" will be permanently removed, including the uploaded file. This can't be undone.</p>
      <div className="dialog-actions">
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
        <button type="button" className="btn btn-danger" onClick={onConfirm}>Delete</button>
      </div>
    </dialog>
  );
}
