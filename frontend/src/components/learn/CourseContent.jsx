import { useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../Icon';
import { MATERIAL_ICON, MATERIAL_LABEL } from '../../lib/materials';

const ASSESSMENT_CHIP = { PASSED: 'Passed', IN_PROGRESS: 'Resume', EXHAUSTED: 'View' };

function assessmentChip(a) {
  if (a.status === 'AVAILABLE') return a.attemptsUsed > 0 ? 'Retake' : 'Start';
  return ASSESSMENT_CHIP[a.status] || '';
}

function Collapse({ open, children }) {
  return (
    <div className={`ln-collapse${open ? ' open' : ''}`} aria-hidden={!open}>
      <div className="ln-collapse-inner">{children}</div>
    </div>
  );
}

function AssessmentItem({ courseId, assessment, canOpen }) {
  const locked = assessment.status === 'LOCKED';
  const chip = assessmentChip(assessment);
  const body = (
    <>
      <span className={`ln-item-icon${assessment.passed ? ' passed' : ''}`}>
        <Icon name={locked ? 'lock' : assessment.passed ? 'check' : 'clipboard-check'} size={15} />
      </span>
      <span className="ln-item-text">
        <span className="ln-item-title">{assessment.title}</span>
        <span className="ln-item-sub">
          {assessment.type === 'QUIZ' ? 'Quiz' : 'Assessment'} · {assessment.questionCount} question{assessment.questionCount === 1 ? '' : 's'}
          {assessment.bestScore != null && ` · best ${assessment.bestScore}%`}
        </span>
      </span>
      {chip && !locked && <span className="ln-chip">{chip}</span>}
    </>
  );
  if (canOpen && !locked) {
    return <Link className="ln-item" to={`/courses/${courseId}/assessments/${assessment.id}`}>{body}</Link>;
  }
  return <div className="ln-item disabled" title={assessment.lockedReason || undefined}>{body}</div>;
}

/**
 * The right-hand "Course content" panel: sections that fold open, each lesson with its own tick, the module's resources and
 * quiz, and the course's final assessment. The section holding what you're watching opens by itself.
 */
export default function CourseContent({ courseId, outline, current, doneOverride = {}, isAdmin }) {
  const { modules, finalAssessment, enrolled } = outline;
  const canOpenAny = enrolled || isAdmin;

  const isDone = (lesson) => (lesson.id in doneOverride ? doneOverride[lesson.id] : Boolean(lesson.completed));
  const holdsCurrent = (module) =>
    module.lessons.some((l) => l.id === current.lessonId) || (module.materials || []).some((m) => m.id === current.materialId);

  const [openModules, setOpenModules] = useState(() => {
    const open = modules.filter(holdsCurrent).map((m) => m.id);
    return new Set(open.length ? open : modules.slice(0, 1).map((m) => m.id));
  });
  const [openResources, setOpenResources] = useState(
    () => new Set(modules.filter((m) => (m.materials || []).some((x) => x.id === current.materialId)).map((m) => m.id)),
  );

  const toggle = (setter) => (id) =>
    setter((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  const toggleModule = toggle(setOpenModules);
  const toggleResources = toggle(setOpenResources);

  const totalLessons = modules.reduce((n, m) => n + m.lessons.length, 0);
  const doneLessons = modules.reduce((n, m) => n + m.lessons.filter(isDone).length, 0);

  return (
    <nav className="ln-content" aria-label="Course content">
      <div className="ln-content-head">
        <h2>Course content</h2>
        <p>
          {modules.length} section{modules.length === 1 ? '' : 's'} · {totalLessons} lesson{totalLessons === 1 ? '' : 's'}
          {enrolled && !isAdmin && ` · ${doneLessons} done`}
        </p>
      </div>

      {modules.map((module, index) => {
        const open = openModules.has(module.id);
        const canOpen = canOpenAny && !module.locked;
        const done = module.lessons.filter(isDone).length;
        const materials = module.materials || [];
        return (
          <section className={`ln-section${open ? ' open' : ''}`} key={module.id}>
            <button type="button" className="ln-section-head" onClick={() => toggleModule(module.id)} aria-expanded={open}>
              <span className="ln-section-text">
                <span className="ln-section-title">Section {index + 1}: {module.title}</span>
                <span className="ln-section-meta">
                  {module.locked ? (
                    <><Icon name="lock" size={12} /> Locked</>
                  ) : enrolled && !isAdmin ? (
                    `${done} / ${module.lessons.length} lessons`
                  ) : (
                    `${module.lessons.length} lesson${module.lessons.length === 1 ? '' : 's'}`
                  )}
                </span>
              </span>
              <Icon name="chevron-down" size={18} className="ln-chevron" />
            </button>

            <Collapse open={open}>
              {module.locked && module.lockedReason && <p className="ln-locked-note">{module.lockedReason}</p>}
              <ol className="ln-items">
                {module.lessons.map((lesson) => {
                  const active = lesson.id === current.lessonId;
                  const completed = isDone(lesson);
                  const inner = (
                    <>
                      <span className={`ln-check${completed ? ' done' : ''}`} aria-hidden="true">
                        {completed && <Icon name="check" size={12} strokeWidth={3} />}
                      </span>
                      <span className="ln-item-text">
                        <span className="ln-item-title">{lesson.title}</span>
                        <span className="ln-item-sub">
                          <Icon name="play" size={11} /> Video
                          {lesson.hasTranscript && <> · <Icon name="captions" size={12} /> Transcript</>}
                        </span>
                      </span>
                    </>
                  );
                  return (
                    <li key={lesson.id}>
                      {canOpen ? (
                        <Link className={`ln-item${active ? ' active' : ''}`} to={`/courses/${courseId}/lessons/${lesson.id}`} aria-current={active ? 'page' : undefined}>
                          {inner}
                        </Link>
                      ) : (
                        <div className="ln-item disabled">{inner}</div>
                      )}
                    </li>
                  );
                })}

                {materials.length > 0 && (
                  <li>
                    <button type="button" className="ln-item ln-resources-toggle" onClick={() => toggleResources(module.id)} aria-expanded={openResources.has(module.id)}>
                      <span className="ln-item-icon"><Icon name="folder" size={15} /></span>
                      <span className="ln-item-text">
                        <span className="ln-item-title">Resources</span>
                        <span className="ln-item-sub">{materials.length} file{materials.length === 1 ? '' : 's'} and links</span>
                      </span>
                      <Icon name="chevron-down" size={16} className={`ln-chevron${openResources.has(module.id) ? ' open' : ''}`} />
                    </button>
                    <Collapse open={openResources.has(module.id)}>
                      <ul className="ln-resources">
                        {materials.map((m) => {
                          const active = m.id === current.materialId;
                          const body = (
                            <>
                              <Icon name={MATERIAL_ICON[m.kind]} size={15} />
                              <span className="ln-item-title">{m.title}</span>
                              <span className="ln-item-sub">{MATERIAL_LABEL[m.kind]}</span>
                            </>
                          );
                          return (
                            <li key={m.id}>
                              {canOpen ? (
                                <Link className={`ln-resource${active ? ' active' : ''}`} to={`/courses/${courseId}/materials/${m.id}`}>{body}</Link>
                              ) : (
                                <span className="ln-resource disabled">{body}</span>
                              )}
                            </li>
                          );
                        })}
                      </ul>
                    </Collapse>
                  </li>
                )}

                {module.assessment && (
                  <li>
                    <AssessmentItem courseId={courseId} assessment={module.assessment} canOpen={enrolled && !isAdmin} />
                  </li>
                )}
              </ol>
            </Collapse>
          </section>
        );
      })}

      {finalAssessment && (
        <section className="ln-section open final">
          <div className="ln-section-head static">
            <span className="ln-section-text">
              <span className="ln-section-title">Final assessment</span>
              <span className="ln-section-meta">Earns your certificate</span>
            </span>
          </div>
          <ol className="ln-items">
            <li><AssessmentItem courseId={courseId} assessment={finalAssessment} canOpen={enrolled && !isAdmin} /></li>
          </ol>
        </section>
      )}
    </nav>
  );
}
