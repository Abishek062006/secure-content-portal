const WIDTH = 320;
const HEIGHT = 90;
const PAD = 12;

/** Overall scores of finished interviews, oldest to newest, so improvement is visible at a glance. */
export default function ScoreTrend({ sessions }) {
  const done = sessions.filter((s) => s.status === 'COMPLETED').slice(0, 10).reverse();
  if (done.length < 2) return null;

  const step = (WIDTH - PAD * 2) / (done.length - 1);
  const points = done.map((s, i) => [PAD + i * step, HEIGHT - PAD - (s.overallScore / 100) * (HEIGHT - PAD * 2)]);
  const path = points.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
  const first = done[0].overallScore;
  const last = done[done.length - 1].overallScore;
  const label = `Score trend over ${done.length} interviews: from ${first} to ${last} percent.`;

  return (
    <figure className="trend" role="img" aria-label={label}>
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} width="100%" preserveAspectRatio="none">
        <path className="trend-line" d={path} fill="none" />
        {points.map(([x, y], i) => <circle key={done[i].id} className="trend-dot" cx={x} cy={y} r="3.5" />)}
      </svg>
      <figcaption>{first}% to {last}% over your last {done.length} interviews</figcaption>
    </figure>
  );
}
