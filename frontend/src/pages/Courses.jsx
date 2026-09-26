import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { API_BASE, api } from '../api';
import PriceTag from '../components/PriceTag';
import { VideoIcon } from '../components/Icons';

function plural(n, word) {
  return `${n} ${word}${n === 1 ? '' : 's'}`;
}

export default function Courses() {
  const [courses, setCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => {
    api.get('/api/courses')
      .then(setCourses)
      .catch(() => setCourses([]))
      .finally(() => setLoading(false));
  }, []);

  const visible = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return courses;
    return courses.filter((c) =>
      [c.title, c.description, c.category].some((value) => (value || '').toLowerCase().includes(query)));
  }, [courses, search]);

  return (
    <div className="container-wide">
      <div className="page-head">
        <h1>Courses</h1>
      </div>

      <form className="search-bar" onSubmit={(e) => e.preventDefault()}>
        <input
          type="search"
          placeholder="Search courses…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </form>

      {!loading && visible.length === 0 && (
        <div className="empty-state">
          <p>{search ? 'No courses match your search.' : 'No courses have been published yet.'}</p>
        </div>
      )}

      {visible.length > 0 && (
        <div className="content-grid">
          {visible.map((course) => (
            <Link className="content-card" key={course.id} to={`/courses/${course.id}`}>
              <div className={`content-card-thumb${course.thumbnailUrl ? ' has-image' : ''}`}>
                {course.thumbnailUrl ? (
                  <img src={`${API_BASE}${course.thumbnailUrl}`} alt="" loading="lazy" />
                ) : (
                  <>
                    <VideoIcon />
                    <span>Course</span>
                  </>
                )}
              </div>
              <div className="content-card-body">
                <h2>{course.title}</h2>
                {course.description && <p>{course.description}</p>}
                <PriceTag pricing={course.pricing} compact />
                <div className="content-card-meta">
                  {course.category && <span className="badge">{course.category}</span>}
                  <span>{plural(course.moduleCount, 'module')} · {plural(course.lessonCount, 'lesson')}</span>
                </div>
                {course.enrolled && (
                  <div className="card-progress" title={`${course.progressPercent}% complete`}>
                    <div className="card-progress-bar" style={{ width: `${course.progressPercent}%` }} />
                  </div>
                )}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
