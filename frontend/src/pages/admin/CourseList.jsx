import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { API_BASE, api } from '../../api';
import Alert from '../../components/Alert';
import ConfirmDialog from '../../components/ConfirmDialog';

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
}

export default function CourseList() {
  const [courses, setCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const location = useLocation();
  const navigate = useNavigate();
  const [successMessage] = useState(location.state?.success || null);

  useEffect(() => {
    api.get('/api/admin/courses')
      .then(setCourses)
      .catch((err) => setErrorMessage(err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (location.state?.success) {
      navigate(location.pathname, { replace: true, state: {} });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function confirmDelete() {
    const course = pendingDelete;
    setPendingDelete(null);
    try {
      await api.del(`/api/admin/courses/${course.id}`);
      setCourses((prev) => prev.filter((c) => c.id !== course.id));
    } catch (err) {
      setErrorMessage(err.message);
    }
  }

  return (
    <div className="container-wide">
      <Alert success={successMessage} error={errorMessage} />

      <div className="page-head">
        <h1>Courses</h1>
        <div className="row-actions">
          <Link className="btn btn-primary" to="/admin/courses/new">New course</Link>
        </div>
      </div>

      {!loading && courses.length === 0 && (
        <div className="empty-state">
          <p>No courses yet.</p>
          <Link className="btn btn-primary" to="/admin/courses/new">Create your first course</Link>
        </div>
      )}

      {courses.length > 0 && (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th></th>
                <th>Title</th>
                <th>Status</th>
                <th>Modules</th>
                <th>Lessons</th>
                <th>Views</th>
                <th>Created</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {courses.map((course) => (
                <tr key={course.id}>
                  <td>
                    {course.thumbnailUrl
                      ? <img className="table-thumb" src={`${API_BASE}${course.thumbnailUrl}`} alt="" />
                      : <span className="table-thumb table-thumb-empty" />}
                  </td>
                  <td>{course.title}</td>
                  <td><span className={`badge status-${course.status.toLowerCase()}`}>{course.status === 'DRAFT' ? 'Draft' : 'Published'}</span></td>
                  <td>{course.moduleCount}</td>
                  <td>{course.lessonCount}</td>
                  <td>{course.viewCount}</td>
                  <td>{formatDate(course.createdAt)}</td>
                  <td className="row-actions">
                    <Link className="btn" to={`/admin/courses/${course.id}/edit`}>Edit</Link>
                    <Link className="btn" to={`/admin/courses/${course.id}/questions`}>Questions</Link>
                    <Link className="btn" to={`/courses/${course.id}`}>Preview</Link>
                    <button type="button" className="btn btn-danger-outline" onClick={() => setPendingDelete(course)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title={pendingDelete?.title}
        detail="Its modules, lessons and every uploaded video will be removed too."
        onCancel={() => setPendingDelete(null)}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
