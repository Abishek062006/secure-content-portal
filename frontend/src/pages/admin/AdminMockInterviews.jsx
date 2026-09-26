import { useState, useEffect } from 'react';
import { api } from '../../api';

export default function AdminMockInterviews() {
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);

  // Filters & Search for Candidate Table
  const [searchQuery, setSearchQuery] = useState('');
  const [trackFilter, setTrackFilter] = useState('all');
  const [difficultyFilter, setDifficultyFilter] = useState('all');

  // Modal inspection state
  const [selectedSession, setSelectedSession] = useState(null);
  const [inspectingSession, setInspectingSession] = useState(false);
  const [modalDetails, setModalDetails] = useState(null);

  // Config State
  const [aiStrictness, setAiStrictness] = useState('Balanced');
  const [passingBenchmark, setPassingBenchmark] = useState(80);

  useEffect(() => {
    loadAnalytics();
  }, []);

  async function loadAnalytics() {
    setLoading(true);
    try {
      const data = await api.getAdminMockInterviewAnalytics();
      setAnalytics(data);
    } catch (err) {
      console.error('Failed to load admin mock interview analytics:', err);
    } finally {
      setLoading(false);
    }
  }

  async function handleInspectSession(sessionId) {
    setInspectingSession(true);
    try {
      const details = await api.getAdminMockInterviewSession(sessionId);
      setModalDetails(details);
      setSelectedSession(sessionId);
    } catch (err) {
      alert('Failed to load candidate session details: ' + err.message);
    } finally {
      setInspectingSession(false);
    }
  }

  function handleExportAudit() {
    if (!analytics || !analytics.recentSessions) return;
    const jsonStr = JSON.stringify(analytics, null, 2);
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `mock_interview_analytics_${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  const recentSessions = analytics?.recentSessions || [];

  const filteredSessions = recentSessions.filter(s => {
    const matchesSearch = !searchQuery.trim() ||
      (s.candidateName && s.candidateName.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (s.candidateEmail && s.candidateEmail.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (s.stream && s.stream.toLowerCase().includes(searchQuery.toLowerCase()));

    if (!matchesSearch) return false;
    if (trackFilter !== 'all' && s.track !== trackFilter) return false;
    if (difficultyFilter !== 'all' && s.difficulty !== difficultyFilter) return false;
    return true;
  });

  return (
    <div className="container-wide" style={{ width: '100%', maxWidth: '1100px', margin: '0 auto', padding: '2rem 1.5rem' }}>
      {/* Header Banner */}
      <div
        style={{
          background: 'linear-gradient(135deg, #ffffff 0%, #f0f9ff 100%)',
          border: '1px solid #e2e8f0',
          borderRadius: '16px',
          padding: '2rem',
          marginBottom: '1.75rem',
          boxShadow: '0 4px 20px rgba(0, 0, 0, 0.03)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1rem'
        }}
      >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <h1 style={{ margin: 0, fontSize: '1.85rem', fontWeight: 800, color: '#0f172a' }}>
              Admin Mock Interviews Portal 🤖
            </h1>
            <span style={{ background: '#dbeafe', color: '#1e40af', fontSize: '0.75rem', fontWeight: 700, padding: '4px 10px', borderRadius: '980px', textTransform: 'uppercase' }}>
              AI Governance & Analytics
            </span>
          </div>
          <p style={{ margin: '0.5rem 0 0', color: '#64748b', fontSize: '1rem' }}>
            Inspect candidate interview transcripts, evaluate stream readiness averages, and configure AI scoring benchmarks.
          </p>
        </div>

        <button
          type="button"
          className="btn btn-secondary"
          onClick={handleExportAudit}
          style={{ borderRadius: '980px', padding: '0.6rem 1.2rem', fontWeight: 700, fontSize: '0.875rem' }}
        >
          📥 Export Audit Report (JSON)
        </button>
      </div>

      {loading ? (
        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '3rem', textAlign: 'center', color: '#64748b' }}>
          Loading interview analytics...
        </div>
      ) : analytics ? (
        <>
          {/* Key Metric Cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.25rem', marginBottom: '2rem' }}>
            <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>Total Candidate Sessions</div>
              <div style={{ fontSize: '2.2rem', fontWeight: 900, color: '#0f172a', marginTop: '0.2rem' }}>
                {analytics.totalSessions || 0}
              </div>
            </div>

            <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>Completed Evaluations</div>
              <div style={{ fontSize: '2.2rem', fontWeight: 900, color: '#16a34a', marginTop: '0.2rem' }}>
                {analytics.completedSessions || 0}
              </div>
            </div>

            <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>Avg Platform Readiness</div>
              <div style={{ fontSize: '2.2rem', fontWeight: 900, color: '#2563eb', marginTop: '0.2rem' }}>
                {analytics.averageScore || 0}<span style={{ fontSize: '1rem', color: '#94a3b8' }}>/100</span>
              </div>
            </div>

            <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>Strictness Rating</div>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: '#7c3aed', marginTop: '0.5rem' }}>
                ⚖️ {aiStrictness} Mode
              </div>
            </div>
          </div>

          {/* Stream Average Readiness Scorecards */}
          <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', marginBottom: '2rem' }}>
            <h3 style={{ margin: '0 0 1rem', fontSize: '1.1rem', fontWeight: 800, color: '#0f172a' }}>
              Stream Readiness Benchmarks
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem' }}>
              {Object.entries(analytics.streamAverageScores || {}).map(([streamName, avgScore]) => (
                <div key={streamName} style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '12px', padding: '1rem' }}>
                  <div style={{ fontSize: '0.8rem', fontWeight: 700, color: '#475569', marginBottom: '0.3rem' }}>{streamName}</div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: '1.5rem', fontWeight: 900, color: avgScore >= 80 ? '#16a34a' : '#2563eb' }}>
                      {avgScore}%
                    </span>
                    <span className={`badge ${avgScore >= 80 ? 'badge-success' : 'badge-secondary'}`}>
                      {avgScore >= 80 ? 'HIGH READINESS' : 'STABLE'}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Candidate Sessions Table with Search & Filters */}
          <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', marginBottom: '2rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '1rem' }}>
              <h3 style={{ margin: 0, fontWeight: 800, color: '#0f172a' }}>
                Candidate Interview Sessions ({filteredSessions.length})
              </h3>

              <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'center' }}>
                {/* Search Bar */}
                <input
                  type="text"
                  className="input"
                  placeholder="🔍 Search name, email, stream..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  style={{ padding: '0.45rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.85rem', width: '220px' }}
                />

                {/* Track Filter */}
                <select
                  value={trackFilter}
                  onChange={(e) => setTrackFilter(e.target.value)}
                  className="input"
                  style={{ padding: '0.45rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.85rem' }}
                >
                  <option value="all">All Tracks</option>
                  <option value="STUDENT">🎓 Student</option>
                  <option value="WORKING_PROFESSIONAL">💼 Working Professional</option>
                </select>

                {/* Difficulty Filter */}
                <select
                  value={difficultyFilter}
                  onChange={(e) => setDifficultyFilter(e.target.value)}
                  className="input"
                  style={{ padding: '0.45rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.85rem' }}
                >
                  <option value="all">All Tiers</option>
                  <option value="EASY">Easy</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HARD">Hard</option>
                </select>
              </div>
            </div>

            {filteredSessions.length > 0 ? (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                  <thead>
                    <tr style={{ borderBottom: '2px solid #e2e8f0', color: '#475569', fontSize: '0.85rem' }}>
                      <th style={{ padding: '0.85rem' }}>Candidate</th>
                      <th style={{ padding: '0.85rem' }}>Stream</th>
                      <th style={{ padding: '0.85rem' }}>Track</th>
                      <th style={{ padding: '0.85rem' }}>Difficulty</th>
                      <th style={{ padding: '0.85rem' }}>Status</th>
                      <th style={{ padding: '0.85rem' }}>Score</th>
                      <th style={{ padding: '0.85rem', textAlign: 'right' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredSessions.map(s => (
                      <tr key={s.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                        <td style={{ padding: '0.85rem' }}>
                          <div style={{ fontWeight: 700, color: '#0f172a' }}>{s.candidateName}</div>
                          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>{s.candidateEmail}</div>
                        </td>
                        <td style={{ padding: '0.85rem', fontSize: '0.875rem', fontWeight: 600, color: '#334155' }}>{s.stream}</td>
                        <td style={{ padding: '0.85rem', fontSize: '0.85rem' }}>
                          <span style={{ background: '#f1f5f9', padding: '2px 8px', borderRadius: '6px', fontSize: '0.75rem', fontWeight: 600 }}>
                            {s.track === 'WORKING_PROFESSIONAL' ? '💼 Professional' : '🎓 Student'}
                          </span>
                        </td>
                        <td style={{ padding: '0.85rem', fontSize: '0.85rem' }}>{s.difficulty}</td>
                        <td style={{ padding: '0.85rem' }}>
                          <span className={`badge ${s.status === 'COMPLETED' ? 'badge-success' : 'badge-secondary'}`}>
                            {s.status}
                          </span>
                        </td>
                        <td style={{ padding: '0.85rem', fontWeight: 900, color: '#2563eb' }}>{s.overallScore}/100</td>
                        <td style={{ padding: '0.85rem', textAlign: 'right' }}>
                          <button
                            type="button"
                            className="btn btn-primary"
                            style={{ padding: '0.4rem 0.85rem', fontSize: '0.8rem', borderRadius: '8px' }}
                            onClick={() => handleInspectSession(s.id)}
                          >
                            🔍 Inspect Q&A
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p style={{ textAlign: 'center', color: '#64748b', padding: '2rem' }}>No candidate sessions match the current search filters.</p>
            )}
          </div>

          {/* AI Evaluation Settings Panel */}
          <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem' }}>
            <h3 style={{ margin: '0 0 1rem', fontWeight: 800, color: '#0f172a' }}>AI Evaluation Governance</h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '1.25rem' }}>
              <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '12px', border: '1px solid #e2e8f0' }}>
                <label style={{ display: 'block', fontWeight: 700, fontSize: '0.9rem', marginBottom: '0.5rem' }}>
                  AI Grading Strictness
                </label>
                <select
                  value={aiStrictness}
                  onChange={(e) => setAiStrictness(e.target.value)}
                  className="input"
                  style={{ width: '100%', padding: '0.5rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                >
                  <option value="Balanced">Balanced (Standard Industry Standard)</option>
                  <option value="Rigorous">Rigorous (FAANG / Tier 1 Strictness)</option>
                  <option value="Leniency Mode">Leniency Mode (Supportive Learning)</option>
                </select>
              </div>

              <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '12px', border: '1px solid #e2e8f0' }}>
                <label style={{ display: 'block', fontWeight: 700, fontSize: '0.9rem', marginBottom: '0.5rem' }}>
                  Target Passing Benchmark: {passingBenchmark}%
                </label>
                <input
                  type="range"
                  min="60"
                  max="95"
                  value={passingBenchmark}
                  onChange={(e) => setPassingBenchmark(Number(e.target.value))}
                  style={{ width: '100%', accentColor: '#2563eb' }}
                />
              </div>
            </div>
          </div>
        </>
      ) : null}

      {/* CANDIDATE TRANSCRIPT INSPECTION MODAL */}
      {selectedSession && modalDetails && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(15, 23, 42, 0.6)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1100,
            padding: '1.5rem'
          }}
        >
          <div
            style={{
              background: '#ffffff',
              borderRadius: '20px',
              maxWidth: '850px',
              width: '100%',
              maxHeight: '90vh',
              overflowY: 'auto',
              padding: '2rem',
              boxShadow: '0 20px 40px rgba(0,0,0,0.15)'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '1rem' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '1.35rem', fontWeight: 800, color: '#0f172a' }}>
                  Candidate Transcript: {modalDetails.candidateName}
                </h3>
                <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
                  {modalDetails.candidateEmail} • {modalDetails.session.stream} ({modalDetails.session.track})
                </span>
              </div>
              <button
                type="button"
                onClick={() => { setSelectedSession(null); setModalDetails(null); }}
                style={{ background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: '#64748b' }}
              >
                ✕
              </button>
            </div>

            {/* Scorecard Header */}
            <div style={{ background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: '12px', padding: '1.25rem', marginBottom: '1.5rem', display: 'flex', justifyContent: 'space-around', textAlign: 'center' }}>
              <div>
                <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#1e40af' }}>OVERALL SCORE</div>
                <div style={{ fontSize: '1.8rem', fontWeight: 900, color: '#2563eb' }}>{modalDetails.session.overallScore}/100</div>
              </div>

              <div>
                <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#1e40af' }}>READINESS RATING</div>
                <div style={{ fontSize: '1.1rem', fontWeight: 800, color: '#16a34a', marginTop: '4px' }}>{modalDetails.session.readinessLevel}</div>
              </div>
            </div>

            {/* Questions breakdown */}
            <h4 style={{ margin: '0 0 1rem', fontWeight: 800, color: '#0f172a' }}>Evaluation Breakdown</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {modalDetails.questions.map((q, idx) => (
                <div key={q.id} style={{ border: '1px solid #e2e8f0', borderRadius: '12px', padding: '1.25rem', background: '#f8fafc' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                    <span style={{ fontSize: '0.8rem', fontWeight: 800, color: '#2563eb' }}>Q{idx + 1}. {q.category}</span>
                    <span style={{ fontWeight: 800, fontSize: '0.85rem', color: '#16a34a' }}>AI Score: {q.score}/10</span>
                  </div>

                  <div style={{ fontWeight: 700, color: '#0f172a', marginBottom: '0.75rem' }}>
                    "{q.questionText}"
                  </div>

                  <div style={{ fontSize: '0.85rem', background: '#ffffff', border: '1px solid #cbd5e1', padding: '0.75rem', borderRadius: '8px', marginBottom: '0.75rem' }}>
                    <strong>Candidate Response:</strong> {q.learnerAnswer || '(No response recorded)'}
                  </div>

                  <div style={{ fontSize: '0.85rem', color: '#1e3a8a', background: '#eff6ff', padding: '0.75rem', borderRadius: '8px', marginBottom: '0.5rem' }}>
                    <strong>AI Feedback:</strong> {q.aiFeedback}
                  </div>

                  {q.idealAnswer && (
                    <div style={{ fontSize: '0.8rem', color: '#047857', background: '#ecfdf5', padding: '0.6rem 0.75rem', borderRadius: '8px' }}>
                      <strong>Reference Ideal Answer:</strong> {q.idealAnswer}
                    </div>
                  )}
                </div>
              ))}
            </div>

            <div style={{ textAlign: 'right', marginTop: '1.5rem' }}>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => { setSelectedSession(null); setModalDetails(null); }}
                style={{ padding: '0.6rem 1.5rem', fontWeight: 700, borderRadius: '8px' }}
              >
                Close Transcript
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
