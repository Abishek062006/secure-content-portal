import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import Alert from '../../components/Alert';
import { AreaChart, Ring } from '../../components/charts';

const number = new Intl.NumberFormat('en-IN');
const percent = (part, whole) => (whole > 0 ? Math.round((part / whole) * 100) : 0);

const SERIES = [
  { key: 'enrollments', label: 'Enrollments' },
  { key: 'newLearners', label: 'New learners' },
  { key: 'attempts', label: 'Quiz attempts' },
  { key: 'posts', label: 'Posts' },
];

const REACTION_LABEL = { LIKE: 'Like', CELEBRATE: 'Celebrate', SUPPORT: 'Support', LOVE: 'Love', INSIGHTFUL: 'Insightful', FUNNY: 'Funny' };

const ACTION_LABEL = {
  COURSE_CREATE: 'created a course', COURSE_PUBLISH: 'published a course', COURSE_UNPUBLISH: 'unpublished a course',
  COURSE_DELETE: 'deleted a course', COURSE_PRICING: 'changed a price', QUESTIONS_GENERATE: 'generated questions',
  QUESTION_ADD: 'added a question', QUESTIONS_IMPORT: 'imported questions', QUESTION_APPROVE: 'approved a question',
  ASSESSMENT_SAVE: 'saved a quiz', MATERIAL_ADD: 'added material', MATERIAL_DELETE: 'removed material',
};

function ago(iso) {
  if (!iso) return '';
  const seconds = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}

function Metric({ label, value, note }) {
  return (
    <div className="an-card an-metric">
      <span className="an-label">{label}</span>
      <strong className="an-number">{value}</strong>
      <span className="an-note">{note}</span>
    </div>
  );
}

