import { useState } from 'react';

/** A smooth single-series area chart in SVG, with a hover read-out. Sized by its container. */
export function AreaChart({ labels, values, height = 240, unit = '' }) {
  const [hover, setHover] = useState(null);
  const width = 720;
  const pad = { top: 16, right: 26, bottom: 28, left: 34 };
  const max = Math.max(4, Math.ceil(Math.max(...values, 1) * 1.15));
  const innerW = width - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;
  const x = (i) => pad.left + (values.length === 1 ? innerW / 2 : (i / (values.length - 1)) * innerW);
  const y = (v) => pad.top + innerH - (v / max) * innerH;

  // Catmull-Rom to Bezier for a soft line without overshooting below zero.
  const points = values.map((v, i) => [x(i), y(v)]);
  const line = points.reduce((d, p, i, arr) => {
    if (i === 0) return `M${p[0]},${p[1]}`;
    const p0 = arr[i - 2] || arr[i - 1];
    const p1 = arr[i - 1];
    const p3 = arr[i + 1] || p;
    const c1x = p1[0] + (p[0] - p0[0]) / 6;
    const c1y = Math.min(pad.top + innerH, p1[1] + (p[1] - p0[1]) / 6);
    const c2x = p[0] - (p3[0] - p1[0]) / 6;
    const c2y = Math.min(pad.top + innerH, p[1] - (p3[1] - p1[1]) / 6);
    return `${d} C${c1x},${c1y} ${c2x},${c2y} ${p[0]},${p[1]}`;
  }, '');
  const area = `${line} L${x(values.length - 1)},${pad.top + innerH} L${x(0)},${pad.top + innerH} Z`;
  const ticks = [0, 0.5, 1].map((t) => Math.round(max * t));
  const labelEvery = Math.ceil(values.length / 6);

  function onMove(e) {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - rect.left) / rect.width) * width;
    const index = Math.round(((px - pad.left) / innerW) * (values.length - 1));
    setHover(Math.max(0, Math.min(values.length - 1, index)));
  }

  const short = (iso) => new Date(`${iso}T00:00:00Z`).toLocaleDateString(undefined, { day: 'numeric', month: 'short', timeZone: 'UTC' });

  return (
    <div className="chart-wrap">
      <svg viewBox={`0 0 ${width} ${height}`} className="area-chart" role="img" aria-label="Activity over the last 30 days"
           onMouseMove={onMove} onMouseLeave={() => setHover(null)}>
        <defs>
          <linearGradient id="areaFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="var(--accent)" stopOpacity="0.28" />
            <stop offset="1" stopColor="var(--accent)" stopOpacity="0" />
          </linearGradient>
        </defs>
        {ticks.map((t) => (
          <g key={t}>
            <line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} className="chart-grid" />
            <text x={pad.left - 8} y={y(t) + 4} textAnchor="end" className="chart-axis">{t}</text>
          </g>
        ))}
        <path d={area} fill="url(#areaFill)" />
        <path d={line} fill="none" stroke="var(--accent)" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
        {labels.map((l, i) => (i % labelEvery === 0 || i === labels.length - 1) && (
          <text key={l} x={x(i)} y={height - 8} textAnchor="middle" className="chart-axis">{short(l)}</text>
        ))}
        {hover !== null && (
          <g>
            <line x1={x(hover)} x2={x(hover)} y1={pad.top} y2={pad.top + innerH} className="chart-cursor" />
            <circle cx={x(hover)} cy={y(values[hover])} r="5" fill="var(--surface)" stroke="var(--accent)" strokeWidth="2.4" />
          </g>
        )}
      </svg>
      {hover !== null && (
        <div className="chart-tip" style={{ left: `${(x(hover) / width) * 100}%` }}>
          <strong>{values[hover]}{unit}</strong>
          <span>{short(labels[hover])}</span>
        </div>
      )}
    </div>
  );
}

/** A ring showing one percentage, with the figure in the middle. */
export function Ring({ percent, label }) {
  const size = 148;
  const stroke = 12;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const value = percent == null ? 0 : Math.max(0, Math.min(100, percent));
  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={`${label}: ${percent == null ? 'no data' : `${Math.round(value)}%`}`}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--line)" strokeWidth={stroke} />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--accent)" strokeWidth={stroke} strokeLinecap="round"
                strokeDasharray={`${(value / 100) * c} ${c}`} transform={`rotate(-90 ${size / 2} ${size / 2})`} />
      </svg>
      <div className="ring-center">
        <strong>{percent == null ? '—' : `${Math.round(value)}%`}</strong>
        <span>{label}</span>
      </div>
    </div>
  );
}
