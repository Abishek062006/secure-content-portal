/** A code snippet from a question. The blank in a fill-the-code question (____) is picked out so it reads as a gap. */
export default function CodeBlock({ code }) {
  if (!code) return null;
  // Splitting on a capturing group puts each blank at an odd position.
  const parts = code.split(/(_{3,})/);
  return (
    <pre className="code-block" tabIndex={0} aria-label="Code snippet">
      <code>
        {parts.map((part, i) => (i % 2 === 1 ? <span key={i} className="code-blank">{part}</span> : part))}
      </code>
    </pre>
  );
}
