import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import Alert from '../components/Alert';

function formatDuration(totalSeconds) {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m`;
  return totalSeconds > 0 ? `${totalSeconds}s` : '0m';
}

function formatDate(iso) {
  return iso ? new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : null;
}

/** +4.2 points → "up 4 points", -1 → "down 1 point"; null (not enough attempts yet) renders nothing. */
function Trend({ value, unit = 'points' }) {
  if (value == null || Math.abs(value) < 0.5) return null;
  const up = value > 0;
  return (
    <span className={`insights-trend ${up ? 'up' : 'down'}`}>
      {up ? '↑' : '↓'} {up ? 'improving' : 'slipping'} · {Math.abs(value).toFixed(1)} {unit} vs your earlier average
    </span>
  );
}

function Stat({ value, label }) {
  return (
    <div className="ml-stat">
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

function Bar({ percent }) {
  return <div className="card-progress"><div className="card-progress-bar" style={{ width: `${Math.max(0, Math.min(100, percent))}%` }} /></div>;
}

function CategoryRow({ label, percent, valueLabel, count }) {
  return (
    <div className="insights-category-row">
      <span className="insights-category-label">{label}{count != null && <span className="insights-category-count"> ({count})</span>}</span>
      <Bar percent={percent} />
      <span className="insights-category-value">{valueLabel}</span>
    </div>
  );
}

export default function Progress() {
  const [data, setData] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    api.get('/api/me/insights').then(setData).catch((err) => setErrorMessage(err.message));
  }, []);

  if (errorMessage && !data) {
    return <div className="container"><Alert error={errorMessage} /></div>;
  }
  if (!data) {
    return <div className="container"><p className="pdf-loading">Loading…</p></div>;
  }

  const { overallCompletionPercent, coursesEnrolled, coursesCompleted, courses, totalWatchSeconds,
    quizzes, interviews, gamification } = data;

  const weakestCategory = quizzes.byCategory[0];
  const strongestCategory = quizzes.byCategory[quizzes.byCategory.length - 1];
  const hasCategorySpread = quizzes.byCategory.length > 1;
  const hardRow = quizzes.byDifficulty.find((d) => d.difficulty === 'HARD');
  const easyRow = quizzes.byDifficulty.find((d) => d.difficulty === 'EASY');

  const rubric = [
    ['Relevance', interviews.avgRelevance],
    ['Depth', interviews.avgDepth],
    ['Structure', interviews.avgStructure],
    ['Communication', interviews.avgCommunication],
  ].filter(([, v]) => v != null);
  const weakestQuestionType = interviews.byQuestionCategory[0];
  const strongestQuestionType = interviews.byQuestionCategory[interviews.byQuestionCategory.length - 1];

  return (
    <div className="container-wide">
      <Alert error={errorMessage} />
      <h1 className="page-title">Your progress</h1>
      <p className="field-hint">A detailed read on your courses, quizzes, assessments and interview practice — where you stand, and specifically what to work on next.</p>

      <section className="progress-card">
        <div className="ml-stats">
          <Stat value={`${overallCompletionPercent}%`} label="Overall completion" />
          <Stat value={coursesCompleted} label="Courses completed" />
          <Stat value={coursesEnrolled} label="Courses enrolled" />
          <Stat value={formatDuration(totalWatchSeconds)} label="Time watched" />
        </div>
      </section>

      {gamification && (
        <section className="progress-card">
          <header><h2>Streak and badges</h2></header>
          <div className="ml-stats">
            <Stat value={gamification.currentStreak} label="Day streak" />
            <Stat value={gamification.totalPoints} label="Points" />
            <Stat value={gamification.unlockedBadges} label="Badges" />
          </div>
        </section>
      )}

      <section className="progress-card">
        <header><h2>Course completion</h2></header>
        {courses.length === 0 ? (
          <p className="field-hint">You haven't enrolled in any courses yet. <Link to="/courses">Browse courses</Link>.</p>
        ) : (
          <ul className="insights-course-list">
            {courses.map((c) => (
              <li key={c.courseId}>
                <div className="insights-course-row">
                  <Link to={`/courses/${c.courseId}`}>{c.title}</Link>
                  <span className="field-hint">{c.progressPercent}%{c.completed ? ' · Complete' : ''}</span>
                </div>
                <Bar percent={c.progressPercent} />
                <p className="field-hint insights-course-meta">
                  {c.completedLessons} of {c.totalLessons} lesson{c.totalLessons === 1 ? '' : 's'}
                  {c.lastAccessedAt && ` · last opened ${formatDate(c.lastAccessedAt)}`}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="progress-card">
        <header><h2>Quiz and assessment performance</h2></header>
        {quizzes.attemptCount === 0 ? (
          <p className="field-hint">No graded attempts yet — quizzes and assessments inside your courses will show up here.</p>
        ) : (
          <>
            <p className="field-hint">
              {quizzes.attemptCount} attempt{quizzes.attemptCount === 1 ? '' : 's'} · average score {Math.round(quizzes.averageScore)}%
            </p>
            <Trend value={quizzes.recentTrend} unit="pts" />

            {(hasCategorySpread || hardRow) && (
              <div className="insights-callout">
                <h3>What to work on</h3>
                {hasCategorySpread && (
                  <p>
                    Strongest in <strong>{strongestCategory.category}</strong> ({Math.round(strongestCategory.averageScore)}% avg) —
                    spend more time on <strong>{weakestCategory.category}</strong> ({Math.round(weakestCategory.averageScore)}% avg,
                    {' '}{weakestCategory.attemptCount} attempt{weakestCategory.attemptCount === 1 ? '' : 's'}).
                  </p>
                )}
                {hardRow && (
                  <p>
                    {easyRow && easyRow.accuracyPercent - hardRow.accuracyPercent >= 15
                      ? <>You do well on easy questions ({Math.round(easyRow.accuracyPercent)}% correct) but hard questions are where
                          you're actually losing marks — only {Math.round(hardRow.accuracyPercent)}% correct
                          ({hardRow.correct} of {hardRow.total}). That's the highest-value place to review next.</>
                      : <>You're getting {Math.round(hardRow.accuracyPercent)}% of hard questions right ({hardRow.correct} of {hardRow.total}).</>}
                  </p>
                )}
              </div>
            )}

            {quizzes.byCategory.length > 0 && (
              <>
                <h3 className="insights-subhead">By course category</h3>
                <div className="insights-categories">
                  {quizzes.byCategory.map((c) => (
                    <CategoryRow key={c.category} label={c.category} percent={c.averageScore}
                                 valueLabel={`${Math.round(c.averageScore)}%`} count={c.attemptCount} />
                  ))}
                </div>
              </>
            )}

            {quizzes.byDifficulty.length > 0 && (
              <>
                <h3 className="insights-subhead">By difficulty</h3>
                <div className="insights-categories">
                  {quizzes.byDifficulty.map((d) => (
                    <CategoryRow key={d.difficulty} label={d.difficulty[0] + d.difficulty.slice(1).toLowerCase()}
                                 percent={d.accuracyPercent} valueLabel={`${Math.round(d.accuracyPercent)}%`} count={d.total} />
                  ))}
                </div>
              </>
            )}

            {quizzes.recent.length > 0 && (
              <>
                <h3 className="insights-subhead">Recent attempts</h3>
                <div className="table-wrap">
                  <table className="data-table">
                    <thead><tr><th>Assessment</th><th>Course</th><th>Score</th><th>Result</th><th>Date</th></tr></thead>
                    <tbody>
                      {quizzes.recent.map((a, i) => (
                        // eslint-disable-next-line react/no-array-index-key
                        <tr key={`${a.assessmentTitle}-${a.submittedAt}-${i}`}>
                          <td>{a.assessmentTitle}</td>
                          <td>{a.courseTitle || '—'}</td>
                          <td>{a.scorePercent}%</td>
                          <td>{a.passed == null ? '—' : a.passed ? <span className="badge status-published">Passed</span> : <span className="badge status-error">Failed</span>}</td>
                          <td>{formatDate(a.submittedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </>
        )}
      </section>

      <section className="progress-card">
        <header><h2>Mock interview performance</h2></header>
        {interviews.sessionsCompleted === 0 ? (
          <p className="field-hint">No completed interviews yet. <Link to="/interview">Practise one</Link>.</p>
        ) : (
          <>
            <p className="field-hint">
              {interviews.sessionsCompleted} completed · average score {Math.round(interviews.averageOverallScore)}%
            </p>
            <Trend value={interviews.recentTrend} unit="pts" />

            {(interviews.latestTopFix || interviews.weakest) && (
              <div className="insights-callout">
                <h3>What to work on</h3>
                {interviews.latestTopFix && <p>{interviews.latestTopFix}</p>}
                {interviews.weakest && (
                  <p>
                    Across your interviews, <strong>{interviews.weakest}</strong> is your weakest of the four scored dimensions
                    {interviews.strongest && <> — <strong>{interviews.strongest}</strong> is where you're strongest</>}.
                  </p>
                )}
                {weakestQuestionType && interviews.byQuestionCategory.length > 1 && (
                  <p>
                    By question type, <strong>{weakestQuestionType.category}</strong> questions score lowest
                    ({weakestQuestionType.averageScore.toFixed(1)}/10{strongestQuestionType && strongestQuestionType.category !== weakestQuestionType.category
                      && <> vs <strong>{strongestQuestionType.category}</strong> at {strongestQuestionType.averageScore.toFixed(1)}/10</>}).
                  </p>
                )}
              </div>
            )}

            {rubric.length > 0 && (
              <>
                <h3 className="insights-subhead">By competency</h3>
                <div className="insights-categories">
                  {rubric.map(([label, v]) => (
                    <CategoryRow key={label} label={label} percent={(v / 10) * 100} valueLabel={`${v.toFixed(1)}/10`} />
                  ))}
                </div>
              </>
            )}

            {interviews.byQuestionCategory.length > 0 && (
              <>
                <h3 className="insights-subhead">By question type</h3>
                <div className="insights-categories">
                  {interviews.byQuestionCategory.map((c) => (
                    <CategoryRow key={c.category} label={c.category} percent={(c.averageScore / 10) * 100}
                                 valueLabel={`${c.averageScore.toFixed(1)}/10`} count={c.questionCount} />
                  ))}
                </div>
              </>
            )}

            {interviews.recent.length > 0 && (
              <>
                <h3 className="insights-subhead">Recent interviews</h3>
                <div className="table-wrap">
                  <table className="data-table">
                    <thead><tr><th>Role</th><th>Score</th><th>Readiness</th><th>Date</th></tr></thead>
                    <tbody>
                      {interviews.recent.map((s, i) => (
                        // eslint-disable-next-line react/no-array-index-key
                        <tr key={`${s.targetRole}-${s.completedAt}-${i}`}>
                          <td>{s.targetRole}</td>
                          <td>{s.overallScore}%</td>
                          <td>{s.readinessLevel}</td>
                          <td>{formatDate(s.completedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </>
        )}
      </section>
    </div>
  );
}
