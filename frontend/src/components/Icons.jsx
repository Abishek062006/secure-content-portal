/**
 * Small hand-drawn icon set — no external icon library or fetched images, so
 * there's nothing to license, nothing extra to load, and every icon inherits
 * its color from CSS (currentColor) to match whatever surface it sits on.
 */

/**
 * A document with a folded corner and a leaf wrapping its bottom edge —
 * content that's alive/organic rather than locked down, matching the
 * softer glassmorphic redesign of the rest of the UI.
 */
export function Logo(props) {
  return (
    <svg width="30" height="30" viewBox="0 0 30 30" fill="none" {...props}>
      <rect width="30" height="30" rx="9" fill="url(#scp-tile-grad)" />
      <rect x="8.6" y="9.6" width="12.4" height="16.2" rx="2.3" transform="rotate(-4 8.6 9.6)" fill="#cdeadb" />
      <path
        d="M10.4 6.5h7.9l3.3 3.3v13.4a1.9 1.9 0 0 1-1.9 1.9H10.4a1.9 1.9 0 0 1-1.9-1.9V8.4a1.9 1.9 0 0 1 1.9-1.9Z"
        fill="url(#scp-paper-grad)"
      />
      <path d="M18.3 6.5l3.3 3.3h-2.2a1.1 1.1 0 0 1-1.1-1.1V6.5Z" fill="#d8ecdf" />
      <rect x="10.6" y="13.6" width="7.2" height="1.35" rx="0.675" fill="#a9d3bb" />
      <rect x="10.6" y="16.7" width="7.2" height="1.35" rx="0.675" fill="#a9d3bb" />
      <rect x="10.6" y="19.8" width="4.6" height="1.35" rx="0.675" fill="#a9d3bb" />
      <path
        d="M17.3 21c3.4-2.3 7-1.7 8.7 1.1-2.6 2.3-6.4 2-8.7-1.1Z"
        fill="url(#scp-leaf-grad)"
      />
      <defs>
        <linearGradient id="scp-tile-grad" x1="0" y1="0" x2="30" y2="30" gradientUnits="userSpaceOnUse">
          <stop stopColor="#f3faf6" />
          <stop offset="1" stopColor="#dff1e6" />
        </linearGradient>
        <linearGradient id="scp-paper-grad" x1="8.5" y1="6.5" x2="21.5" y2="25.1" gradientUnits="userSpaceOnUse">
          <stop stopColor="#ffffff" />
          <stop offset="1" stopColor="#eaf6ee" />
        </linearGradient>
        <linearGradient id="scp-leaf-grad" x1="17.3" y1="23.4" x2="26" y2="19.6" gradientUnits="userSpaceOnUse">
          <stop stopColor="#1f8a4c" />
          <stop offset="1" stopColor="#8fd96f" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function VideoIcon(props) {
  return (
    <svg width="32" height="32" viewBox="0 0 34 34" fill="none" {...props}>
      <rect x="2" y="6" width="30" height="22" rx="5" stroke="currentColor" strokeWidth="2" />
      <path d="M14 12.5v9l8-4.5-8-4.5z" fill="currentColor" />
    </svg>
  );
}

export function PdfIcon(props) {
  return (
    <svg width="32" height="32" viewBox="0 0 34 34" fill="none" {...props}>
      <path
        d="M9 3h11l6 6v20a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinejoin="round"
      />
      <path d="M20 3v6h6" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
      <path d="M11 19h12M11 23h12M11 15h6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

export function HtmlIcon(props) {
  return (
    <svg width="32" height="32" viewBox="0 0 34 34" fill="none" {...props}>
      <rect x="3" y="5" width="28" height="24" rx="4" stroke="currentColor" strokeWidth="2" />
      <path d="M3 11h28" stroke="currentColor" strokeWidth="2" />
      <circle cx="7.5" cy="8" r="1" fill="currentColor" />
      <circle cx="11" cy="8" r="1" fill="currentColor" />
      <path
        d="M13 20l-3 3 3 3M21 20l3 3-3 3M18.5 18l-3 10"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function LockIcon(props) {
  return (
    <svg width="24" height="24" viewBox="0 0 26 26" fill="none" {...props}>
      <rect x="5" y="12" width="16" height="11" rx="3" stroke="currentColor" strokeWidth="2" />
      <path d="M8.5 12V8.5a4.5 4.5 0 1 1 9 0V12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <circle cx="13" cy="17.5" r="1.6" fill="currentColor" />
    </svg>
  );
}

export function ShieldPlayIcon(props) {
  return (
    <svg width="24" height="24" viewBox="0 0 26 26" fill="none" {...props}>
      <path
        d="M13 3l8 3v6.2c0 5.2-3.4 9.5-8 10.8-4.6-1.3-8-5.6-8-10.8V6l8-3z"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinejoin="round"
      />
      <path d="M11 9.5v7l6-3.5-6-3.5z" fill="currentColor" />
    </svg>
  );
}

export function UsersGearIcon(props) {
  return (
    <svg width="24" height="24" viewBox="0 0 26 26" fill="none" {...props}>
      <circle cx="9" cy="8.5" r="3.2" stroke="currentColor" strokeWidth="2" />
      <path d="M3 21c0-3.6 2.7-6 6-6s6 2.4 6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <circle cx="19" cy="8" r="2" stroke="currentColor" strokeWidth="1.8" />
      <path d="M19 15.5c2.2 0 4 1.8 4 5.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

export function contentTypeIcon(type, props) {
  if (type === 'VIDEO') return <VideoIcon {...props} />;
  if (type === 'PDF') return <PdfIcon {...props} />;
  if (type === 'HTML') return <HtmlIcon {...props} />;
  return null;
}
