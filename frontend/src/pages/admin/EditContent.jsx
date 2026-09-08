import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';

export default function EditContent() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [item, setItem] = useState(null);
  const [form, setForm] = useState({ title: '', description: '', category: '' });
  const [errorMessage, setErrorMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    api.get(`/api/admin/content`)
      .then((items) => {
        const found = items.find((i) => i.id === id);
        if (found) {
          setItem(found);
          setForm({ title: found.title, description: found.description || '', category: found.category || '' });
        } else {
          setErrorMessage('Content item not found.');
        }
      })
      .catch((err) => setErrorMessage(err.message));
  }, [id]);

  async function onSubmit(e) {
    e.preventDefault();
    setErrorMessage(null);
    setSubmitting(true);
    try {
      const updated = await api.put(`/api/admin/content/${id}`, form);
      navigate('/admin/content', { state: { success: `"${updated.title}" updated.` } });
    } catch (err) {
      setErrorMessage(err.message);
      setSubmitting(false);
    }
  }

  if (!item) {
    return (
      <div className="container">
        <Alert error={errorMessage} />
      </div>
    );
  }

  return (
    <div className="container">
      <Alert error={errorMessage} />
      <h1>Edit content</h1>
      <p className="field-hint" style={{ marginTop: -10 }}>
        {item.originalFilename} &middot; {item.sizeLabel}
        {' '}— the file itself can't be replaced here; delete and re-upload if you need to change it.
      </p>

      <form className="form-panel" onSubmit={onSubmit}>
        <div className="field">
          <label htmlFor="title">Title</label>
          <input id="title" type="text" maxLength={200} value={form.title}
                 onChange={(e) => setForm({ ...form, title: e.target.value })} required />
        </div>

        <div className="field">
          <label htmlFor="description">Description</label>
          <textarea id="description" maxLength={2000} rows={4} value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </div>

        <div className="field">
          <label htmlFor="category">Category / tag</label>
          <input id="category" type="text" maxLength={80} value={form.category}
                 onChange={(e) => setForm({ ...form, category: e.target.value })} />
        </div>

        <div className="form-actions">
          <Link className="btn" to="/admin/content">Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>
    </div>
  );
}
