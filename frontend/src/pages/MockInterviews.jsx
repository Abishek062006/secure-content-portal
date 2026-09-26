import { useState, useEffect } from 'react';
import { api } from '../api';

export default function MockInterviews() {
  const [viewState, setViewState] = useState('setup'); // 'setup' | 'active' | 'scorecard' | 'history'

  // Setup state
  const [track, setTrack] = useState('STUDENT'); // 'STUDENT' or 'WORKING_PROFESSIONAL'
  const [stream, setStream] = useState('Engineering & Web Dev');
  const [difficulty, setDifficulty] = useState('MEDIUM');
  const [availableStreams, setAvailableStreams] = useState([]);
  const [starting, setStarting] = useState(false);

  // Active Session state
  const [sessionData, setSessionData] = useState(null); // { session, questions }
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answerInput, setAnswerInput] = useState('');
  const [evaluating, setEvaluating] = useState(false);
  const [currentEvaluation, setCurrentEvaluation] = useState(null); // feedback for current Q

  // History state
  const [historyList, setHistoryList] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  useEffect(() => {
    loadStreams();
    loadHistory();
  }, []);

  async function loadStreams() {
    try {
      const data = await api.getStreams();
      if (Array.isArray(data) && data.length > 0) setAvailableStreams(data);
      else throw new Error();
    } catch {
      setAvailableStreams([
        'Engineering & Web Dev',
        'AI & Data Science',
        'UI/UX & Design',
        'Cloud & Infrastructure'
      ]);
    }
  }

  async function loadHistory() {
    setLoadingHistory(true);
    try {
      const data = await api.getMockInterviewHistory();
      setHistoryList(data || []);
    } catch (err) {
      console.error('Failed to load interview history:', err);
    } finally {
      setLoadingHistory(false);
    }
  }

  async function handleStartSession() {
    setStarting(true);
    setCurrentEvaluation(null);
    setAnswerInput('');
    setCurrentIndex(0);

    try {
      const session = await api.startMockInterview(track, stream, difficulty);
      const details = await api.getMockInterviewSession(session.id);
      setSessionData(details);
      setViewState('active');
    } catch (err) {
      alert('Failed to start interview session: ' + err.message);
    } finally {
      setStarting(false);
    }
  }

  async function handleSubmitAnswer() {
    if (!answerInput.trim()) {
      alert('Please enter your answer before submitting.');
      return;
    }
    setEvaluating(true);

    const questions = sessionData?.questions || [];
    const currentQ = questions[currentIndex];

    try {
      const res = await api.submitMockInterviewAnswer(sessionData.session.id, currentQ.id, answerInput);
      setCurrentEvaluation(res.question);

      // Refresh session details to sync
      const updatedDetails = await api.getMockInterviewSession(sessionData.session.id);
      setSessionData(updatedDetails);
    } catch (err) {
      alert('Failed to evaluate answer: ' + err.message);
    } finally {
      setEvaluating(false);
    }
  }

  function handleNextQuestion() {
    setCurrentEvaluation(null);
    setAnswerInput('');
    const questions = sessionData?.questions || [];

    if (currentIndex + 1 < questions.length) {
      setCurrentIndex(currentIndex + 1);
    } else {
      handleCompleteSession();
    }
  }

  async function handleCompleteSession() {
    try {
      const res = await api.completeMockInterviewSession(sessionData.session.id);
      setSessionData({ session: res.session, questions: res.questions });
      setViewState('scorecard');
      loadHistory();
    } catch (err) {
      alert('Failed to finalize session: ' + err.message);
    }
  }

  async function handleViewHistoryReport(sessionItem) {
    try {
      const details = await api.getMockInterviewSession(sessionItem.id);
      setSessionData(details);
      setViewState('scorecard');
    } catch (err) {
      alert('Failed to load session details: ' + err.message);
    }
  }

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
            <h1 style={{ margin: 0, fontSize: '1.85rem', fontWeight: 800, color: '#0f172a', letterSpacing: '-0.02em' }}>
              AI Mock Interview Arena 🤖
            </h1>
            <span style={{ background: '#dbeafe', color: '#1e40af', fontSize: '0.75rem', fontWeight: 700, padding: '4px 10px', borderRadius: '980px', textTransform: 'uppercase' }}>
              Instant AI Evaluation
            </span>
          </div>
          <p style={{ margin: '0.5rem 0 0', color: '#64748b', fontSize: '1rem' }}>
            Practice real-world technical & behavioral interviews, receive instant AI feedback, and earn +50 XP on completion!
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button
            type="button"
            className={`btn ${viewState === 'setup' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setViewState('setup')}
            style={{ borderRadius: '980px', padding: '0.55rem 1.2rem', fontWeight: 600 }}
          >
            🎯 New Interview
          </button>
          <button
            type="button"
            className={`btn ${viewState === 'history' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => { setViewState('history'); loadHistory(); }}
            style={{ borderRadius: '980px', padding: '0.55rem 1.2rem', fontWeight: 600 }}
          >
            📜 History ({historyList.length})
          </button>
        </div>
      </div>

      {/* SETUP VIEW */}
      {viewState === 'setup' && (
        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '2rem', boxShadow: '0 2px 10px rgba(0,0,0,0.02)' }}>
          <h2 style={{ margin: '0 0 1.5rem', fontSize: '1.35rem', fontWeight: 800, color: '#0f172a' }}>
            Configure Your Mock Interview
          </h2>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.5rem', marginBottom: '2rem' }}>
            {/* Target Track */}
            <div style={{ border: '1px solid #e2e8f0', borderRadius: '14px', padding: '1.25rem', background: '#f8fafc' }}>
              <label style={{ display: 'block', fontWeight: 700, fontSize: '0.95rem', color: '#0f172a', marginBottom: '0.75rem' }}>
                1. Select Target Track
              </label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                <button
                  type="button"
                  onClick={() => setTrack('STUDENT')}
                  style={{
                    padding: '0.85rem 1rem',
                    borderRadius: '10px',
                    border: track === 'STUDENT' ? '2px solid #2563eb' : '1px solid #cbd5e1',
                    background: track === 'STUDENT' ? '#eff6ff' : '#ffffff',
                    color: track === 'STUDENT' ? '#1e40af' : '#334155',
                    fontWeight: 700,
                    textAlign: 'left',
                    cursor: 'pointer'
                  }}
                >
                  🎓 Student / Entry Level
                  <div style={{ fontSize: '0.75rem', fontWeight: 500, color: '#64748b', marginTop: '2px' }}>
                    Focus on fundamentals, coding logic, and basic system design.
                  </div>
                </button>

                <button
                  type="button"
                  onClick={() => setTrack('WORKING_PROFESSIONAL')}
                  style={{
                    padding: '0.85rem 1rem',
                    borderRadius: '10px',
                    border: track === 'WORKING_PROFESSIONAL' ? '2px solid #2563eb' : '1px solid #cbd5e1',
                    background: track === 'WORKING_PROFESSIONAL' ? '#eff6ff' : '#ffffff',
                    color: track === 'WORKING_PROFESSIONAL' ? '#1e40af' : '#334155',
                    fontWeight: 700,
                    textAlign: 'left',
                    cursor: 'pointer'
                  }}
                >
                  💼 Working Professional
                  <div style={{ fontSize: '0.75rem', fontWeight: 500, color: '#64748b', marginTop: '2px' }}>
                    Focus on high-scale architecture, production outages, and leadership.
                  </div>
                </button>
              </div>
            </div>

            {/* Stream */}
            <div style={{ border: '1px solid #e2e8f0', borderRadius: '14px', padding: '1.25rem', background: '#f8fafc' }}>
              <label style={{ display: 'block', fontWeight: 700, fontSize: '0.95rem', color: '#0f172a', marginBottom: '0.75rem' }}>
                2. Select Domain Stream
              </label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                {availableStreams.map(s => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => setStream(s)}
                    style={{
                      padding: '0.65rem 1rem',
                      borderRadius: '8px',
                      border: stream === s ? '2px solid #2563eb' : '1px solid #cbd5e1',
                      background: stream === s ? '#eff6ff' : '#ffffff',
                      color: stream === s ? '#1e40af' : '#334155',
                      fontWeight: stream === s ? 700 : 500,
                      textAlign: 'left',
                      cursor: 'pointer',
                      fontSize: '0.875rem'
                    }}
                  >
                    {stream === s ? '✓ ' : ''}{s}
                  </button>
                ))}
              </div>
            </div>

            {/* Difficulty Tier */}
            <div style={{ border: '1px solid #e2e8f0', borderRadius: '14px', padding: '1.25rem', background: '#f8fafc' }}>
              <label style={{ display: 'block', fontWeight: 700, fontSize: '0.95rem', color: '#0f172a', marginBottom: '0.75rem' }}>
                3. Difficulty Tier
              </label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                {['EASY', 'MEDIUM', 'HARD'].map(d => (
                  <button
                    key={d}
                    type="button"
                    onClick={() => setDifficulty(d)}
                    style={{
                      padding: '0.65rem 1rem',
                      borderRadius: '8px',
                      border: difficulty === d ? '2px solid #2563eb' : '1px solid #cbd5e1',
                      background: difficulty === d ? '#eff6ff' : '#ffffff',
                      color: difficulty === d ? '#1e40af' : '#334155',
                      fontWeight: 700,
                      textAlign: 'left',
                      cursor: 'pointer',
                      fontSize: '0.875rem'
                    }}
                  >
                    {d === 'EASY' && '🟢 Easy Tier (Core Concepts)'}
                    {d === 'MEDIUM' && '🟡 Medium Tier (Standard Industry Interview)'}
                    {d === 'HARD' && '🔴 Hard Tier (FAANG/Tier-1 Level)'}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div style={{ textAlign: 'center', paddingTop: '1rem', borderTop: '1px solid #f1f5f9' }}>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleStartSession}
              disabled={starting}
              style={{ padding: '0.85rem 2.5rem', fontSize: '1rem', fontWeight: 800, borderRadius: '980px', boxShadow: '0 4px 14px rgba(37, 99, 235, 0.25)' }}
            >
              {starting ? 'Generating AI Session...' : '🚀 Start AI Interview Session (+50 XP)'}
            </button>
          </div>
        </div>
      )}

      {/* ACTIVE INTERVIEW PLAYER VIEW */}
      {viewState === 'active' && sessionData && (
        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '2rem', boxShadow: '0 2px 10px rgba(0,0,0,0.02)' }}>
          {/* Progress Header */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.5rem' }}>
            <div>
              <span style={{ fontSize: '0.8rem', fontWeight: 800, color: '#2563eb', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                Question {currentIndex + 1} of {sessionData.questions.length}
              </span>
              <h3 style={{ margin: '0.2rem 0 0', fontWeight: 800, color: '#0f172a' }}>
                {sessionData.session.stream} • <span style={{ color: '#64748b', fontSize: '0.9rem' }}>{sessionData.session.difficulty}</span>
              </h3>
            </div>

            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <span style={{ background: '#f1f5f9', color: '#334155', padding: '4px 10px', borderRadius: '6px', fontSize: '0.75rem', fontWeight: 700 }}>
                Category: {sessionData.questions[currentIndex]?.category || 'TECHNICAL'}
              </span>
            </div>
          </div>

          {/* Progress Bar */}
          <div style={{ height: '6px', background: '#e2e8f0', borderRadius: '980px', marginBottom: '1.75rem', overflow: 'hidden' }}>
            <div
              style={{
                height: '100%',
                width: `${((currentIndex + 1) / sessionData.questions.length) * 100}%`,
                background: '#2563eb',
                transition: 'width 0.3s ease'
              }}
            />
          </div>

          {/* Question Box */}
          <div style={{ background: 'linear-gradient(135deg, #f8fafc 0%, #eff6ff 100%)', border: '1px solid #cbd5e1', borderRadius: '14px', padding: '1.5rem', marginBottom: '1.5rem' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '1rem' }}>
              <div style={{ width: '42px', height: '42px', borderRadius: '50%', background: '#2563eb', color: '#ffffff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, flexShrink: 0 }}>
                AI
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#2563eb', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                  AI Technical Interviewer
                </div>
                <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#0f172a', lineHeight: 1.4 }}>
                  "{sessionData.questions[currentIndex]?.questionText}"
                </div>
              </div>
            </div>
          </div>

          {/* Learner Answer Box */}
          {!currentEvaluation ? (
            <div>
              <label style={{ display: 'block', fontWeight: 700, color: '#0f172a', marginBottom: '0.5rem', fontSize: '0.9rem' }}>
                Your Answer / Explanation:
              </label>
              <textarea
                className="input"
                rows="6"
                placeholder="Type your structured answer here. Include architectural concepts, code logic, or trade-offs..."
                value={answerInput}
                onChange={(e) => setAnswerInput(e.target.value)}
                style={{ width: '100%', padding: '0.85rem 1rem', borderRadius: '12px', border: '1px solid #cbd5e1', fontSize: '0.95rem', marginBottom: '1rem' }}
              />

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                  {answerInput.length} characters
                </span>

                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleSubmitAnswer}
                  disabled={evaluating || !answerInput.trim()}
                  style={{ padding: '0.75rem 1.75rem', fontWeight: 700, borderRadius: '10px' }}
                >
                  {evaluating ? 'Evaluating with AI...' : 'Submit Answer for AI Evaluation →'}
                </button>
              </div>
            </div>
          ) : (
            /* AI Feedback Card */
            <div style={{ background: '#ffffff', border: '2px solid #3b82f6', borderRadius: '14px', padding: '1.5rem', marginTop: '1rem', boxShadow: '0 4px 16px rgba(59, 130, 246, 0.08)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', borderBottom: '1px solid #f1f5f9', paddingBottom: '0.85rem' }}>
                <span style={{ fontSize: '1rem', fontWeight: 800, color: '#0f172a' }}>
                  Instant AI Evaluation
                </span>
                <span style={{ background: '#dcfce7', color: '#15803d', fontSize: '0.9rem', fontWeight: 800, padding: '4px 12px', borderRadius: '980px' }}>
                  Score: {currentEvaluation.score} / 10
                </span>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1.25rem' }}>
                <div style={{ background: '#f8fafc', padding: '1rem', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#16a34a', textTransform: 'uppercase', marginBottom: '0.3rem' }}>
                    💪 Key Strengths
                  </div>
                  <p style={{ margin: 0, fontSize: '0.875rem', color: '#334155' }}>
                    {currentEvaluation.keyStrengths}
                  </p>
                </div>

                <div style={{ background: '#f8fafc', padding: '1rem', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#d97706', textTransform: 'uppercase', marginBottom: '0.3rem' }}>
                    🎯 Areas to Improve
                  </div>
                  <p style={{ margin: 0, fontSize: '0.875rem', color: '#334155' }}>
                    {currentEvaluation.areasToImprove}
                  </p>
                </div>
              </div>

              <div style={{ marginBottom: '1.25rem' }}>
                <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#2563eb', textTransform: 'uppercase', marginBottom: '0.3rem' }}>
                  💡 Ideal Answer / Reference Answer
                </div>
                <div style={{ background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: '10px', padding: '1rem', fontSize: '0.875rem', color: '#1e3a8a', lineHeight: 1.5 }}>
                  {currentEvaluation.idealAnswer}
                </div>
              </div>

              <div style={{ textAlign: 'right' }}>
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleNextQuestion}
                  style={{ padding: '0.75rem 1.75rem', fontWeight: 700, borderRadius: '10px' }}
                >
                  {currentIndex + 1 < sessionData.questions.length ? 'Next Question →' : 'Finish & View Readiness Report →'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* SCORECARD / REPORT VIEW */}
      {viewState === 'scorecard' && sessionData && (
        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '2rem', boxShadow: '0 2px 10px rgba(0,0,0,0.02)' }}>
          <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
            <div style={{ display: 'inline-block', background: '#dcfce7', color: '#15803d', fontSize: '0.85rem', fontWeight: 800, padding: '6px 16px', borderRadius: '980px', marginBottom: '0.75rem' }}>
              🎉 SESSION COMPLETED • +{sessionData.session.xpEarned || 50} XP AWARDED!
            </div>
            <h2 style={{ margin: '0 0 0.5rem', fontSize: '1.85rem', fontWeight: 800, color: '#0f172a' }}>
              Mock Interview Performance Report
            </h2>
            <p style={{ color: '#64748b', fontSize: '0.95rem' }}>
              {sessionData.session.stream} • {sessionData.session.track} Track ({sessionData.session.difficulty} Tier)
            </p>
          </div>

          {/* Metric Cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.25rem', marginBottom: '2rem' }}>
            <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '14px', padding: '1.5rem', textAlign: 'center' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>Overall Score</div>
              <div style={{ fontSize: '2.5rem', fontWeight: 900, color: '#2563eb', margin: '0.2rem 0' }}>
                {sessionData.session.overallScore}<span style={{ fontSize: '1.2rem', color: '#94a3b8' }}>/100</span>
              </div>
            </div>

            <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '14px', padding: '1.5rem', textAlign: 'center' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>Readiness Rating</div>
              <div style={{ fontSize: '1.35rem', fontWeight: 800, color: sessionData.session.readinessLevel === 'EXCELLENT' ? '#16a34a' : sessionData.session.readinessLevel === 'GOOD' ? '#2563eb' : '#d97706', margin: '0.5rem 0' }}>
                {sessionData.session.readinessLevel}
              </div>
            </div>

            <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '14px', padding: '1.5rem', textAlign: 'center' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>Bonus XP Earned</div>
              <div style={{ fontSize: '2rem', fontWeight: 900, color: '#16a34a', margin: '0.2rem 0' }}>
                ⚡ +{sessionData.session.xpEarned || 50} XP
              </div>
            </div>
          </div>

          {/* Feedback Summary */}
          <div style={{ background: '#f0f9ff', border: '1px solid #bae6fd', borderRadius: '14px', padding: '1.5rem', marginBottom: '2rem' }}>
            <h4 style={{ margin: '0 0 0.5rem', fontWeight: 800, color: '#0369a1' }}>Summary Feedback</h4>
            <p style={{ margin: 0, color: '#0c4a6e', fontSize: '0.95rem', lineHeight: 1.5 }}>
              {sessionData.session.summaryFeedback}
            </p>
          </div>

          {/* Detailed Question Review */}
          <h3 style={{ margin: '0 0 1rem', fontWeight: 800, color: '#0f172a' }}>Question Breakdown</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '2rem' }}>
            {sessionData.questions.map((q, idx) => (
              <div key={q.id} style={{ border: '1px solid #e2e8f0', borderRadius: '12px', padding: '1.25rem', background: '#ffffff' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                  <span style={{ fontSize: '0.8rem', fontWeight: 800, color: '#2563eb' }}>Q{idx + 1}. {q.category}</span>
                  <span style={{ fontWeight: 800, fontSize: '0.85rem', color: '#16a34a' }}>Score: {q.score}/10</span>
                </div>
                <div style={{ fontWeight: 700, color: '#0f172a', marginBottom: '0.75rem' }}>"{q.questionText}"</div>

                {q.learnerAnswer && (
                  <div style={{ fontSize: '0.85rem', color: '#475569', background: '#f8fafc', padding: '0.75rem', borderRadius: '8px', marginBottom: '0.75rem' }}>
                    <strong>Your Response:</strong> {q.learnerAnswer}
                  </div>
                )}

                {q.aiFeedback && (
                  <div style={{ fontSize: '0.85rem', color: '#1e3a8a', background: '#eff6ff', padding: '0.75rem', borderRadius: '8px' }}>
                    <strong>AI Feedback:</strong> {q.aiFeedback}
                  </div>
                )}
              </div>
            ))}
          </div>

          <div style={{ textAlign: 'center' }}>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => setViewState('setup')}
              style={{ padding: '0.75rem 2rem', fontWeight: 700, borderRadius: '980px' }}
            >
              Start Another Interview Session
            </button>
          </div>
        </div>
      )}

      {/* HISTORY VIEW */}
      {viewState === 'history' && (
        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.75rem', boxShadow: '0 2px 10px rgba(0,0,0,0.02)' }}>
          <h3 style={{ margin: '0 0 1.25rem', fontWeight: 800, color: '#0f172a' }}>Past Interview Sessions</h3>

          {loadingHistory ? (
            <p style={{ textAlign: 'center', color: '#64748b', padding: '2rem' }}>Loading interview history...</p>
          ) : historyList.length > 0 ? (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid #e2e8f0', color: '#475569', fontSize: '0.85rem' }}>
                    <th style={{ padding: '0.85rem' }}>Date</th>
                    <th style={{ padding: '0.85rem' }}>Stream</th>
                    <th style={{ padding: '0.85rem' }}>Track</th>
                    <th style={{ padding: '0.85rem' }}>Difficulty</th>
                    <th style={{ padding: '0.85rem' }}>Score</th>
                    <th style={{ padding: '0.85rem' }}>Readiness</th>
                    <th style={{ padding: '0.85rem', textAlign: 'right' }}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {historyList.map(h => (
                    <tr key={h.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                      <td style={{ padding: '0.85rem', fontSize: '0.85rem', color: '#64748b' }}>
                        {h.createdAt ? new Date(h.createdAt).toLocaleDateString() : 'Recent'}
                      </td>
                      <td style={{ padding: '0.85rem', fontWeight: 700, color: '#0f172a' }}>{h.stream}</td>
                      <td style={{ padding: '0.85rem', fontSize: '0.85rem' }}>{h.track}</td>
                      <td style={{ padding: '0.85rem', fontSize: '0.85rem' }}>{h.difficulty}</td>
                      <td style={{ padding: '0.85rem', fontWeight: 800, color: '#2563eb' }}>{h.overallScore}/100</td>
                      <td style={{ padding: '0.85rem' }}>
                        <span className={`badge ${h.readinessLevel === 'EXCELLENT' ? 'badge-success' : 'badge-secondary'}`}>
                          {h.readinessLevel}
                        </span>
                      </td>
                      <td style={{ padding: '0.85rem', textAlign: 'right' }}>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}
                          onClick={() => handleViewHistoryReport(h)}
                        >
                          View Report
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p style={{ textAlign: 'center', color: '#64748b', padding: '2rem' }}>No past mock interviews found. Start your first session above!</p>
          )}
        </div>
      )}
    </div>
  );
}
