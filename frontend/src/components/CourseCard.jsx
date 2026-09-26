import { Link } from 'react-router-dom';
import Icon from './Icon';
import PriceTag from './PriceTag';
import { mediaUrl } from '../lib/media';

function Cover({ course }) {
  return (
    <div className={`lc-cover${course.thumbnailUrl ? ' has-image' : ''}`}>
      {course.thumbnailUrl ? <img src={mediaUrl(course.thumbnailUrl)} alt="" loading="lazy" /> : <Icon name="play" size={34} strokeWidth={1.6} />}
    </div>
  );
}

/** A catalog card: cover, title, instructor, quick facts, price and where it leads. */
export function CatalogCard({ course, admin }) {
  const lessons = `${course.lessonCount} lesson${course.lessonCount === 1 ? '' : 's'}`;
  return (
    <Link to={`/courses/${course.id}`} className="lc-card">
      <Cover course={course} />
      <div className="lc-body">
        <h3 className="lc-title">{course.title}</h3>
        {course.instructorName && <p className="lc-instructor">{course.instructorName}</p>}
        <div className="lc-pills">
          {course.enrollmentCount >= 3 && <span className="pill pill-hot"><Icon name="flame" size={12} /> Popular</span>}
          <span className="pill">Course</span>
          <span className="pill">{lessons}</span>
        </div>
        <div className="lc-foot">
          <PriceTag pricing={course.pricing} compact />
          <span className={`btn ${course.enrolled ? 'btn-primary' : ''} btn-sm lc-cta`}>
            {admin ? 'Preview' : course.enrolled ? 'Continue' : 'View course'}
          </span>
        </div>
      </div>
    </Link>
  );
}

/** A "My learning" card: cover, title, instructor and how far along the learner is. */
export function LearningCard({ item }) {
  const { course, resumeLessonId, certificate } = item;
  const done = course.progressPercent === 100;
  const to = resumeLessonId ? `/courses/${course.id}/lessons/${resumeLessonId}` : `/courses/${course.id}`;
  return (
    <Link to={to} className="lc-card lc-learning">
      <Cover course={course} />
      <div className="lc-body">
        <h3 className="lc-title">{course.title}</h3>
        {course.instructorName && <p className="lc-instructor">{course.instructorName}</p>}
        <div className="lc-progress">
          <div className="lc-progress-bar"><div style={{ width: `${course.progressPercent}%` }} /></div>
          <div className="lc-progress-row">
            <span>{course.progressPercent}% complete</span>
            <span className="lc-progress-cta">{certificate ? 'Certified' : done ? 'Review' : course.progressPercent === 0 ? 'Start course' : 'Continue'}</span>
          </div>
        </div>
      </div>
    </Link>
  );
}
