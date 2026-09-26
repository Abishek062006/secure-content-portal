import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api';

export default function AdminHackathons() {
  const navigate = useNavigate();
  const [hackathons, setHackathons] = useState([]);
  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);

  const [formData, setFormData] = useState({
    title: '',
    organizer: 'GradientNova & Global Partners',
    description: '',
    bannerUrl: '',
    stream: 'Engineering & Web Dev',
    mode: 'ONLINE',
    location: 'Virtual / Global',
    prizePool: '$10,000 Prize Pool',
    registrationUrl: '',
    status: 'ACTIVE',
    featured: false,
    pointsReward: 25,
  });

  useEffect(() => {
    loadHackathons();
  }, []);

  async function loadHackathons() {
    setLoading(true);
    try {
      const data = await api.getAdminHackathons('all', 'all');
      setHackathons(data || []);
    } catch (err) {
      console.error('Failed to load admin hackathons:', err);
    } finally {
      setLoading(false);
    }
  }

  function handleOpenCreate() {
    setEditingId(null);
    setFormData({
      title: '',
      organizer: 'GradientNova & Global Partners',
      description: '',
      bannerUrl: '',
      stream: 'Engineering & Web Dev',
      mode: 'ONLINE',
      location: 'Virtual / Global',
      prizePool: '$10,000 Prize Pool',
      registrationUrl: '',
      status: 'ACTIVE',
      featured: false,
      pointsReward: 25,
    });
    setModalOpen(true);
  }

  function handleOpenEdit(h) {
    setEditingId(h.id);
    setFormData({
      title: h.title || '',
      organizer: h.organizer || 'GradientNova & Global Partners',
      description: h.description || '',
      bannerUrl: h.bannerUrl || '',
      stream: h.stream || 'Engineering & Web Dev',
      mode: h.mode || 'ONLINE',
      location: h.location || 'Virtual',
      prizePool: h.prizePool || '',
      registrationUrl: h.registrationUrl || '',
      status: h.status || 'ACTIVE',
      featured: Boolean(h.featured),
      pointsReward: h.pointsReward || 25,
    });
    setModalOpen(true);
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!formData.title || !formData.registrationUrl) {
      alert('Title and Registration URL are required.');
      return;
    }

    try {
      if (editingId) {
        await api.updateAdminHackathon(editingId, formData);
      } else {
        await api.createAdminHackathon(formData);
      }
      setModalOpen(false);
      loadHackathons();
    } catch (err) {
      alert('Action failed: ' + err.message);
    }
  }

  async function handleDelete(id) {
    if (!window.confirm('Are you sure you want to delete this hackathon listing?')) return;
    try {
      await api.deleteAdminHackathon(id);
      loadHackathons();
    } catch (err) {
      alert('Delete failed: ' + err.message);
    }
  }

  return (
    <div className="container-wide" style={{ width: '100%', maxWidth: '1100px', margin: '0 auto', padding: '2rem 1.5rem' }}>
      <div
        style={{
          background: 'linear-gradient(135deg, #ffffff 0%, #eff6ff 100%)',
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
          <h1 style={{ margin: 0, fontSize: '1.85rem', fontWeight: 800, color: '#0f172a' }}>
            Manage Hackathon Listings 🛠️
          </h1>
          <p style={{ margin: '0.5rem 0 0', color: '#64748b', fontSize: '1rem' }}>
            Publish, edit, and moderate hackathon opportunities for learners across all streams.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleOpenCreate}
            style={{ fontWeight: 700 }}
          >
            + Create Hackathon
          </button>
        </div>
      </div>

      <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '1.5rem', width: '100%', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
        <h3 style={{ margin: '0 0 1.25rem', fontWeight: 700, color: '#0f172a' }}>All Hackathons List</h3>

        {loading ? (
          <p style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>Loading listings...</p>
        ) : hackathons.length > 0 ? (
          <div style={{ overflowX: 'auto', width: '100%' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #e2e8f0', color: '#475569', fontSize: '0.85rem' }}>
                  <th style={{ padding: '0.85rem' }}>Title</th>
                  <th style={{ padding: '0.85rem' }}>Stream</th>
                  <th style={{ padding: '0.85rem' }}>Mode</th>
                  <th style={{ padding: '0.85rem' }}>Prize Pool</th>
                  <th style={{ padding: '0.85rem' }}>Status</th>
                  <th style={{ padding: '0.85rem' }}>XP Reward</th>
                  <th style={{ padding: '0.85rem', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {hackathons.map(h => (
                  <tr key={h.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '0.85rem', fontWeight: 700, color: '#0f172a' }}>
                      {h.title} {h.featured && <span style={{ color: '#3b82f6', fontSize: '0.75rem' }}>[FEATURED]</span>}
                    </td>
                    <td style={{ padding: '0.85rem', color: '#64748b', fontSize: '0.875rem' }}>{h.stream}</td>
                    <td style={{ padding: '0.85rem', fontSize: '0.85rem' }}>{h.mode}</td>
                    <td style={{ padding: '0.85rem', fontWeight: 700, color: '#10b981' }}>{h.prizePool}</td>
                    <td style={{ padding: '0.85rem' }}>
                      <span className={`badge ${h.status === 'ACTIVE' ? 'badge-success' : 'badge-secondary'}`}>
                        {h.status}
                      </span>
                    </td>
                    <td style={{ padding: '0.85rem', fontWeight: 800, color: '#2563eb' }}>⚡ {h.pointsReward} XP</td>
                    <td style={{ padding: '0.85rem', textAlign: 'right' }}>
                      <div style={{ display: 'flex', gap: '0.4rem', justifyContent: 'flex-end' }}>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          style={{ padding: '0.3rem 0.65rem', fontSize: '0.8rem' }}
                          onClick={() => handleOpenEdit(h)}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          style={{ padding: '0.3rem 0.65rem', fontSize: '0.8rem', color: '#dc2626' }}
                          onClick={() => handleDelete(h.id)}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>No hackathons created yet.</p>
        )}
      </div>

      {/* Modal */}
      {modalOpen && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '2rem', width: '100%', maxWidth: '540px', boxShadow: '0 20px 48px rgba(0,0,0,0.15)', maxHeight: '90vh', overflowY: 'auto' }}>
            <h3 style={{ margin: '0 0 1.25rem', fontWeight: 700, color: '#0f172a' }}>
              {editingId ? 'Edit Hackathon Listing' : 'Create Hackathon Listing'}
            </h3>

            <form onSubmit={handleSubmit}>
              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.3rem' }}>Hackathon Title</label>
                <input
                  type="text"
                  className="input"
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                  style={{ width: '100%', padding: '0.6rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  required
                />
              </div>

              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.3rem' }}>Organizer / Host Name</label>
                <input
                  type="text"
                  className="input"
                  placeholder="e.g. Google AI, Meta, AWS, GradientNova"
                  value={formData.organizer}
                  onChange={(e) => setFormData({ ...formData, organizer: e.target.value })}
                  style={{ width: '100%', padding: '0.6rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  required
                />
              </div>

              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.3rem' }}>Description</label>
                <textarea
                  className="input"
                  rows="3"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  style={{ width: '100%', padding: '0.6rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
                <div>
                  <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.3rem' }}>Stream</label>
                  <select
                    className="input"
                    value={formData.stream}
                    onChange={(e) => setFormData({ ...formData, stream: e.target.value })}
                    style={{ width: '100%', padding: '0.6rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  >
                    <option value="Engineering & Web Dev">Engineering & Web Dev</option>
                    <option value="AI & Data Science">AI & Data Science</option>
                    <option value="UI/UX & Design">UI/UX & Design</option>
                    <option value="Cloud & Infrastructure">Cloud & Infrastructure</option>
                    <option value="Global">Global</option>
                  </select>
                </div>

                <div>
                  <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.3rem' }}>Mode</label>
                  <select
                    className="input"
                    value={formData.mode}
                    onChange={(e) => setFormData({ ...formData, mode: e.target.value })}
                    style={{ width: '100%', padding: '0.6rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  >
                    <option value="ONLINE">ONLINE</option>
                    <option value="OFFLINE">OFFLINE</option>
                    <option value="HYBRID">HYBRID</option>
                  </select>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
                <div>
                  <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.3rem' }}>Prize Pool</label>
                  <input
                    type="text"
                    className="input"
                    placeholder="e.g. $10,000"
                    value={formData.prizePool}
                    onChange={(e) => setFormData({ ...formData, prizePool: e.target.value })}
                    style={{ width: '100%', padding: '0.6rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  />
                </div>

                <div>
                  <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.3rem' }}>XP Reward</label>
                  <input
                    type="number"
                    className="input"
                    value={formData.pointsReward}
                    onChange={(e) => setFormData({ ...formData, pointsReward: parseInt(e.target.value, 10) || 25 })}
                    style={{ width: '100%', padding: '0.6rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  />
                </div>
              </div>

              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.3rem' }}>Registration URL (External Link)</label>
                <input
                  type="url"
                  className="input"
                  placeholder="https://devpost.com"
                  value={formData.registrationUrl}
                  onChange={(e) => setFormData({ ...formData, registrationUrl: e.target.value })}
                  style={{ width: '100%', padding: '0.6rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                  required
                />
              </div>

              <div style={{ marginBottom: '1.25rem' }}>
                <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.3rem' }}>Banner Image URL</label>
                <input
                  type="text"
                  className="input"
                  placeholder="https://images.unsplash.com/..."
                  value={formData.bannerUrl}
                  onChange={(e) => setFormData({ ...formData, bannerUrl: e.target.value })}
                  style={{ width: '100%', padding: '0.6rem 0.85rem', borderRadius: '8px', border: '1px solid #cbd5e1' }}
                />
              </div>

              <div style={{ display: 'flex', gap: '1.5rem', marginBottom: '1.5rem', alignItems: 'center' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 600, fontSize: '0.875rem' }}>
                  <input
                    type="checkbox"
                    checked={formData.featured}
                    onChange={(e) => setFormData({ ...formData, featured: e.target.checked })}
                  />
                  Featured Hackathon
                </label>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setModalOpen(false)}
                >
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  {editingId ? 'Save Changes' : 'Publish Hackathon'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
