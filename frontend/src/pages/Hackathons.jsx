import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';

function getTimeRemaining(deadlineStr) {
  if (!deadlineStr) return null;
  const total = Date.parse(deadlineStr) - Date.parse(new Date());
  if (total <= 0) return { days: 0, hours: 0, expired: true };
  const seconds = Math.floor((total / 1000) % 60);
  const minutes = Math.floor((total / 1000 / 60) % 60);
  const hours = Math.floor((total / (1000 * 60 * 60)) % 24);
  const days = Math.floor(total / (1000 * 60 * 60 * 24));
  return { total, days, hours, minutes, seconds, expired: false };
}

const STREAM_ICONS = {
  'Engineering & Web Dev': '💻',
  'AI & Data Science': '🤖',
  'UI/UX & Design': '🎨',
  'Cloud & Infrastructure': '☁️',
  'Core CS & Algorithms': '⚙️',
  'Global': '🌍'
};

export default function Hackathons() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [stream, setStream] = useState('all');
  const [mode, setMode] = useState('all');
  const [statusFilter, setStatusFilter] = useState('all'); // 'all' | 'registered' | 'featured'
  const [searchQuery, setSearchQuery] = useState('');
  const [availableStreams, setAvailableStreams] = useState([]);
  const [hackathons, setHackathons] = useState([]);
  const [loading, setLoading] = useState(true);

  const [registeringId, setRegisteringId] = useState(null);
  const [toastMessage, setToastMessage] = useState(null);

  useEffect(() => {
    loadStreams();
  }, []);

  useEffect(() => {
    loadHackathons();
  }, [stream, mode]);

  async function loadStreams() {
    try {
      const data = await api.getStreams();
      if (Array.isArray(data)) setAvailableStreams(data);
    } catch {
      setAvailableStreams([
        'Engineering & Web Dev',
        'AI & Data Science',
        'UI/UX & Design',
        'Cloud & Infrastructure'
      ]);
    }
  }

  async function loadHackathons() {
    setLoading(true);
    try {
      const data = await api.getHackathons(stream, mode);
      setHackathons(data || []);
    } catch (err) {
      console.error('Failed to load hackathons:', err);
    } finally {
      setLoading(false);
    }
  }

  async function handleRegister(hackathon) {
    setRegisteringId(hackathon.id);
    setToastMessage(null);

    if (hackathon.registrationUrl) {
      window.open(hackathon.registrationUrl, '_blank', 'noopener,noreferrer');
    }

    try {
      const res = await api.registerHackathon(hackathon.id);
      if (res.alreadyRegistered) {
        setToastMessage({
          type: 'info',
          text: `You have already registered for ${hackathon.title}. Opening portal...`
        });
      } else {
        setToastMessage({
          type: 'success',
          text: `🎉 ${res.message}`
        });
        loadHackathons();
      }
    } catch (err) {
      setToastMessage({
        type: 'error',
        text: err.message || 'Registration tracking failed'
      });
    } finally {
      setRegisteringId(null);
    }
  }

  function handleFindTeammates(hackathon) {
    const postTag = `#${hackathon.title.replace(/[^a-zA-Z0-9]/g, '')}`;
    navigate(`/feed?search=${encodeURIComponent(postTag)}&teammate=true`);
  }

  function handleCopyReminder(hackathon) {
    const details = `📅 Hackathon Reminder: ${hackathon.title}\n🏢 Host: ${hackathon.organizer || 'GradientNova & Partners'}\n🏆 Prize: ${hackathon.prizePool}\n🔗 Link: ${hackathon.registrationUrl}`;
    navigator.clipboard?.writeText(details);
    setToastMessage({
      type: 'info',
      text: `📋 Copied calendar reminder for "${hackathon.title}" to clipboard!`
    });
  }

  const filteredHackathons = hackathons.filter(h => {
    const matchesSearch = !searchQuery.trim() ||
      h.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (h.organizer && h.organizer.toLowerCase().includes(searchQuery.toLowerCase())) ||
      h.stream.toLowerCase().includes(searchQuery.toLowerCase());

    if (!matchesSearch) return false;

    if (statusFilter === 'registered') return Boolean(h.isRegistered);
    if (statusFilter === 'featured') return Boolean(h.featured);
    return true;
  });

  return (
    <div className="container-wide" style={{ width: '100%', maxWidth: '1100px', margin: '0 auto', padding: '2rem 1.5rem' }}>
      {/* Header Banner */}
      <div
        style={{
          background: 'linear-gradient(135deg, #ffffff 0%, #eff6ff 100%)',
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
                Hackathon & Innovation Arena
              </h1>
              <span style={{ background: '#dbeafe', color: '#1e40af', fontSize: '0.75rem', fontWeight: 700, padding: '4px 10px', borderRadius: '980px', textTransform: 'uppercase' }}>
                Global Sprint
              </span>
            </div>
            <p style={{ margin: '0.5rem 0 0', color: '#64748b', fontSize: '1rem' }}>
              Discover global hackathons, build real-world projects, recruit teammates, and earn bonus XP!
            </p>
          </div>
        </div>
      </div>

      {toastMessage && (
        <div className={`alert alert-${toastMessage.type}`} style={{ marginBottom: '1.5rem', borderRadius: '12px' }}>
          {toastMessage.text}
        </div>
      )}

      {/* Stream Badges Bar */}
      <div style={{ display: 'flex', gap: '0.6rem', overflowX: 'auto', paddingBottom: '0.5rem', marginBottom: '1.5rem' }}>
        <button
          type="button"
          onClick={() => setStream('all')}
          style={{
            padding: '0.55rem 1.1rem',
            borderRadius: '980px',
            border: stream === 'all' ? '2px solid #2563eb' : '1px solid #e2e8f0',
            background: stream === 'all' ? '#eff6ff' : '#ffffff',
            color: stream === 'all' ? '#1e40af' : '#475569',
            fontWeight: stream === 'all' ? 700 : 500,
            whiteSpace: 'nowrap',
            fontSize: '0.85rem',
            cursor: 'pointer'
          }}
        >
          🌟 All Streams
        </button>
        {availableStreams.map(s => (
          <button
            key={s}
            type="button"
            onClick={() => setStream(s)}
            style={{
              padding: '0.55rem 1.1rem',
              borderRadius: '980px',
              border: stream === s ? '2px solid #2563eb' : '1px solid #e2e8f0',
              background: stream === s ? '#eff6ff' : '#ffffff',
              color: stream === s ? '#1e40af' : '#475569',
              fontWeight: stream === s ? 700 : 500,
              whiteSpace: 'nowrap',
              fontSize: '0.85rem',
              cursor: 'pointer'
            }}
          >
            {STREAM_ICONS[s] || '💡'} {s}
          </button>
        ))}
      </div>

      {/* Filter & Search Toolbar */}
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
        {/* Search Bar */}
        <div style={{ flex: '1 1 280px', maxWidth: '400px' }}>
          <input
            type="text"
            className="input"
            placeholder="🔍 Search hackathons by title, host, or tags..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{ width: '100%', padding: '0.55rem 1rem', borderRadius: '10px', border: '1px solid #cbd5e1', fontSize: '0.875rem' }}
          />
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem', flexWrap: 'wrap' }}>
          {/* Status Tabs */}
          <div style={{ display: 'flex', background: '#f1f5f9', padding: '3px', borderRadius: '10px' }}>
            <button
              type="button"
              onClick={() => setStatusFilter('all')}
              style={{
                padding: '0.4rem 0.85rem',
                borderRadius: '8px',
                border: 'none',
                background: statusFilter === 'all' ? '#ffffff' : 'transparent',
                color: statusFilter === 'all' ? '#0f172a' : '#64748b',
                fontWeight: statusFilter === 'all' ? 700 : 500,
                fontSize: '0.8rem',
                cursor: 'pointer',
                boxShadow: statusFilter === 'all' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none'
              }}
            >
              All Listings
            </button>
            <button
              type="button"
              onClick={() => setStatusFilter('featured')}
              style={{
                padding: '0.4rem 0.85rem',
                borderRadius: '8px',
                border: 'none',
                background: statusFilter === 'featured' ? '#ffffff' : 'transparent',
                color: statusFilter === 'featured' ? '#0f172a' : '#64748b',
                fontWeight: statusFilter === 'featured' ? 700 : 500,
                fontSize: '0.8rem',
                cursor: 'pointer',
                boxShadow: statusFilter === 'featured' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none'
              }}
            >
              ⭐ Featured
            </button>
            <button
              type="button"
              onClick={() => setStatusFilter('registered')}
              style={{
                padding: '0.4rem 0.85rem',
                borderRadius: '8px',
                border: 'none',
                background: statusFilter === 'registered' ? '#ffffff' : 'transparent',
                color: statusFilter === 'registered' ? '#0f172a' : '#64748b',
                fontWeight: statusFilter === 'registered' ? 700 : 500,
                fontSize: '0.8rem',
                cursor: 'pointer',
                boxShadow: statusFilter === 'registered' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none'
              }}
            >
              ✓ Registered
            </button>
          </div>

          {/* Mode Selector */}
          <select
            value={mode}
            onChange={(e) => setMode(e.target.value)}
            className="input"
            style={{ width: 'auto', padding: '0.45rem 0.9rem', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.85rem' }}
          >
            <option value="all">All Modes</option>
            <option value="ONLINE">Online / Virtual</option>
            <option value="OFFLINE">In-Person</option>
            <option value="HYBRID">Hybrid</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '3rem', textAlign: 'center', color: '#64748b' }}>
          Loading active hackathons...
        </div>
      ) : filteredHackathons.length > 0 ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(330px, 1fr))', gap: '1.5rem', width: '100%' }}>
          {filteredHackathons.map((h) => {
            const countdown = getTimeRemaining(h.registrationDeadline);
            return (
              <div
                key={h.id}
                style={{
                  background: '#ffffff',
                  border: h.featured ? '2px solid #3b82f6' : '1px solid #e2e8f0',
                  borderRadius: '16px',
                  overflow: 'hidden',
                  display: 'flex',
                  flexDirection: 'column',
                  boxShadow: h.featured ? '0 6px 20px rgba(59, 130, 246, 0.08)' : '0 2px 10px rgba(0,0,0,0.02)',
                  position: 'relative'
                }}
              >
                {/* Banner Header Image */}
                <div style={{ height: '140px', background: '#0f172a', position: 'relative', overflow: 'hidden' }}>
                  {h.bannerUrl ? (
                    <img src={h.bannerUrl} alt={h.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : (
                    <div style={{ width: '100%', height: '100%', background: 'linear-gradient(135deg, #1e293b 0%, #3b82f6 100%)' }} />
                  )}
                  <div style={{ position: 'absolute', top: '12px', left: '12px', display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                    {h.featured && (
                      <span style={{ background: '#3b82f6', color: '#ffffff', fontSize: '0.7rem', fontWeight: 800, padding: '3px 8px', borderRadius: '6px', textTransform: 'uppercase' }}>
                        ⭐ FEATURED
                      </span>
                    )}
                    <span style={{ background: 'rgba(15, 23, 42, 0.8)', color: '#ffffff', fontSize: '0.7rem', fontWeight: 700, padding: '3px 8px', borderRadius: '6px', backdropFilter: 'blur(4px)' }}>
                      {h.mode}
                    </span>
                  </div>

                  {h.prizePool && (
                    <div style={{ position: 'absolute', bottom: '12px', right: '12px', background: '#10b981', color: '#ffffff', fontSize: '0.75rem', fontWeight: 800, padding: '4px 10px', borderRadius: '980px', boxShadow: '0 2px 8px rgba(0,0,0,0.2)' }}>
                      🏆 {h.prizePool}
                    </div>
                  )}
                </div>

                {/* Card Body */}
                <div style={{ padding: '1.25rem', flex: 1, display: 'flex', flexDirection: 'column' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                    <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#2563eb', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      {STREAM_ICONS[h.stream] || '💡'} {h.stream}
                    </span>
                    <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>
                      👥 {h.participantCount} registered
                    </span>
                  </div>

                  {/* Host Name / Organizer */}
                  <div style={{ fontSize: '0.8rem', fontWeight: 700, color: '#475569', marginBottom: '0.4rem', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    🏢 Host: <span style={{ color: '#0f172a' }}>{h.organizer || 'GradientNova & Partners'}</span>
                  </div>

                  <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.15rem', fontWeight: 800, color: '#0f172a', lineHeight: 1.3 }}>
                    {h.title}
                  </h3>

                  <p style={{ margin: '0 0 1rem', color: '#64748b', fontSize: '0.875rem', lineHeight: 1.5, display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                    {h.description}
                  </p>

                  {/* Countdown Timer Row */}
                  {countdown && !countdown.expired && (
                    <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '0.6rem 0.85rem', marginBottom: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: '0.75rem', color: '#475569', fontWeight: 600 }}>Deadline:</span>
                      <span style={{ fontSize: '0.85rem', color: '#dc2626', fontWeight: 800 }}>
                        ⏰ {countdown.days}d {countdown.hours}h {countdown.minutes}m left
                      </span>
                    </div>
                  )}

                  <div style={{ marginTop: 'auto', paddingTop: '1rem', borderTop: '1px solid #f1f5f9', display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button
                        type="button"
                        className={`btn ${h.isRegistered ? 'btn-secondary' : 'btn-primary'}`}
                        style={{ flex: 1, padding: '0.6rem 1rem', fontWeight: 700, borderRadius: '10px', fontSize: '0.875rem' }}
                        disabled={registeringId === h.id}
                        onClick={() => handleRegister(h)}
                      >
                        {h.isRegistered ? '✓ Registered (Open Link)' : registeringId === h.id ? 'Registering...' : `Register Now (+${h.pointsReward} XP)`}
                      </button>

                      <button
                        type="button"
                        className="btn btn-secondary"
                        style={{ padding: '0.6rem 0.85rem', borderRadius: '10px', fontSize: '0.85rem' }}
                        onClick={() => handleCopyReminder(h)}
                        title="Copy Event Reminder to Clipboard"
                      >
                        📅
                      </button>

                      <button
                        type="button"
                        className="btn btn-secondary"
                        style={{ padding: '0.6rem 0.85rem', borderRadius: '10px', fontSize: '0.85rem' }}
                        onClick={() => handleFindTeammates(h)}
                        title="Find teammates in Community Feed"
                      >
                        🤝
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '3rem', textAlign: 'center', color: '#64748b' }}>
          No active hackathons found for the selected search/filter.
        </div>
      )}
    </div>
  );
}
