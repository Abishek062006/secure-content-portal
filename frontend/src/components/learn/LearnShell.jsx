import { useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../Icon';
import { Logo } from '../Icons';
import ProgressRing from './ProgressRing';
import CourseContent from './CourseContent';
import '../../styles/learn.css';

function readCollapsed() {
  try {
    return localStorage.getItem('ln-side') === 'closed';
  } catch {
    return false;
  }
}

/**
 * The focused player layout shared by lessons and materials: a slim frosted bar (back, course title, your progress),
 * the content on the left, and the course outline on the right that stays put while you scroll.
 */
export default function LearnShell({ courseId, outline, current, doneOverride, progressPercent, isAdmin, children }) {
  const [collapsed, setCollapsed] = useState(readCollapsed);

  function toggleSide() {
    setCollapsed((previous) => {
      const next = !previous;
      try {
        localStorage.setItem('ln-side', next ? 'closed' : 'open');
      } catch {
        // Remembering the choice is a nicety.
      }
      return next;
    });
  }

  const percent = progressPercent ?? outline?.progressPercent ?? 0;
  const showProgress = outline?.enrolled && !isAdmin;

  return (
    <div className={`ln${collapsed ? ' side-closed' : ''}`}>
      <header className="ln-bar">
        <Link className="ln-icon-btn" to={`/courses/${courseId}`} aria-label="Back to the course" title="Back to the course">
          <Icon name="arrow-left" size={19} />
        </Link>
        <Link className="ln-brand" to="/courses" aria-label="GradientNovaAI home"><Logo size={26} /></Link>
        <span className="ln-divider" aria-hidden="true" />
        <span className="ln-course-title">{outline?.course.title || ''}</span>

        <div className="ln-bar-right">
          {isAdmin && <span className="ln-pill">Admin preview</span>}
          {showProgress && (
            percent >= 100 ? (
              <Link className="ln-pill accent" to={`/courses/${courseId}`}>
                <Icon name="award" size={16} /> Get your certificate
              </Link>
            ) : (
              <span className="ln-pill" title="Your progress in this course">
                <ProgressRing percent={percent} size={22} stroke={3} />
                <span>{percent}%</span>
              </span>
            )
          )}
          <button type="button" className="ln-icon-btn side-toggle" onClick={toggleSide} aria-pressed={!collapsed}
                  aria-label={collapsed ? 'Show course content' : 'Hide course content'} title={collapsed ? 'Show course content' : 'Hide course content'}>
            <Icon name="panel-right" size={19} />
          </button>
        </div>
      </header>

      <div className="ln-body">
        <main className="ln-main">{children}</main>
        <aside className="ln-side">
          {outline ? (
            <CourseContent courseId={courseId} outline={outline} current={current} doneOverride={doneOverride} isAdmin={isAdmin} />
          ) : (
            <div className="ln-skeleton" aria-hidden="true"><i /><i /><i /><i /><i /></div>
          )}
        </aside>
      </div>
    </div>
  );
}
