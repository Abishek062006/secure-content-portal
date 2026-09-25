import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';
import CoverPicker from '../../components/CoverPicker';

export default function NewCourse() {
  const navigate = useNavigate();
  const [errorMessage, setErrorMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [coverFile, setCoverFile] = useState(null);

  async function onSubmit(e) {
    e.preventDefault();
    setErrorMessage(null);
    setSubmitting(true);

    const form = e.currentTarget;
    const formData = new FormData();
    formData.set('title', form.title.value);
    formData.set('description', form.description.value);
    formData.set('category', form.category.value);
    if (coverFile) formData.set('thumbnail', coverFile);

    try {
      const created = await api.upload('/api/admin/courses', formData);
      navigate(`/admin/courses/${created.id}/edit`, {
        state: { success: 'Course created. Now add modules and lessons, then publish it.' },
      });
    } catch (err) {
      setErrorMessage(err.message);
      setSubmitting(false);
    }
  }

  return (
    <div className="container container-course-form">
      <Alert error={errorMessage} />
      <h1>New course</h1>
      <p className="field-hint" style={{ marginTop: -10 }}>
        Start with the basics. You'll add modules and video lessons on the next screen.
      </p>
      <form className="form-panel course-form" onSubmit={onSubmit}>
        <div className="course-form-cover">
          <label>Cover image</label>
          <CoverPicker onPick={setCoverFile} onRemove={() => setCoverFile(null)} />
        </div>
        <div className="course-form-fields">
        <div className="field">
          <label htmlFor="title">Title</label>
          <input id="title" name="title" type="text" maxLength={200} required />
        </div>

        <div className="field">
          <label htmlFor="description">Description</label>
          <textarea id="description" name="description" maxLength={2000} rows={4} />
        </div>

        <div className="field">
          <label htmlFor="category">Category / tag</label>
          <input id="category" name="category" type="text" maxLength={80} placeholder="e.g. Onboarding" />
        </div>

        </div>
        <div className="form-actions">
          <Link className="btn" to="/admin/courses">Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Creating…' : 'Create course'}
          </button>
        </div>
      </form>
    </div>
  );
}
