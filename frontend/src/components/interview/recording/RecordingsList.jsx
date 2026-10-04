import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import ConfirmDialog from '../../ConfirmDialog';
import Icon from '../../Icon';
import { deleteRecording, formatBytes, formatLength, listRecordings, settleInterrupted, storageUsage } from '../../../lib/localRecordings';

/** The interview recordings saved on this device, with what they take up and a way to delete them. Nothing shows without any. */
export default function RecordingsList({ userId, onChange }) {
  const [items, setItems] = useState(null);
  const [usage, setUsage] = useState(null);
  const [pending, setPending] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    if (userId == null) return;
    try {
      await settleInterrupted(userId);
      setItems(await listRecordings(userId));
      setUsage(await storageUsage(userId));
    } catch {
      setItems([]);
    }
  }, [userId]);

  useEffect(() => { load(); }, [load]);

  async function confirmDelete() {
    const target = pending;
    setPending(null);
    try {
      await deleteRecording(target.sessionId);
      await load();
      onChange?.();
    } catch {
      setError('That recording could not be deleted. Try again.');
    }
  }

  if (!items || items.length === 0) return null;

  return (
    <section className="progress-card recordings">
      <header>
        <h2>Recordings on this device</h2>
        <span className="field-hint">{usage ? `${usage.count} saved · ${formatBytes(usage.appBytes)}` : ''}</span>
      </header>
      <p className="field-hint">Saved only in this browser and never uploaded. Clearing your browser's site data removes them.</p>
      {error && <p className="field-error" role="alert">{error}</p>}
      <ul className="recordings-list">
        {items.map((item) => (
          <li key={item.sessionId}>
            <span className="recordings-main">
              <strong>{item.title || 'Interview'}</strong>
              <span className="field-hint">
                {new Date(item.startedAt).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}
                {' · '}{formatLength(item.durationMs)}{' · '}{formatBytes(item.sizeBytes || 0)}
                {item.status === 'interrupted' && ' · stopped early'}
              </span>
            </span>
            <span className="recordings-actions">
              <Link className="btn btn-sm" to={`/interview/${item.sessionId}/replay`}
                    aria-label={`Watch the recording of ${item.title || 'this interview'}`}>
                <Icon name="play" size={14} /> Watch
              </Link>
              <button type="button" className="btn btn-sm btn-danger-outline" onClick={() => setPending(item)}
                      aria-label={`Delete the recording of ${item.title || 'this interview'}`}>
                <Icon name="trash-2" size={14} /> Delete
              </button>
            </span>
          </li>
        ))}
      </ul>
      <ConfirmDialog
        open={Boolean(pending)}
        title={pending ? `Recording of ${pending.title || 'this interview'}` : ''}
        detail="The video is deleted from this device. Your interview results are not affected."
        onCancel={() => setPending(null)}
        onConfirm={confirmDelete}
      />
    </section>
  );
}
