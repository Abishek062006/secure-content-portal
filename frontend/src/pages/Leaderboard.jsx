import { useState, useEffect } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';

function initials(name) {
  const source = (name || '').trim();
  if (!source) return '?';
  const parts = source.split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 1).toUpperCase();
  return (parts[0].slice(0, 1) + parts[parts.length - 1].slice(0, 1)).toUpperCase();
}

const TIMEFRAMES = [
  { id: 'today', label: 'Today' },
  { id: 'this_week', label: 'This Week' },
  { id: 'this_month', label: 'This Month' },
  { id: 'all_time', label: 'All Time' },
];

const BADGE_CATEGORIES = [
  { id: 'ALL', label: 'All Badges' },
  { id: 'MILESTONE', label: 'Milestones' },
  { id: 'MASTERY', label: 'Mastery' },
  { id: 'STREAK', label: 'Streaks' },
  { id: 'STREAM', label: 'Stream Pioneers' },
];

export default function Leaderboard() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState('leaderboard'); // 'leaderboard' | 'badges' | 'history'
  const [timeframe, setTimeframe] = useState('all_time');
  const [stream, setStream] = useState('all');
  const [availableStreams, setAvailableStreams] = useState([]);
  
  const [leaderboardData, setLeaderboardData] = useState(null);
  const [badgesData, setBadgesData] = useState([]);
  const [summaryData, setSummaryData] = useState(null);
  const [pointHistory, setPointHistory] = useState([]);
  const [badgeCategory, setBadgeCategory] = useState('ALL');
  
  const [loading, setLoading] = useState(true);
  const [claiming, setClaiming] = useState(false);
  const [claimMessage, setClaimMessage] = useState(null);

  // Admin section state
  const [isAdminView, setIsAdminView] = useState(false);
  const [adminTab, setAdminTab] = useState('rankings'); // 'rankings' | 'rules' | 'history'
  const [adminData, setAdminData] = useState(null);
  const [pointRules, setPointRules] = useState([]);
  const [adminHistory, setAdminHistory] = useState([]);
  const [editingRule, setEditingRule] = useState(null);
  const [ruleValue, setRuleValue] = useState('');
  
  // Adjust XP Modal State
  const [adjustModalOpen, setAdjustModalOpen] = useState(false);
  const [adjustTargetUser, setAdjustTargetUser] = useState(null);
  const [adjustAmount, setAdjustAmount] = useState('');
  const [adjustReason, setAdjustReason] = useState('');
  const [adjustError, setAdjustError] = useState(null);
  const [adjustSuccess, setAdjustSuccess] = useState(null);

  useEffect(() => {
    loadStreams();
    loadSummary();
    loadBadges();
  }, []);

  useEffect(() => {
    if (isAdminView) {
      loadAdminData();
      loadAdminRules();
      loadAdminHistory();
    } else {
      loadLeaderboard();
    }
  }, [timeframe, stream, isAdminView]);

  async function loadStreams() {
    try {
      const data = await api.getStreams();
      if (Array.isArray(data)) {
        setAvailableStreams(data);
      }
    } catch {
      setAvailableStreams([
        'Engineering & Web Dev',
        'AI & Data Science',
        'UI/UX & Design',
        'Cloud & Infrastructure'
      ]);
    }
  }

  async function loadSummary() {
    try {
      const data = await api.getGamificationSummary();
      setSummaryData(data);
    } catch (err) {
      console.error('Failed to load summary:', err);
    }
  }

  async function loadLeaderboard() {
    setLoading(true);
    try {
      const data = await api.getLeaderboard(timeframe, stream);
      setLeaderboardData(data);
    } catch (err) {
      console.error('Failed to load leaderboard:', err);
    } finally {
      setLoading(false);
    }
  }

  async function loadBadges() {
    try {
      const data = await api.getBadges();
      setBadgesData(data);
    } catch (err) {
      console.error('Failed to load badges:', err);
    }
  }

  async function loadPointHistory() {
    try {
      const data = await api.getMyPointHistory();
      setPointHistory(data || []);
    } catch (err) {
      console.error('Failed to load point history:', err);
    }
  }

  async function loadAdminData() {
    setLoading(true);
    try {
      const data = await api.getAdminLeaderboard(timeframe, stream);
      setAdminData(data);
    } catch (err) {
      console.error('Failed to load admin leaderboard:', err);
    } finally {
      setLoading(false);
    }
  }

  async function loadAdminRules() {
    try {
      const rules = await api.getAdminPointRules();
      setPointRules(rules || []);
    } catch (err) {
      console.error('Failed to load XP rules:', err);
    }
  }

  async function loadAdminHistory() {
    try {
      const history = await api.getAdminPointHistory();
      setAdminHistory(history || []);
    } catch (err) {
      console.error('Failed to load admin history:', err);
    }
  }

  async function handleCheckIn() {
    if (claiming || (summaryData && summaryData.checkedInToday)) return;
    setClaiming(true);
    setClaimMessage(null);
    try {
      const res = await api.checkInDaily();
      setClaimMessage({
        type: 'success',
        text: `Checked in successfully! +${res.pointsEarned} XP earned. Streak: ${res.newStreak} day(s).`
      });
      loadSummary();
      loadLeaderboard();
    } catch (err) {
      setClaimMessage({ type: 'error', text: err.message || 'Check-in failed' });
    } finally {
      setClaiming(false);
    }
  }

  async function handleSaveRule(actionType) {
    const pts = parseInt(ruleValue, 10);
    if (isNaN(pts) || pts < 0) {
      alert('Points must be a valid non-negative integer.');
      return;
    }
    try {
      await api.updateAdminPointRule(actionType, pts);
      setEditingRule(null);
      loadAdminRules();
    } catch (err) {
      alert('Failed to update rule: ' + err.message);
    }
  }

  async function handleSubmitAdjustXP(e) {
    e.preventDefault();
    setAdjustError(null);
    setAdjustSuccess(null);

    const amt = parseInt(adjustAmount, 10);
    if (isNaN(amt) || amt === 0) {
      setAdjustError('Please enter a valid non-zero integer amount.');
      return;
    }
    if (!adjustReason || !adjustReason.trim()) {
      setAdjustError('Reason is required for audit logging.');
      return;
    }

    try {
      await api.adjustAdminUserXp(adjustTargetUser.userId, amt, adjustReason.trim());
      setAdjustSuccess(`Successfully adjusted ${amt > 0 ? '+' : ''}${amt} XP for ${adjustTargetUser.displayName}`);
      setTimeout(() => {
        setAdjustModalOpen(false);
        setAdjustTargetUser(null);
        setAdjustAmount('');
        setAdjustReason('');
        setAdjustSuccess(null);
        loadAdminData();
        loadAdminHistory();
      }, 1200);
    } catch (err) {
      setAdjustError(err.message || 'Adjustment failed');
    }
  }

  const filteredBadges = badgeCategory === 'ALL'
    ? badgesData
    : badgesData.filter(b => b.category === badgeCategory);

  const unlockedCount = badgesData.filter(b => b.unlocked).length;

  return (
    <div className="container-wide" style={{ width: '100%', maxWidth: '1100px', margin: '0 auto', padding: '2rem 1.5rem' }}>
      {/* Header Banner */}
      <div
        style={{
          background: 'linear-gradient(135deg, #ffffff 0%, #f0fdf4 100%)',
          border: '1px solid #e2e8f0',
          borderRadius: '16px',
          padding: '2rem',
          marginBottom: '1.75rem',
          boxShadow: '0 4px 20px rgba(0, 0, 0, 0.03)'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <h1 style={{ margin: 0, fontSize: '1.85rem', fontWeight: 800, color: '#0f172a', letterSpacing: '-0.02em' }}>
                Leaderboard & Achievements
              </h1>
              <span style={{ background: '#dcfce7', color: '#166534', fontSize: '0.75rem', fontWeight: 700, padding: '4px 10px', borderRadius: '980px', textTransform: 'uppercase' }}>
                Live XP
              </span>
            </div>
            <p style={{ margin: '0.5rem 0 0', color: '#64748b', fontSize: '1rem' }}>
              Compete across learning streams, track daily streaks, earn XP, and collect badges.
            </p>
          </div>
        </div>

        {/* Top Mode Tabs */}
        {!isAdminView && (
          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem', flexWrap: 'wrap' }}>
            <button
              type="button"
              className={`btn ${activeTab === 'leaderboard' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setActiveTab('leaderboard')}
              style={{ padding: '0.55rem 1.4rem', borderRadius: '980px', fontWeight: 600 }}
            >
              🏆 Leaderboard
            </button>
            <button
              type="button"
              className={`btn ${activeTab === 'badges' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setActiveTab('badges')}
              style={{ padding: '0.55rem 1.4rem', borderRadius: '980px', fontWeight: 600 }}
            >
              🏅 Badges ({unlockedCount}/{badgesData.length})
            </button>
            <button
              type="button"
              className={`btn ${activeTab === 'history' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => {
                setActiveTab('history');
                loadPointHistory();
              }}
              style={{ padding: '0.55rem 1.4rem', borderRadius: '980px', fontWeight: 600 }}
            >
              📜 My XP History
            </button>
          </div>
        )}
      </div>

      {/* ADMIN VIEW */}
      {isAdminView && user?.admin && (
        <div style={{ width: '100%' }}>
          {/* Admin Stats Summary Bar */}
          {adminData && (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1.25rem', marginBottom: '1.75rem' }}>
              <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.25rem', textAlign: 'center', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
                <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>Total Participants</span>
                <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#0f172a', marginTop: '0.25rem' }}>{adminData.totalParticipants}</div>
              </div>
              <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.25rem', textAlign: 'center', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
                <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>Active Learners</span>
                <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#16a34a', marginTop: '0.25rem' }}>{adminData.activeLearners}</div>
              </div>
              <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.25rem', textAlign: 'center', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
                <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>Total XP Awarded</span>
                <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#0284c7', marginTop: '0.25rem' }}>⚡ {adminData.totalXpAwarded}</div>
              </div>
              <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.25rem', textAlign: 'center', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
                <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>Badges Unlocked</span>
                <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#d97706', marginTop: '0.25rem' }}>🏅 {adminData.totalBadgesEarned}</div>
              </div>
              <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.25rem', textAlign: 'center', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
                <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>Avg XP / Learner</span>
                <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#9333ea', marginTop: '0.25rem' }}>{adminData.avgXpPerLearner}</div>
              </div>
            </div>
          )}

          {/* Admin Navigation Tabs */}
          <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '0.75rem' }}>
            <button
              type="button"
              className={`btn ${adminTab === 'rankings' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setAdminTab('rankings')}
            >
              👥 Learner Standings
            </button>
            <button
              type="button"
              className={`btn ${adminTab === 'rules' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setAdminTab('rules')}
            >
              ⚙️ XP Rules Engine
            </button>
            <button
              type="button"
              className={`btn ${adminTab === 'history' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setAdminTab('history')}
            >
              📋 XP Audit Log
            </button>
          </div>

          {/* Admin Standings Sub-tab */}
          {adminTab === 'rankings' && (
            <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', width: '100%', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', marginBottom: '1.5rem' }}>
                <h3 style={{ margin: 0, fontWeight: 700, color: '#0f172a' }}>Learner Standings & Performance</h3>
                
                <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
                  <select
                    value={timeframe}
                    onChange={(e) => setTimeframe(e.target.value)}
                    className="input"
                    style={{ width: 'auto', padding: '0.45rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  >
                    {TIMEFRAMES.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}
                  </select>

                  <select
                    value={stream}
                    onChange={(e) => setStream(e.target.value)}
                    className="input"
                    style={{ width: 'auto', padding: '0.45rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  >
                    <option value="all">All Streams</option>
                    {availableStreams.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                </div>
              </div>

              {loading ? (
                <p style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>Loading learner data...</p>
              ) : adminData?.rankings?.length > 0 ? (
                <div style={{ overflowX: 'auto', width: '100%' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                    <thead>
                      <tr style={{ borderBottom: '2px solid #e2e8f0', color: '#475569', fontSize: '0.85rem' }}>
                        <th style={{ padding: '0.85rem' }}>Rank</th>
                        <th style={{ padding: '0.85rem' }}>Learner</th>
                        <th style={{ padding: '0.85rem' }}>Email</th>
                        <th style={{ padding: '0.85rem' }}>Streak</th>
                        <th style={{ padding: '0.85rem' }}>Badges</th>
                        <th style={{ padding: '0.85rem' }}>Courses</th>
                        <th style={{ padding: '0.85rem' }}>Quizzes</th>
                        <th style={{ padding: '0.85rem' }}>Total XP</th>
                        <th style={{ padding: '0.85rem', textAlign: 'right' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {adminData.rankings.map(row => (
                        <tr key={row.userId} style={{ borderBottom: '1px solid #f1f5f9' }}>
                          <td style={{ padding: '0.85rem', fontWeight: 700, color: '#334155' }}>#{row.rank}</td>
                          <td style={{ padding: '0.85rem', fontWeight: 600, color: '#0f172a' }}>{row.displayName}</td>
                          <td style={{ padding: '0.85rem', color: '#64748b', fontSize: '0.875rem' }}>{row.email}</td>
                          <td style={{ padding: '0.85rem' }}>🔥 {row.streak} d</td>
                          <td style={{ padding: '0.85rem' }}>🏅 {row.badgeCount}</td>
                          <td style={{ padding: '0.85rem' }}>🎓 {row.coursesCompleted}</td>
                          <td style={{ padding: '0.85rem' }}>🎯 {row.quizzesPassed}</td>
                          <td style={{ padding: '0.85rem', fontWeight: 800, color: '#0a7d6d' }}>⚡ {row.points}</td>
                          <td style={{ padding: '0.85rem', textAlign: 'right' }}>
                            <button
                              type="button"
                              className="btn btn-secondary"
                              style={{ padding: '0.3rem 0.75rem', fontSize: '0.825rem' }}
                              onClick={() => {
                                setAdjustTargetUser(row);
                                setAdjustAmount('');
                                setAdjustReason('');
                                setAdjustError(null);
                                setAdjustSuccess(null);
                                setAdjustModalOpen(true);
                              }}
                            >
                              ⚙️ Adjust XP
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>No learner records found for selected filter.</p>
              )}
            </div>
          )}

          {/* Admin Rules Sub-tab */}
          {adminTab === 'rules' && (
            <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', width: '100%', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
              <h3 style={{ margin: '0 0 0.5rem', fontWeight: 700, color: '#0f172a' }}>XP Rules Management Engine</h3>
              <p style={{ color: '#64748b', marginBottom: '1.5rem', fontSize: '0.925rem' }}>
                Configure the server-side point rewards awarded for key learning events. Changes are saved atomically and logged to the system audit trail.
              </p>

              <div style={{ overflowX: 'auto', width: '100%' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                  <thead>
                    <tr style={{ borderBottom: '2px solid #e2e8f0', color: '#475569', fontSize: '0.85rem' }}>
                      <th style={{ padding: '0.85rem' }}>Rule Name</th>
                      <th style={{ padding: '0.85rem' }}>Action Key</th>
                      <th style={{ padding: '0.85rem' }}>Description</th>
                      <th style={{ padding: '0.85rem' }}>Reward Value</th>
                      <th style={{ padding: '0.85rem', textAlign: 'right' }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pointRules.map(rule => (
                      <tr key={rule.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                        <td style={{ padding: '0.85rem', fontWeight: 700, color: '#0f172a' }}>{rule.displayName}</td>
                        <td style={{ padding: '0.85rem', fontFamily: 'monospace', color: '#475569', fontSize: '0.85rem' }}>{rule.actionType}</td>
                        <td style={{ padding: '0.85rem', color: '#64748b' }}>{rule.description}</td>
                        <td style={{ padding: '0.85rem', fontWeight: 800, color: '#0a7d6d' }}>
                          {editingRule === rule.actionType ? (
                            <input
                              type="number"
                              className="input"
                              value={ruleValue}
                              onChange={(e) => setRuleValue(e.target.value)}
                              style={{ width: '90px', padding: '0.3rem' }}
                            />
                          ) : (
                            `⚡ ${rule.points} XP`
                          )}
                        </td>
                        <td style={{ padding: '0.85rem', textAlign: 'right' }}>
                          {editingRule === rule.actionType ? (
                            <div style={{ display: 'flex', gap: '0.4rem', justifyContent: 'flex-end' }}>
                              <button
                                type="button"
                                className="btn btn-primary"
                                style={{ padding: '0.3rem 0.7rem', fontSize: '0.8rem' }}
                                onClick={() => handleSaveRule(rule.actionType)}
                              >
                                Save
                              </button>
                              <button
                                type="button"
                                className="btn btn-secondary"
                                style={{ padding: '0.3rem 0.7rem', fontSize: '0.8rem' }}
                                onClick={() => setEditingRule(null)}
                              >
                                Cancel
                              </button>
                            </div>
                          ) : (
                            <button
                              type="button"
                              className="btn btn-secondary"
                              style={{ padding: '0.3rem 0.75rem', fontSize: '0.825rem' }}
                              onClick={() => {
                                setEditingRule(rule.actionType);
                                setRuleValue(String(rule.points));
                              }}
                            >
                              Edit Value
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Admin Audit Trail Sub-tab */}
          {adminTab === 'history' && (
            <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', width: '100%', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
              <h3 style={{ margin: '0 0 1rem', fontWeight: 700, color: '#0f172a' }}>XP Audit Log & Transaction History</h3>
              {adminHistory.length > 0 ? (
                <div style={{ overflowX: 'auto', width: '100%' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                    <thead>
                      <tr style={{ borderBottom: '2px solid #e2e8f0', color: '#475569', fontSize: '0.85rem' }}>
                        <th style={{ padding: '0.85rem' }}>Date & Time</th>
                        <th style={{ padding: '0.85rem' }}>User ID</th>
                        <th style={{ padding: '0.85rem' }}>Action Type</th>
                        <th style={{ padding: '0.85rem' }}>Description</th>
                        <th style={{ padding: '0.85rem' }}>Stream</th>
                        <th style={{ padding: '0.85rem' }}>Amount</th>
                      </tr>
                    </thead>
                    <tbody>
                      {adminHistory.map(tx => (
                        <tr key={tx.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                          <td style={{ padding: '0.85rem', color: '#64748b', fontSize: '0.85rem' }}>
                            {new Date(tx.createdAt).toLocaleString()}
                          </td>
                          <td style={{ padding: '0.85rem', fontWeight: 600 }}>#{tx.userId}</td>
                          <td style={{ padding: '0.85rem', fontFamily: 'monospace', fontSize: '0.85rem' }}>{tx.actionType}</td>
                          <td style={{ padding: '0.85rem', color: '#334155' }}>{tx.description}</td>
                          <td style={{ padding: '0.85rem', color: '#64748b' }}>{tx.stream || 'Global'}</td>
                          <td style={{ padding: '0.85rem', fontWeight: 800, color: tx.amount >= 0 ? '#16a34a' : '#dc2626' }}>
                            {tx.amount >= 0 ? `+${tx.amount}` : tx.amount} XP
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>No transaction history recorded yet.</p>
              )}
            </div>
          )}
        </div>
      )}

      {/* LEARNER VIEW */}
      {!isAdminView && (
        <div style={{ width: '100%' }}>
          {/* Daily Streak Card - Wide Banner */}
          {summaryData && (
            <div
              style={{
                background: 'linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%)',
                border: '1px solid #fde68a',
                borderRadius: '16px',
                padding: '1.25rem 1.75rem',
                marginBottom: '1.75rem',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: '1.25rem',
                boxShadow: '0 2px 10px rgba(245, 158, 11, 0.06)'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
                <div style={{ width: '56px', height: '56px', borderRadius: '50%', background: '#ffffff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.85rem', boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }}>
                  🔥
                </div>
                <div>
                  <h3 style={{ margin: 0, fontWeight: 700, fontSize: '1.2rem', color: '#78350f' }}>
                    {summaryData.currentStreak} Day Learning Streak
                  </h3>
                  <p style={{ margin: '0.25rem 0 0', color: '#92400e', fontSize: '0.925rem' }}>
                    Log in daily to earn streak multipliers! Next milestone: {summaryData.currentStreak >= 30 ? '30 Days Complete!' : summaryData.currentStreak >= 7 ? '14 Days' : '7 Days'}.
                  </p>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                <button
                  type="button"
                  className={`btn ${summaryData.checkedInToday ? 'btn-secondary' : 'btn-primary'}`}
                  disabled={summaryData.checkedInToday || claiming}
                  onClick={handleCheckIn}
                  style={{ padding: '0.65rem 1.6rem', fontWeight: 700, borderRadius: '980px' }}
                >
                  {summaryData.checkedInToday ? '✓ Checked in Today' : claiming ? 'Checking in...' : 'Claim Daily (+5 XP)'}
                </button>
              </div>
            </div>
          )}

          {claimMessage && (
            <div className={`alert alert-${claimMessage.type}`} style={{ marginBottom: '1.5rem', borderRadius: '12px' }}>
              {claimMessage.text}
            </div>
          )}

          {/* LEADERBOARD TAB */}
          {activeTab === 'leaderboard' && (
            <div style={{ width: '100%' }}>
              {/* Filters Bar */}
              <div
                style={{
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '16px',
                  padding: '1.25rem 1.5rem',
                  marginBottom: '1.75rem',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '1rem',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.02)'
                }}
              >
                <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                  {TIMEFRAMES.map(t => (
                    <button
                      key={t.id}
                      type="button"
                      className={`btn ${timeframe === t.id ? 'btn-primary' : 'btn-secondary'}`}
                      onClick={() => setTimeframe(t.id)}
                      style={{ padding: '0.45rem 1.1rem', fontSize: '0.875rem', borderRadius: '980px' }}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                  <span style={{ fontSize: '0.875rem', fontWeight: 600, color: '#64748b' }}>Filter Stream:</span>
                  <select
                    value={stream}
                    onChange={(e) => setStream(e.target.value)}
                    className="input"
                    style={{ width: 'auto', padding: '0.45rem 0.9rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  >
                    <option value="all">All Streams</option>
                    {availableStreams.map(s => (
                      <option key={s} value={s}>{s}</option>
                    ))}
                  </select>
                </div>
              </div>

              {loading ? (
                <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '3rem', textAlign: 'center', color: '#64748b' }}>
                  Loading leaderboard standings...
                </div>
              ) : leaderboardData ? (
                <div style={{ width: '100%' }}>
                  {/* Top 3 Podium */}
                  {leaderboardData.podium?.length > 0 && (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.25rem', marginBottom: '1.75rem', width: '100%' }}>
                      {leaderboardData.podium.map((p, idx) => {
                        const medals = ['🥇', '🥈', '🥉'];
                        const borderColors = ['#f59e0b', '#94a3b8', '#d97706'];
                        const bgGradients = [
                          'linear-gradient(180deg, #fffcf0 0%, #ffffff 100%)',
                          'linear-gradient(180deg, #f8fafc 0%, #ffffff 100%)',
                          'linear-gradient(180deg, #fff7ed 0%, #ffffff 100%)'
                        ];
                        return (
                          <div
                            key={p.userId}
                            style={{
                              background: p.isCurrentUser ? '#f0fdf4' : bgGradients[idx] || '#ffffff',
                              border: `1px solid ${p.isCurrentUser ? '#86efac' : '#e2e8f0'}`,
                              borderTop: `5px solid ${borderColors[idx] || '#e2e8f0'}`,
                              borderRadius: '16px',
                              padding: '1.75rem 1.5rem',
                              textAlign: 'center',
                              position: 'relative',
                              boxShadow: '0 4px 14px rgba(0,0,0,0.03)'
                            }}
                          >
                            <div style={{ position: 'absolute', top: '14px', right: '16px', fontSize: '1.6rem' }}>
                              {medals[idx]}
                            </div>
                            
                            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '0.85rem' }}>
                              {p.pictureUrl ? (
                                <img src={p.pictureUrl} alt="" className="avatar" style={{ width: '64px', height: '64px', borderRadius: '50%', boxShadow: '0 4px 10px rgba(0,0,0,0.08)' }} />
                              ) : (
                                <span className="avatar" style={{ width: '64px', height: '64px', fontSize: '1.5rem', borderRadius: '50%', boxShadow: '0 4px 10px rgba(0,0,0,0.08)' }}>{initials(p.displayName)}</span>
                              )}
                            </div>

                            <h4 style={{ margin: '0 0 0.35rem', fontWeight: 700, fontSize: '1.1rem', color: '#0f172a' }}>{p.displayName}</h4>
                            <p style={{ margin: '0 0 0.85rem', color: '#64748b', fontSize: '0.875rem' }}>
                              🔥 {p.streak} day streak • 🏅 {p.badgeCount} badges
                            </p>

                            <div style={{ fontSize: '1.35rem', fontWeight: 800, color: '#0a7d6d' }}>
                              ⚡ {p.points} XP
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}

                  {/* Leaderboard Table */}
                  <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', marginBottom: '1.75rem', width: '100%', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
                    <h3 style={{ margin: '0 0 1.25rem', fontWeight: 700, fontSize: '1.2rem', color: '#0f172a' }}>Full Standings</h3>

                    {leaderboardData.rankings?.length > 0 ? (
                      <div style={{ overflowX: 'auto', width: '100%' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                          <thead>
                            <tr style={{ borderBottom: '2px solid #e2e8f0', color: '#475569', fontSize: '0.85rem' }}>
                              <th style={{ padding: '0.85rem' }}>Rank</th>
                              <th style={{ padding: '0.85rem' }}>Learner</th>
                              <th style={{ padding: '0.85rem' }}>Streak</th>
                              <th style={{ padding: '0.85rem' }}>Badges</th>
                              <th style={{ padding: '0.85rem', textAlign: 'right' }}>Total XP</th>
                            </tr>
                          </thead>
                          <tbody>
                            {leaderboardData.rankings.map(entry => (
                              <tr
                                key={entry.userId}
                                style={{
                                  borderBottom: '1px solid #f1f5f9',
                                  background: entry.isCurrentUser ? '#f0fdf4' : 'transparent',
                                  fontWeight: entry.isCurrentUser ? 700 : 400
                                }}
                              >
                                <td style={{ padding: '0.85rem', fontWeight: 700, color: '#334155' }}>#{entry.rank}</td>
                                <td style={{ padding: '0.85rem' }}>
                                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                                    {entry.pictureUrl ? (
                                      <img src={entry.pictureUrl} alt="" className="avatar" style={{ width: '36px', height: '36px' }} />
                                    ) : (
                                      <span className="avatar" style={{ width: '36px', height: '36px', fontSize: '0.85rem' }}>{initials(entry.displayName)}</span>
                                    )}
                                    <span style={{ color: '#0f172a' }}>{entry.displayName} {entry.isCurrentUser && '(You)'}</span>
                                  </div>
                                </td>
                                <td style={{ padding: '0.85rem' }}>🔥 {entry.streak} d</td>
                                <td style={{ padding: '0.85rem' }}>🏅 {entry.badgeCount}</td>
                                <td style={{ padding: '0.85rem', textAlign: 'right', fontWeight: 800, color: '#0a7d6d' }}>
                                  ⚡ {entry.points} XP
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <p style={{ color: '#64748b', textAlign: 'center', padding: '1.5rem' }}>No other entries found in this view.</p>
                    )}
                  </div>

                  {/* CURRENT USER RANK SPOTLIGHT SECTION - Clean Light Mint Theme */}
                  {leaderboardData.currentUserRank && (
                    <div
                      style={{
                        background: 'linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%)',
                        border: '1px solid #a7f3d0',
                        borderRadius: '16px',
                        padding: '1.25rem 1.75rem',
                        color: '#065f46',
                        boxShadow: '0 4px 14px rgba(16, 185, 129, 0.08)'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                          <div style={{ width: '50px', height: '50px', borderRadius: '50%', background: '#059669', color: '#ffffff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: '1.25rem', boxShadow: '0 2px 8px rgba(0,0,0,0.1)' }}>
                            #{leaderboardData.currentUserRank.rank}
                          </div>
                          <div>
                            <span style={{ fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.06em', color: '#047857', fontWeight: 700 }}>Your Standing</span>
                            <h4 style={{ margin: '0.15rem 0 0', color: '#064e3b', fontWeight: 800, fontSize: '1.15rem' }}>
                              {leaderboardData.currentUserRank.displayName}
                            </h4>
                          </div>
                        </div>

                        <div style={{ display: 'flex', gap: '1.75rem', alignItems: 'center' }}>
                          <div>
                            <span style={{ fontSize: '0.75rem', color: '#047857', display: 'block', fontWeight: 600 }}>Streak</span>
                            <span style={{ fontWeight: 700, fontSize: '1.05rem' }}>🔥 {leaderboardData.currentUserRank.streak} days</span>
                          </div>
                          <div>
                            <span style={{ fontSize: '0.75rem', color: '#047857', display: 'block', fontWeight: 600 }}>Badges</span>
                            <span style={{ fontWeight: 700, fontSize: '1.05rem' }}>🏅 {leaderboardData.currentUserRank.badgeCount}</span>
                          </div>
                          <div>
                            <span style={{ fontSize: '0.75rem', color: '#047857', display: 'block', fontWeight: 600 }}>Your Total XP</span>
                            <span style={{ fontWeight: 800, fontSize: '1.35rem', color: '#065f46' }}>⚡ {leaderboardData.currentUserRank.points} XP</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              ) : null}
            </div>
          )}

          {/* BADGES TAB */}
          {activeTab === 'badges' && (
            <div style={{ width: '100%' }}>
              <div
                style={{
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '16px',
                  padding: '1.25rem 1.5rem',
                  marginBottom: '1.75rem',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '1rem',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.02)'
                }}
              >
                <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                  {BADGE_CATEGORIES.map(cat => (
                    <button
                      key={cat.id}
                      type="button"
                      className={`btn ${badgeCategory === cat.id ? 'btn-primary' : 'btn-secondary'}`}
                      onClick={() => setBadgeCategory(cat.id)}
                      style={{ padding: '0.45rem 1.1rem', fontSize: '0.875rem', borderRadius: '980px' }}
                    >
                      {cat.label}
                    </button>
                  ))}
                </div>

                <div style={{ fontWeight: 700, color: '#475569' }}>
                  Badges Unlocked: <span style={{ color: '#0a7d6d', fontSize: '1.1rem' }}>{unlockedCount} / {badgesData.length}</span>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '1.25rem', width: '100%' }}>
                {filteredBadges.map(badge => (
                  <div
                    key={badge.id}
                    style={{
                      background: badge.unlocked ? '#ffffff' : '#f8fafc',
                      border: badge.unlocked ? '2px solid #10b981' : '1px solid #e2e8f0',
                      borderRadius: '16px',
                      padding: '1.5rem',
                      opacity: badge.unlocked ? 1 : 0.65,
                      boxShadow: badge.unlocked ? '0 4px 14px rgba(16, 185, 129, 0.06)' : 'none',
                      transition: 'all 0.2s ease'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
                      <span style={{ fontSize: '2.75rem', filter: badge.unlocked ? 'none' : 'grayscale(100%)' }}>{badge.icon || '🏅'}</span>
                      <span className={`badge rarity-${(badge.rarity || 'COMMON').toLowerCase()}`}>
                        {badge.rarity}
                      </span>
                    </div>

                    <h4 style={{ margin: '0 0 0.4rem', fontWeight: 700, fontSize: '1.1rem', color: '#0f172a' }}>{badge.title}</h4>
                    <p style={{ margin: '0 0 1rem', color: '#64748b', fontSize: '0.875rem', minHeight: '40px' }}>
                      {badge.description}
                    </p>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '0.85rem', borderTop: '1px solid #f1f5f9', fontSize: '0.875rem' }}>
                      <span style={{ fontWeight: 800, color: '#0a7d6d' }}>+ {badge.pointsReward} XP</span>
                      {badge.unlocked ? (
                        <span style={{ color: '#16a34a', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                          ✓ Unlocked
                        </span>
                      ) : (
                        <span style={{ color: '#94a3b8' }}>🔒 Locked</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* HISTORY TAB */}
          {activeTab === 'history' && (
            <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', width: '100%', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
              <h3 style={{ margin: '0 0 1.25rem', fontWeight: 700, color: '#0f172a' }}>Your XP Activity Log</h3>

              {pointHistory.length > 0 ? (
                <div style={{ overflowX: 'auto', width: '100%' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                    <thead>
                      <tr style={{ borderBottom: '2px solid #e2e8f0', color: '#475569', fontSize: '0.85rem' }}>
                        <th style={{ padding: '0.85rem' }}>Date</th>
                        <th style={{ padding: '0.85rem' }}>Activity</th>
                        <th style={{ padding: '0.85rem' }}>Stream</th>
                        <th style={{ padding: '0.85rem', textAlign: 'right' }}>XP Amount</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pointHistory.map(tx => (
                        <tr key={tx.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                          <td style={{ padding: '0.85rem', color: '#64748b', fontSize: '0.85rem' }}>
                            {new Date(tx.createdAt).toLocaleDateString()}
                          </td>
                          <td style={{ padding: '0.85rem' }}>
                            <strong style={{ display: 'block', fontSize: '0.9rem', color: '#0f172a' }}>{tx.actionType}</strong>
                            <span style={{ color: '#64748b', fontSize: '0.85rem' }}>{tx.description}</span>
                          </td>
                          <td style={{ padding: '0.85rem', color: '#64748b', fontSize: '0.85rem' }}>{tx.stream || 'General'}</td>
                          <td style={{ padding: '0.85rem', textAlign: 'right', fontWeight: 800, color: tx.amount >= 0 ? '#16a34a' : '#dc2626' }}>
                            {tx.amount >= 0 ? `+${tx.amount}` : tx.amount} XP
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>No point activity recorded yet.</p>
              )}
            </div>
          )}
        </div>
      )}

      {/* ADJUST XP MODAL FOR ADMIN */}
      {adjustModalOpen && adjustTargetUser && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '2rem', width: '100%', maxWidth: '480px', boxShadow: '0 20px 48px rgba(0,0,0,0.15)' }}>
            <h3 style={{ margin: '0 0 0.5rem', fontWeight: 700, color: '#0f172a' }}>Adjust XP for Learner</h3>
            <p style={{ color: '#64748b', fontSize: '0.9rem', marginBottom: '1.25rem' }}>
              Target Learner: <strong>{adjustTargetUser.displayName}</strong> ({adjustTargetUser.email})
            </p>

            {adjustError && <div className="alert alert-error" style={{ marginBottom: '1rem', borderRadius: '8px' }}>{adjustError}</div>}
            {adjustSuccess && <div className="alert alert-success" style={{ marginBottom: '1rem', borderRadius: '8px' }}>{adjustSuccess}</div>}

            <form onSubmit={handleSubmitAdjustXP}>
              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.3rem', color: '#334155' }}>
                  XP Amount (Positive to add, Negative to deduct)
                </label>
                <input
                  type="number"
                  className="input"
                  placeholder="e.g. 50 or -20"
                  value={adjustAmount}
                  onChange={(e) => setAdjustAmount(e.target.value)}
                  style={{ width: '100%', padding: '0.6rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  required
                />
              </div>

              <div style={{ marginBottom: '1.5rem' }}>
                <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.3rem', color: '#334155' }}>
                  Mandatory Reason (Audited)
                </label>
                <textarea
                  className="input"
                  rows="3"
                  placeholder="e.g. Bonus award for winning hackathon event"
                  value={adjustReason}
                  onChange={(e) => setAdjustReason(e.target.value)}
                  style={{ width: '100%', padding: '0.6rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  required
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setAdjustModalOpen(false)}
                  style={{ borderRadius: '8px' }}
                >
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" style={{ borderRadius: '8px' }}>
                  Confirm Adjustment
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
