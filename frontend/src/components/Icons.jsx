/**
 * Small hand-drawn icon set — no external icon library or fetched images, so
 * there's nothing to license, nothing extra to load, and every icon inherits
 * its color from CSS (currentColor) to match whatever surface it sits on.
 */

export function Logo(props) {
  return (
    <svg width="30" height="30" viewBox="0 0 30 30" fill="none" {...props}>
      <rect width="30" height="30" rx="8" fill="url(#scp-logo-grad)" />
      <path
        d="M15 6.5l7 2.6v4.7c0 4.7-3 8.5-7 9.4-4-.9-7-4.7-7-9.4V9.1l7-2.6z"
        fill="#fff"
        fillOpacity="0.97"
      />
      <path
        d="M11.8 15.2l2.3 2.3 4-4.6"
        stroke="url(#scp-logo-grad)"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <defs>
        <linearGradient id="scp-logo-grad" x1="0" y1="0" x2="30" y2="30" gradientUnits="userSpaceOnUse">
          <stop stopColor="#0eae97" />
          <stop offset="1" stopColor="#06584c" />
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