export default function Analytics() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [series, setSeries] = useState('enrollments');

  useEffect(() => {
    api.get('/api/admin/analytics').then(setData).catch((e) => setError(e.message));
  }, []);

  if (!data) return <div className="container"><Alert error={error} />{!error && <p className="muted">Loading analytics…</p>}</div>;

  const { totals, funnel, quiz, content, community, topCourses, recent } = data;
  const steps = [
    { label: 'Enrolled', value: funnel.enrolled },
    { label: 'Started', value: funnel.started },
    { label: 'Finished all lessons', value: funnel.completed },
    { label: 'Certified', value: funnel.certified },
  ];
  const topReaction = Math.max(1, ...Object.values(community.reactionsByType));
  const maxEnrollments = Math.max(1, ...topCourses.map((c) => c.enrollments));

  return (
    <div className="an-page">
      <header className="an-head">
        <div>
          <h1>Analytics</h1>
          <p>How learners are using the platform, over the last 30 days.</p>
        </div>
      </header>

      <div className="an-metrics">
        <Metric label="Learners" value={number.format(totals.learners)} note={`+${totals.newLearners30} this month · ${totals.activeLearners7} active this week`} />
        <Metric label="Enrollments" value={number.format(totals.enrollments)} note={`+${totals.enrollments30} this month`} />
        <Metric label="Completion rate" value={`${percent(funnel.completed, funnel.enrolled)}%`} note={`${funnel.completed} of ${funnel.enrolled} enrollments finished`} />
        <Metric label="Certificates" value={number.format(totals.certificates)} note={`+${totals.certificates30} this month`} />
      </div>

      <div className="an-row an-row-wide">
        <section className="an-card">
          <div className="an-card-head">
            <h2>Activity</h2>
            <div className="segmented" role="tablist">
              {SERIES.map((s) => (
                <button key={s.key} type="button" role="tab" aria-selected={series === s.key} className={series === s.key ? 'active' : ''} onClick={() => setSeries(s.key)}>{s.label}</button>
              ))}
            </div>
          </div>
          <AreaChart labels={data.series.dates} values={data.series[series]} />
        </section>

        <section className="an-card an-center">
          <h2>Quiz performance</h2>
          <Ring percent={quiz.passRate} label="pass rate" />
          <dl className="an-facts">
            <div><dt>Average score</dt><dd>{quiz.averageScore == null ? '—' : `${Math.round(quiz.averageScore)}%`}</dd></div>
            <div><dt>Attempts</dt><dd>{number.format(quiz.attempts)} <span>+{quiz.attempts30} this month</span></dd></div>
          </dl>
        </section>
      </div>

      <div className="an-row">
        <section className="an-card">
          <h2>Learner journey</h2>
          <ul className="an-funnel">
            {steps.map((step, i) => (
              <li key={step.label}>
                <div className="an-funnel-top"><span>{step.label}</span><strong>{number.format(step.value)}</strong></div>
                <div className="an-bar"><div style={{ width: `${percent(step.value, funnel.enrolled)}%`, opacity: 1 - i * 0.18 }} /></div>
                {i > 0 && <span className="an-funnel-pct">{percent(step.value, funnel.enrolled)}% of enrolled</span>}
              </li>
            ))}
          </ul>
        </section>

        <section className="an-card">
          <div className="an-card-head"><h2>Top courses</h2><Link to="/admin/courses" className="cat-link">Manage</Link></div>
          {topCourses.length === 0 ? <p className="muted">No published courses yet.</p> : (
            <ul className="an-courses">
              {topCourses.map((c) => (
                <li key={c.id}>
                  <div className="an-course-top">
                    <Link to={`/admin/courses/${c.id}/edit`}>{c.title}</Link>
                    <span>{number.format(c.enrollments)} enrolled</span>
                  </div>
                  <div className="an-bar"><div style={{ width: `${(c.enrollments / maxEnrollments) * 100}%` }} /></div>
                  <span className="an-course-sub">{c.category ? `${c.category} · ` : ''}{percent(c.completed, c.enrollments)}% completed · {c.certificates} certified</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <div className="an-row an-row-3">
        <section className="an-card">
          <h2>Content</h2>
          <dl className="an-list">
            <div><dt>Published courses</dt><dd>{totals.publishedCourses}</dd></div>
            <div><dt>Drafts</dt><dd>{totals.draftCourses}</dd></div>
            <div><dt>Lessons</dt><dd>{content.lessons}</dd></div>
            <div><dt>Materials</dt><dd>{content.materials}</dd></div>
            <div><dt>Questions</dt><dd>{content.approvedQuestions} <span>of {content.questions} approved</span></dd></div>
            <div><dt>AI-written</dt><dd>{content.aiQuestions}</dd></div>
          </dl>
        </section>

        <section className="an-card">
          <h2>Community</h2>
          <dl className="an-list">
            <div><dt>Posts</dt><dd>{community.posts} <span>+{community.posts30} this month</span></dd></div>
            <div><dt>Comments</dt><dd>{community.comments}</dd></div>
            <div><dt>Reactions</dt><dd>{community.reactions}</dd></div>
          </dl>
          <ul className="an-reactions">
            {Object.entries(community.reactionsByType).map(([type, count]) => (
              <li key={type}>
                <span>{REACTION_LABEL[type]}</span>
                <div className="an-bar thin"><div style={{ width: `${(count / topReaction) * 100}%` }} /></div>
                <em>{count}</em>
              </li>
            ))}
          </ul>
        </section>

        <section className="an-card">
          <div className="an-card-head"><h2>Recent activity</h2><Link to="/admin/audit" className="cat-link">All</Link></div>
          {recent.length === 0 ? <p className="muted">Nothing yet.</p> : (
            <ul className="an-activity">
              {recent.map((a, i) => (
                <li key={i}>
                  <span className="dot" />
                  <div>
                    <strong>{a.actor.split('@')[0]}</strong> {ACTION_LABEL[a.action] || a.action.toLowerCase().replaceAll('_', ' ')}
                    {a.detail && <span className="an-detail"> — {a.detail}</span>}
                    <div className="muted">{ago(a.at)}</div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
