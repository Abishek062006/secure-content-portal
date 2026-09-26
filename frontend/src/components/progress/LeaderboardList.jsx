import Avatar from '../Avatar';

/** Ranked rows, with the viewer's own row shown below a gap when they are outside the list. */
export default function LeaderboardList({ entries, mine, empty }) {
  const rows = entries || [];
  const mineListed = rows.some((row) => row.isCurrentUser);
  if (rows.length === 0 && !mine) {
    return <p className="field-hint">{empty}</p>;
  }
  return (
    <ol className="board">
      {rows.map((row) => <Row key={row.userId} row={row} />)}
      {mine && !mineListed && (
        <>
          <li className="board-gap" aria-hidden="true">···</li>
          <Row row={mine} />
        </>
      )}
    </ol>
  );
}

function Row({ row }) {
  return (
    <li className={`board-row${row.isCurrentUser ? ' me' : ''}${row.rank <= 3 ? ` top top-${row.rank}` : ''}`}>
      <span className="board-rank">{row.rank}</span>
      <Avatar name={row.displayName} url={row.pictureUrl} userId={row.userId} size={34} />
      <span className="board-name">{row.displayName}{row.isCurrentUser && <em> (you)</em>}</span>
      <span className="board-points">{row.points.toLocaleString()} XP</span>
    </li>
  );
}
