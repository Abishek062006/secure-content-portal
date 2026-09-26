import { useState, useEffect } from 'react';
import { api } from '../../api';

const TIMEFRAMES = [
  { id: 'today', label: 'Today' },
  { id: 'this_week', label: 'This Week' },
  { id: 'this_month', label: 'This Month' },
  { id: 'all_time', label: 'All Time' },
];

export default function AdminLeaderboard() {
  const [timeframe, setTimeframe] = useState('all_time');
  const [stream, setStream] = useState('all');
  const [availableStreams, setAvailableStreams] = useState([]);
  
  const [adminTab, setAdminTab] = useState('rankings'); // 'rankings' | 'rules' | 'history'
  const [adminData, setAdminData] = useState(null);
  const [pointRules, setPointRules] = useState([]);
  const [adminHistory, setAdminHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  
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
    loadAdminData();
    loadAdminRules();
    loadAdminHistory();
  }, [timeframe, stream]);

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

  return (
    <div className="container-wide" style={{ width: '100%', maxWidth: '1100px', margin: '0 auto', padding: '2rem 1.5rem' }}>
      {/* Header Banner - Clean Light Theme */}
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
        <h1 style={{ margin: 0, fontSize: '1.85rem', fontWeight: 800, color: '#0f172a', letterSpacing: '-0.02em' }}>
          Leaderboard & Gamification Management
        </h1>
        <p style={{ margin: '0.5rem 0 0', color: '#64748b', fontSize: '1rem' }}>
          Platform analytics, central XP rules engine, learner rankings, and manual XP adjustments.
        </p>
      </div>

      {/* Analytics Summary */}
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

      {/* Navigation Sub-tabs */}
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

      {/* Rankings View */}
      {adminTab === 'rankings' && (
        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', width: '100%', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', marginBottom: '1.5rem' }}>
            <h3 style={{ margin: 0, fontWeight: 700, color: '#0f172a' }}>Learner Standings</h3>
            
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
            <p style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>Loading standings...</p>
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
            <p style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>No learner records found.</p>
          )}
        </div>
      )}

      {/* Rules Engine View */}
      {adminTab === 'rules' && (
        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', width: '100%', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
          <h3 style={{ margin: '0 0 1rem', fontWeight: 700, color: '#0f172a' }}>XP Rules Engine</h3>
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

      {/* XP Audit Log View */}
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

      {/* Adjust XP Modal */}
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
                  placeholder="e.g. Bonus award for winning community event"
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
