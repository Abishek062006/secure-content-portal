import Icon from '../Icon';

const SIZE = 34;
const STROKE = 3;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/** The daily goal as a ring, with the streak beside it. The flame lights up once the day's streak is safe. */
export default function DailyRing({ done, goal, streak, safe }) {
  const fraction = goal > 0 ? Math.min(1, done / goal) : 0;
  const label = `${streak}-day streak. ${done} of ${goal} finished today.`;
  return (
    <span className={`daily-ring${fraction >= 1 ? ' complete' : ''}${safe ? ' safe' : ''}`} title={label} role="img" aria-label={label}>
      <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`} aria-hidden="true">
        <circle className="daily-ring-track" cx={SIZE / 2} cy={SIZE / 2} r={RADIUS} strokeWidth={STROKE} fill="none" />
        <circle className="daily-ring-fill" cx={SIZE / 2} cy={SIZE / 2} r={RADIUS} strokeWidth={STROKE} fill="none"
                strokeDasharray={CIRCUMFERENCE} strokeDashoffset={CIRCUMFERENCE * (1 - fraction)}
                transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`} />
      </svg>
      <Icon name="flame" size={15} className="daily-ring-flame" />
      <span className="daily-ring-streak">{streak}</span>
    </span>
  );
}
