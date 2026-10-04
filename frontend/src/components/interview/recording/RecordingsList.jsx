import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import ConfirmDialog from '../../ConfirmDialog';
import Icon from '../../Icon';
import {
  deleteAllRecordings, deleteRecording, formatBytes, formatLength, isPersisted, listRecordings, requestPersistence, settleInterrupted, storageUsage,
} from '../../../lib/localRecordings';

/** Below this much room left the learner is told to make some. */
const LOW_SPACE_BYTES = 1024 * 1024 * 1024;

/** The interview recordings saved on this device, with what they take up and a way to delete them. Nothing shows without any. */
export default function RecordingsList({ userId, onChange }) {
  const [items, setItems] = useState(null);
  const [usage, setUsage] = useState(null);
  const [pending, setPending] = useState(null);
  const [deletingAll, setDeletingAll] = useState(false);
  const [persisted, setPersisted] = useState(false);
  const [asked, setAsked] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    if (userId == null) return;
    try {
      await settleInterrupted(userId);
      setItems(await listRecordings(userId));
      setUsage(await storageUsage(userId));
      setPersisted(await isPersisted());
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

  async function confirmDeleteAll() {
    setDeletingAll(false);
    try {
      await deleteAllRecordings(userId);
      await load();
      onChange?.();
    } catch {
      setError('The recordings could not be deleted. Try again.');
    }
  }

  async function keepThem() {
    setPersisted(await requestPersistence());
    setAsked(true);
  }

  if (!items || items.length === 0) return null;

  const room = usage?.browserQuota != null && usage?.browserUsage != null ? usage.browserQuota - usage.browserUsage : null;
  const low = room != null && room < LOW_SPACE_BYTES;

  return (
    <section className="progress-card recordings">
      <header>
        <h2>Recordings on this device</h2>
        <span className="field-hint">{usage ? `${usage.count} saved · ${formatBytes(usage.appBytes)}` : ''}</span>
      </header>
      <p className="field-hint">Saved only in this browser and never uploaded. Clearing your browser's site data removes them.</p>
      <div className="recordings-storage">
        {room != null && (
          <p className={low ? 'field-error' : 'field-hint'}>
            {low
              ? `This device is running low on room for recordings (${formatBytes(Math.max(0, room))} left). Delete some you no longer need.`
              : `This browser can hold about ${formatBytes(room)} more for this site.`}
          </p>
        )}
        <p className="field-hint">
          {persisted && 'The browser has agreed to keep these recordings unless you delete them.'}
          {!persisted && !asked && 'The browser may clear these recordings if the device runs short of space.'}
          {!persisted && asked && "The browser didn't agree to keep them this time, so it may clear them if the device runs short of space."}
          {!persisted && !asked && <> <button type="button" className="link-button" onClick={keepThem}>Ask the browser to keep them</button></>}
        </p>
      </div>
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
      {items.length > 1 && (
        <div className="recordings-foot">
          <button type="button" className="btn btn-sm btn-danger-outline" onClick={() => setDeletingAll(true)}>
            <Icon name="trash-2" size={14} /> Delete all {items.length} recordings
          </button>
        </div>
      )}
      <ConfirmDialog
        open={deletingAll}
        title={`All ${items.length} recordings on this device`}
        detail="The videos are deleted from this device. Your interview results are not affected."
        onCancel={() => setDeletingAll(false)}
        onConfirm={confirmDeleteAll}
      />
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
