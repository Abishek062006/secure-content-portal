import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import Icon from '../components/Icon';
import Alert from '../components/Alert';
import { useAuth } from '../context/AuthContext';
import '../styles/hackathons.css';

export default function HackathonLeaderboard() {
  const { id } = useParams();
  const { user } = useAuth();
  const [hackathon, setHackathon] = useState(null);
  const [standings, setStandings] = useState([]);
  const [problems, setProblems] = useState([]);
  const [certificate, setCertificate] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Filters
  const [selectedTrack, setSelectedTrack] = useState('ALL');
  const [selectedProblem, setSelectedProblem] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    Promise.all([
      api.getHackathon(id),
      api.getHackathonProblems(id).catch(() => []),
    ])
      .then(([event, probList]) => {
        if (!active) return;
        setHackathon(event);
        setProblems(probList || []);

        if (event.resultsPublished) {
          api.getHackathonResults(id)
            .then((res) => {
              if (active) setStandings(res || []);
            })
            .catch((err) => {
              if (active) setError(err.message || 'Could not load leaderboard results.');
            });

          if (user && !user.admin) {
            api.getHackathonCertificate(id)
              .then((cert) => {
                if (active) setCertificate(cert);
              })
              .catch(() => {});
          }
        }
      })
      .catch((err) => {
        if (active) setError(err.message || 'Failed to load hackathon.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [id, user]);

  // Available tracks for filter
  const tracks = useMemo(() => {
    if (!hackathon?.tracks) return [];
    return hackathon.tracks.split(',').map((t) => t.trim()).filter(Boolean);
  }, [hackathon]);

  // Filtered rows
  const filteredStandings = useMemo(() => {
    return standings.filter((row) => {
      if (selectedTrack !== 'ALL' && row.track !== selectedTrack) {
        return false;
      }
      if (selectedProblem !== 'ALL' && row.problemStatementTitle !== selectedProblem) {
        return false;
      }
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchTeam = row.teamName?.toLowerCase().includes(q);
        const matchTitle = row.title?.toLowerCase().includes(q);
        const matchMembers = row.members?.some((m) => m.toLowerCase().includes(q));
        if (!matchTeam && !matchTitle && !matchMembers) return false;
      }
      return true;
    });
  }, [standings, selectedTrack, selectedProblem, searchQuery]);

  // Top 3 for podium
  const topThree = useMemo(() => {
    return standings.slice(0, 3);
  }, [standings]);

  if (loading) {
    return (
      <div className="container" style={{ padding: '40px 0' }}>
        <p className="muted">Loading leaderboard…</p>
      </div>
    );
  }

  if (error && !hackathon) {
    return (
      <div className="container" style={{ padding: '40px 0' }}>
        <Alert error={error} />
        <Link to="/hackathons" className="btn" style={{ marginTop: '16px' }}>
          Back to Hackathons
        </Link>
      </div>
    );
  }

  const isPublished = hackathon?.resultsPublished;

  return (
    <div className="container hackathon-leaderboard-page" style={{ paddingBottom: '60px' }}>
      {/* Top Nav Breadcrumb */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '20px 0 16px' }}>
        <Link to={`/hackathons/${id}`} className="interview-exit" style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
          <Icon name="arrow-left" size={16} /> Back to Hackathon
        </Link>
        <div style={{ display: 'flex', gap: '10px' }}>
          <Link to={`/hackathons/${id}/workspace`} className="btn btn-sm">
            Workspace
          </Link>
          {certificate && (
            <Link to={`/hackathons/${id}/certificate`} className="btn btn-sm btn-primary">
              <Icon name="award" size={14} /> My Certificate
            </Link>
          )}
        </div>
      </div>

      {/* Header Banner */}
      <div className="leaderboard-hero">
        <div>
          <span className="badge" style={{ background: isPublished ? '#ecfdf5' : '#fffbeb', color: isPublished ? '#065f46' : '#92400e', marginBottom: '8px' }}>
            {isPublished ? 'Official Final Results' : 'Results Pending'}
          </span>
          <h1 style={{ margin: '4px 0 8px', fontSize: '1.8rem', fontWeight: 800 }}>
            {hackathon?.title} · Leaderboard
          </h1>
          <p style={{ margin: 0, color: 'var(--ink-mid)', fontSize: '0.95rem' }}>
            {isPublished
              ? 'Final scores and rankings decided by the judging panel.'
              : 'The hackathon is currently in the evaluation phase. Standings will appear once an administrator publishes official results.'}
          </p>
        </div>
        {certificate && (
          <div style={{ background: '#fff', border: '1px solid #facc15', padding: '12px 18px', borderRadius: 'var(--radius)', textAlign: 'center' }}>
            <div style={{ fontSize: '0.8rem', color: '#854d0e', fontWeight: 700, textTransform: 'uppercase' }}>
              Congratulations!
            </div>
            <div style={{ fontSize: '1.05rem', fontWeight: 800, color: '#1e3a8a', margin: '2px 0 6px' }}>
              {certificate.type === 'WINNER' ? '🏆 Winner Certificate' : (certificate.type === 'RUNNER_UP' ? '🥈 Runner-Up Certificate' : '📜 Participation Certificate')}
            </div>
            <Link to={`/hackathons/${id}/certificate`} className="btn btn-sm btn-primary" style={{ width: '100%' }}>
              View & Download PDF
            </Link>
          </div>
        )}
      </div>

      {!isPublished ? (
        <div className="empty-state" style={{ padding: '60px 20px', textAlign: 'center', background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 'var(--radius)' }}>
          <Icon name="clock" size={48} style={{ color: 'var(--ink-soft)', marginBottom: '16px' }} />
          <h2 style={{ fontSize: '1.3rem', fontWeight: 700, margin: '0 0 8px' }}>Results Have Not Been Published Yet</h2>
          <p style={{ color: 'var(--ink-mid)', maxWidth: '500px', margin: '0 auto 20px' }}>
            Judges are actively evaluating submissions or finalizing scores. As soon as the event administrator concludes judging and publishes results, the official podium and rankings will unlock here.
          </p>
          <div style={{ display: 'flex', gap: '12px', justifyContent: 'center' }}>
            <Link to={`/hackathons/${id}`} className="btn">
              Hackathon Overview
            </Link>
            <Link to={`/hackathons/${id}/workspace`} className="btn btn-primary">
              Open Workspace
            </Link>
          </div>
        </div>
      ) : standings.length === 0 ? (
        <div className="empty-state" style={{ padding: '40px 20px', textAlign: 'center' }}>
          <p>No project submissions were scored for this hackathon.</p>
        </div>
      ) : (
        <>
          {/* Top 3 Podium Highlights */}
          {topThree.length > 0 && (
            <section style={{ marginBottom: '32px' }}>
              <h2 style={{ fontSize: '1.15rem', fontWeight: 700, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Icon name="award" size={20} style={{ color: '#eab308' }} /> Winners Podium
              </h2>
              <div className="leaderboard-podium">
                {topThree.map((item) => {
                  const rankClass = item.rank === 1 ? 'rank-1' : (item.rank === 2 ? 'rank-2' : 'rank-3');
                  const badgeClass = item.rank === 1 ? 'gold' : (item.rank === 2 ? 'silver' : 'bronze');
                  return (
                    <div key={item.teamName} className={`podium-card ${rankClass}`}>
                      <div className={`podium-badge ${badgeClass}`}>
                        #{item.rank}
                      </div>
                      <h3 className="podium-team">{item.teamName}</h3>
                      <p className="podium-project">{item.title}</p>
                      {item.track && (
                        <span className="badge" style={{ background: 'var(--surface-alt)', color: 'var(--ink-mid)' }}>
                          {item.track}
                        </span>
                      )}
                      <div className="podium-score">
                        {item.overall.toFixed(1)} <span>/ 10</span>
                      </div>
                      <div style={{ fontSize: '0.8rem', color: 'var(--ink-soft)' }}>
                        {item.members?.join(', ')}
                      </div>
                      <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                        {item.repoUrl && (
                          <a href={item.repoUrl} target="_blank" rel="noreferrer" className="btn btn-sm" title="View Code">
                            <Icon name="github" size={14} /> Code
                          </a>
                        )}
                        {item.demoUrl && (
                          <a href={item.demoUrl} target="_blank" rel="noreferrer" className="btn btn-sm btn-primary" title="View Demo">
                            <Icon name="external-link" size={14} /> Demo
                          </a>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>
          )}

          {/* Filters Bar */}
          <div className="leaderboard-filters">
            <div style={{ flex: '1 1 200px' }}>
              <input
                type="text"
                className="input"
                placeholder="Search team, project, member…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{ width: '100%' }}
              />
            </div>

            {tracks.length > 0 && (
              <div style={{ flex: '0 1 180px' }}>
                <select
                  className="select"
                  value={selectedTrack}
                  onChange={(e) => setSelectedTrack(e.target.value)}
                  style={{ width: '100%' }}
                >
                  <option value="ALL">All Tracks ({tracks.length})</option>
                  {tracks.map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </div>
            )}

            {problems.length > 0 && (
              <div style={{ flex: '0 1 240px' }}>
                <select
                  className="select"
                  value={selectedProblem}
                  onChange={(e) => setSelectedProblem(e.target.value)}
                  style={{ width: '100%' }}
                >
                  <option value="ALL">All Problem Statements ({problems.length})</option>
                  {problems.map((p) => (
                    <option key={p.id} value={p.title}>{p.title}</option>
                  ))}
                </select>
              </div>
            )}

            {(selectedTrack !== 'ALL' || selectedProblem !== 'ALL' || searchQuery) && (
              <button
                className="btn btn-sm"
                onClick={() => {
                  setSelectedTrack('ALL');
                  setSelectedProblem('ALL');
                  setSearchQuery('');
                }}
              >
                Reset Filters
              </button>
            )}
          </div>

          {/* Full Standings Table */}
          <div className="leaderboard-table-wrap">
            <table className="leaderboard-table">
              <thead>
                <tr>
                  <th style={{ width: '60px', textAlign: 'center' }}>Rank</th>
                  <th>Team & Members</th>
                  <th>Project / Challenge</th>
                  <th>Score Breakdown (1-10)</th>
                  <th style={{ textAlign: 'right' }}>Final Score</th>
                  <th style={{ width: '110px', textAlign: 'center' }}>Links</th>
                </tr>
              </thead>
              <tbody>
                {filteredStandings.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ textAlign: 'center', padding: '32px', color: 'var(--ink-soft)' }}>
                      No teams match your filter criteria.
                    </td>
                  </tr>
                ) : (
                  filteredStandings.map((row) => {
                    const isTop = row.rank <= 3;
                    const rankStyle = row.rank === 1 ? 'gold' : (row.rank === 2 ? 'silver' : (row.rank === 3 ? 'bronze' : 'normal'));
                    return (
                      <tr key={row.teamName} className={isTop ? 'top-rank' : ''}>
                        <td style={{ textAlign: 'center' }}>
                          <span className={`rank-indicator ${rankStyle}`}>
                            {row.rank}
                          </span>
                        </td>
                        <td>
                          <div style={{ fontWeight: 700, fontSize: '0.98rem', color: 'var(--ink)' }}>
                            {row.teamName}
                          </div>
                          <div style={{ fontSize: '0.8rem', color: 'var(--ink-soft)', marginTop: '2px' }}>
                            {row.members?.join(', ') || 'No members listed'}
                          </div>
                          {row.track && (
                            <span className="badge" style={{ marginTop: '4px', fontSize: '0.7rem' }}>
                              Track: {row.track}
                            </span>
                          )}
                        </td>
                        <td>
                          <div style={{ fontWeight: 600, color: 'var(--ink)' }}>
                            {row.title}
                          </div>
                          {row.problemStatementTitle ? (
                            <div style={{ fontSize: '0.8rem', color: 'var(--accent-deep)', marginTop: '2px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                              <Icon name="check-circle" size={12} /> {row.problemStatementTitle}
                            </div>
                          ) : (
                            <div style={{ fontSize: '0.78rem', color: 'var(--ink-soft)', fontStyle: 'italic', marginTop: '2px' }}>
                              Open Innovation / General
                            </div>
                          )}
                        </td>
                        <td>
                          <div className="breakdown-bars">
                            <div className="breakdown-bar-item" title={`Innovation: ${row.innovation.toFixed(1)}/10`}>
                              <span className="breakdown-bar-label">Innov</span>
                              <div className="breakdown-bar-track">
                                <div className="breakdown-bar-fill innov" style={{ width: `${(row.innovation / 10) * 100}%` }} />
                              </div>
                              <span className="breakdown-bar-val">{row.innovation.toFixed(1)}</span>
                            </div>
                            <div className="breakdown-bar-item" title={`Execution: ${row.execution.toFixed(1)}/10`}>
                              <span className="breakdown-bar-label">Exec</span>
                              <div className="breakdown-bar-track">
                                <div className="breakdown-bar-fill exec" style={{ width: `${(row.execution / 10) * 100}%` }} />
                              </div>
                              <span className="breakdown-bar-val">{row.execution.toFixed(1)}</span>
                            </div>
                            <div className="breakdown-bar-item" title={`Impact: ${row.impact.toFixed(1)}/10`}>
                              <span className="breakdown-bar-label">Impact</span>
                              <div className="breakdown-bar-track">
                                <div className="breakdown-bar-fill imp" style={{ width: `${(row.impact / 10) * 100}%` }} />
                              </div>
                              <span className="breakdown-bar-val">{row.impact.toFixed(1)}</span>
                            </div>
                            <div className="breakdown-bar-item" title={`Presentation: ${row.presentation.toFixed(1)}/10`}>
                              <span className="breakdown-bar-label">Pres</span>
                              <div className="breakdown-bar-track">
                                <div className="breakdown-bar-fill pres" style={{ width: `${(row.presentation / 10) * 100}%` }} />
                              </div>
                              <span className="breakdown-bar-val">{row.presentation.toFixed(1)}</span>
                            </div>
                          </div>
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <div style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--ink)' }}>
                            {row.overall.toFixed(1)}
                          </div>
                          <div style={{ fontSize: '0.72rem', color: 'var(--ink-soft)' }}>
                            {row.scoreCount} {row.scoreCount === 1 ? 'judge' : 'judges'}
                          </div>
                        </td>
                        <td style={{ textAlign: 'center' }}>
                          <div style={{ display: 'inline-flex', gap: '6px' }}>
                            {row.repoUrl && (
                              <a href={row.repoUrl} target="_blank" rel="noreferrer" className="notification-mini-btn" title="GitHub Code Repository">
                                <Icon name="github" size={16} />
                              </a>
                            )}
                            {row.demoUrl && (
                              <a href={row.demoUrl} target="_blank" rel="noreferrer" className="notification-mini-btn" title="Live Interactive Demo">
                                <Icon name="external-link" size={16} />
                              </a>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
