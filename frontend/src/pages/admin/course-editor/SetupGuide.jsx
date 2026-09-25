import { Link } from 'react-router-dom';

/** A live checklist so it's obvious where videos, transcripts and quiz questions come from. */
export default function SetupGuide({ id, modules, finalAssessment, published }) {
  const lessons = modules.flatMap((m) => m.lessons);
  const withTranscript = lessons.filter((l) => l.hasTranscript).length;
  const hasAssessment = Boolean(finalAssessment) || modules.some((m) => m.assessment);

  const steps = [
    {
      done: modules.length > 0,
      title: 'Add a module',
      text: 'Use "Add module" at the bottom of the Outline. A module is a chapter, like "Week 1".',
    },
    {
      done: lessons.length > 0,
      title: 'Add lessons with a video and a transcript',
      text: lessons.length > 0
        ? `${lessons.length} lesson${lessons.length === 1 ? '' : 's'}, ${withTranscript} with a transcript. `
          + 'Each module has a "+ Add lesson" button; the form asks for the video and a .vtt transcript.'
        : 'Inside a module, click "+ Add lesson". The form asks for the video (.mp4/.webm) and the transcript (.vtt).',
    },
    {
      done: false,
      title: 'Generate quiz questions from the transcripts',
      text: withTranscript === 0
        ? 'Needs at least one lesson with a transcript.'
        : 'Open the question bank and click Generate on a lesson: AI writes questions from that transcript for you to review.',
      link: withTranscript > 0 ? { to: `/admin/courses/${id}/questions`, label: 'Open question bank' } : null,
    },
    {
      done: hasAssessment,
      title: 'Choose a quiz or assessment per module',
      text: 'Each module has a quiz/assessment panel, and there is a final assessment at the bottom. Both draw from the approved questions.',
    },
    {
      done: published,
      title: 'Publish',
      text: 'The Publish button at the top makes the course visible to learners.',
    },
  ];

  return (
    <section className="setup-guide">
      <h2>How to set up this course</h2>
      <ol>
        {steps.map((step) => (
          <li key={step.title} className={step.done ? 'done' : ''}>
            <span className="step-mark">{step.done ? '✓' : ''}</span>
            <div>
              <strong>{step.title}</strong>
              <p>{step.text} {step.link && <Link to={step.link.to}>{step.link.label}</Link>}</p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
