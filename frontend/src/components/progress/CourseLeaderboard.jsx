import { useEffect, useState } from 'react';
import { api } from '../../api';
import LeaderboardList from './LeaderboardList';

const SHOWN = 5;

/** This week's ranking for one course, so a learner competes with the people taking the same thing. */
export default function CourseLeaderboard({ courseId }) {
  const [board, setBoard] = useState(null);

  useEffect(() => {
    let cancelled = false;
    api.getLeaderboard('weekly', courseId)
      .then((res) => { if (!cancelled) setBoard(res); })
      .catch(() => { if (!cancelled) setBoard(null); });
    return () => { cancelled = true; };
  }, [courseId]);

  if (!board) return null;
  const entries = [...board.podium, ...board.rankings].slice(0, SHOWN);
  return (
    <section className="progress-card course-board">
      <header>
        <h2>This week in this course</h2>
        <span className="field-hint">{board.totalParticipants} learner{board.totalParticipants === 1 ? '' : 's'}</span>
      </header>
      <LeaderboardList entries={entries} mine={board.currentUserRank}
                       empty="Finish a lesson this week to start the ranking." />
    </section>
  );
}
