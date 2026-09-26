import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import Alert from '../components/Alert';
import Icon from '../components/Icon';
import DailyRing from '../components/progress/DailyRing';
import LeaderboardList from '../components/progress/LeaderboardList';

const SHOWN = 10;
const PERIODS = [
  { id: 'weekly', label: 'This week' },
  { id: 'all_time', label: 'All time' },
];

export default function Leaderboard() {
  const [period, setPeriod] = useState('weekly');
  const [board, setBoard] = useState(null);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const loadBoard = useCallback(() => {
    api.getLeaderboard(period).then(setBoard).catch((err) => setError(err.message));
  }, [period]);

  useEffect(() => { loadBoard(); }, [loadBoard]);
  useEffect(() => {
    api.getGamificationSummary().then(setSummary).catch((err) => setError(err.message));
  }, []);

  async function setHidden(hidden) {
    setSaving(true);
    try {
      setSummary(await api.setLeaderboardHidden(hidden));
      loadBoard();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  const entries = board ? [...board.podium, ...board.rankings].slice(0, SHOWN) : [];

  return (
    <div className="container">
      <Alert error={error} />
      <h1 className="page-title">Progress</h1>

      {summary && (
        <section className="progress-card progress-summary">
          <DailyRing done={summary.activitiesToday} goal={summary.dailyGoal} streak={summary.currentStreak} safe={summary.checkedInToday} />
          <div className="progress-stat">
            <strong>{summary.currentStreak}</strong>
            <span>day streak</span>
          </div>
          <div className="progress-stat">
            <strong>{summary.activitiesToday}/{summary.dailyGoal}</strong>
            <span>finished today</span>
          </div>
          <div className="progress-stat">
            <strong>{summary.totalPoints.toLocaleString()}</strong>
            <span>total XP</span>
          </div>
          <div className="progress-stat">
            <strong>{summary.maxStreak}</strong>
            <span>best streak</span>
          </div>
          <Link className="btn btn-sm" to="/profile">
            <Icon name="award" size={16} /> {summary.unlockedBadgesCount} badge{summary.unlockedBadgesCount === 1 ? '' : 's'}
          </Link>
        </section>
      )}

      <section className="progress-card">
        <header>
          <h2>Leaderboard</h2>
          <div className="segmented" role="tablist" aria-label="Time period">
            {PERIODS.map((p) => (
              <button key={p.id} type="button" role="tab" aria-selected={period === p.id}
                      className={period === p.id ? 'active' : ''} onClick={() => setPeriod(p.id)}>
                {p.label}
              </button>
            ))}
          </div>
        </header>

        {summary?.leaderboardHidden && (
          <p className="field-hint">You're hidden from leaderboards. Your progress and badges are unaffected.</p>
        )}
        {board && (
          <LeaderboardList entries={entries} mine={board.currentUserRank}
                           empty={period === 'weekly' ? 'No one has finished anything this week yet. Be the first.' : 'No rankings yet.'} />
        )}

        {summary && (
          <label className="board-toggle">
            <input type="checkbox" checked={!summary.leaderboardHidden} disabled={saving}
                   onChange={(e) => setHidden(!e.target.checked)} />
            Show me on leaderboards
          </label>
        )}
      </section>
    </div>
  );
}
