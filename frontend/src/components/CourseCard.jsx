import { useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from './Icon';
import PriceTag from './PriceTag';
import EnquiryModal from './EnquiryModal';
import { mediaUrl } from '../lib/media';

function Cover({ course }) {
  return (
    <div className={`lc-cover${course.thumbnailUrl ? ' has-image' : ''}`}>
      {course.thumbnailUrl ? <img src={mediaUrl(course.thumbnailUrl)} alt="" loading="lazy" /> : <Icon name="play" size={34} strokeWidth={1.6} />}
    </div>
  );
}

/** A catalog card: cover, title, instructor, quick facts, price — and, below that, its own action row.
 *  The action row sits outside the card's main link so "Enquire" opens right there instead of navigating
 *  away, and a REGISTER-type course reads "Register" instead of "View course" (both still just open the
 *  course page — that's where the actual request goes, since that's where the outcomes and details are). */
export function CatalogCard({ course, admin }) {
  const [showEnquiry, setShowEnquiry] = useState(false);
  const lessons = `${course.lessonCount} lesson${course.lessonCount === 1 ? '' : 's'}`;
  const ctaLabel = admin ? 'Preview' : course.enrolled ? 'Continue' : course.accessType === 'REGISTER' ? 'Register' : 'View course';

  return (
    <div className="lc-card">
      <Link to={`/courses/${course.id}`} className="lc-card-link">
        <Cover course={course} />
        <div className="lc-body">
          <h3 className="lc-title">{course.title}</h3>
          {course.instructorName && <p className="lc-instructor">{course.instructorName}</p>}
          <div className="lc-pills">
            {course.enrollmentCount >= 3 && <span className="pill pill-hot"><Icon name="flame" size={12} /> Popular</span>}
            <span className="pill">Course</span>
            <span className="pill">{lessons}</span>
          </div>
          <div className="lc-foot"><PriceTag pricing={course.pricing} accessType={course.accessType} compact /></div>
        </div>
      </Link>
      <div className="lc-actions">
        <Link to={`/courses/${course.id}`} className={`btn ${course.enrolled ? 'btn-primary' : ''} btn-sm`}>{ctaLabel}</Link>
        {!admin && <button type="button" className="btn btn-sm" onClick={() => setShowEnquiry(true)}>Enquire</button>}
      </div>
      {!admin && <EnquiryModal open={showEnquiry} courseId={course.id} courseTitle={course.title} onClose={() => setShowEnquiry(false)} />}
    </div>
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
