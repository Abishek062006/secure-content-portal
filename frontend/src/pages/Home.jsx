import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';

const LESSONS = [
  { title: 'Introduction', state: 'done' },
  { title: 'Variables and types', state: 'done' },
  { title: 'Lists', state: 'now' },
  { title: 'Loops', state: 'todo' },
  { title: 'Module quiz', state: 'todo' },
];

function AppWindow() {
  return (
    <div className="ap-window" aria-hidden="true">
      <div className="ap-window-bar"><i /><i /><i /><span>Python in 30 Minutes</span></div>
      <div className="ap-window-body">
        <div className="ap-player">
          <div className="ap-play"><Icon name="play" size={30} strokeWidth={1.8} /></div>
          <div className="ap-timeline"><div style={{ width: '38%' }} /></div>
          <span className="ap-caption">3. Lists</span>
        </div>
        <ul className="ap-lessons">
          {LESSONS.map((l) => (
            <li key={l.title} className={l.state}>
              <span className="ap-dot">{l.state === 'done' && <Icon name="check" size={11} strokeWidth={3} />}</span>
              {l.title}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

function TranscriptToQuestion() {
  return (
    <div className="ap-pair" aria-hidden="true">
      <div className="ap-sheet ap-transcript">
        <span className="ap-sheet-label">Transcript</span>
        <p><b>5:52</b> Strings are arrays of characters, so you can index into them.</p>
        <p className="hl"><b>6:08</b> If a is "hello world", a[1] is the second character.</p>
        <p><b>6:21</b> Indexing starts at zero, not one.</p>
      </div>
      <div className="ap-arrow"><Icon name="arrow-right" size={22} strokeWidth={1.6} /></div>
      <div className="ap-sheet ap-question">
        <span className="ap-sheet-label">Hard · from 6:08</span>
        <p className="q">Given a = "hello world", what does print(a[1]) show?</p>
        <div className="opt">o</div>
        <div className="opt on"><Icon name="check" size={14} strokeWidth={2.8} /> e</div>
        <div className="opt">l</div>
      </div>
    </div>
  );
}

function Certificate() {
  return (
    <div className="ap-cert" aria-hidden="true">
      <span className="ap-cert-brand">GRADIENTNOVAAI</span>
      <h4>Certificate of Completion</h4>
      <span className="ap-cert-small">This certifies that</span>
      <strong>Abishek R S</strong>
      <span className="ap-cert-small">has completed</span>
      <em>Python in 30 Minutes</em>
      <div className="ap-cert-id">Credential ID · ABCD-EFGH-JKLM</div>
    </div>
  );
}

function FeedPost() {
  return (
    <div className="ap-post" aria-hidden="true">
      <div className="ap-post-head">
        <span className="ap-avatar">A</span>
        <div><strong>Abishek R S</strong><span>Student · 2h</span></div>
      </div>
      <p>Just finished Python in 30 Minutes. On to the next one.</p>
      <div className="ap-post-cert"><b>Python in 30 Minutes</b><span>Credential ID ABCD-EFGH-JKLM</span></div>
      <div className="ap-reacts">
        <span className="stack">
          <img src="/reactions/like.svg" alt="" /><img src="/reactions/celebrate.svg" alt="" /><img src="/reactions/love.svg" alt="" />
        </span>
        <span>24 reactions</span>
      </div>
    </div>
  );
}

export default function Home() {
  const { user, loading } = useAuth();

  // Signed-in members land on the course catalog and admins on their dashboard; this page is for visitors.
  if (loading) return null;
  if (user) return <Navigate to={user.admin ? '/admin/dashboard' : '/courses'} replace />;

  return (
    <div className="ap">
      <section className="ap-hero">
        <h1>Learn from the lecture.<br />Prove it with a certificate.</h1>
        <p>GradientNovaAI turns recorded lectures into courses, with quizzes, assessments and certificates.</p>
        <div className="ap-actions">
          <Link className="ap-btn" to="/login">Get started</Link>
          <a className="ap-link" href="#how">How it works <Icon name="chevron-right" size={18} /></a>
        </div>
        <AppWindow />
      </section>

      <section className="ap-story" id="how">
        <div className="ap-story-text">
          <span className="ap-kicker">Practice</span>
          <h2>Questions that come from the lecture.</h2>
          <p>Upload a recording and its transcript. Ask for as many questions as you like, at the difficulty you choose, and approve the ones you want. Learners are quizzed on what was actually taught.</p>
        </div>
        <TranscriptToQuestion />
      </section>

      <section className="ap-story flip">
        <div className="ap-story-text">
          <span className="ap-kicker">Proof</span>
          <h2>Finish. Get certified.</h2>
          <p>Pass the assessments and claim a certificate with its own credential ID. Anyone can check it, and it sits on your profile for people to see.</p>
        </div>
        <Certificate />
      </section>

      <section className="ap-story">
        <div className="ap-story-text">
          <span className="ap-kicker">Community</span>
          <h2>Learn in good company.</h2>
          <p>Share updates and articles, react and comment, and post the certificates you earn. A feed for people who are learning the same things.</p>
        </div>
        <FeedPost />
      </section>

      <section className="ap-dark">
        <span className="ap-kicker light">For instructors</span>
        <h2>Upload a lecture.<br />Get a course.</h2>
        <p>Add a recording and its transcript, review the questions, set the pass mark and publish. Then follow how learners are doing on your dashboard.</p>
        <div className="ap-figures" aria-hidden="true">
          <div><strong>Drafted by AI</strong><span>approved by you</span></div>
          <div><strong>View or download</strong><span>your choice, per file</span></div>
          <div><strong>One dashboard</strong><span>for every course</span></div>
        </div>
      </section>

      <section className="ap-trust">
        <div><h3>Sign in with Google.</h3><p>No new password to remember or lose.</p></div>
        <div><h3>Content stays protected.</h3><p>Streamed with session-locked links and watermarked to the viewer.</p></div>
        <div><h3>Graded on the server.</h3><p>Questions, timers and scores never depend on the browser.</p></div>
      </section>

      <section className="ap-final">
        <h2>Start learning.</h2>
        <Link className="ap-btn" to="/login">Get started</Link>
      </section>

      <footer className="ap-footer">GradientNovaAI</footer>
    </div>
  );
}
