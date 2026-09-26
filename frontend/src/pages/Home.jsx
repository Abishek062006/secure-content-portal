import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import { Logo } from '../components/Icons';

const FEATURES = [
  { icon: 'video', title: 'Video courses, organised', body: 'Lessons grouped into modules, with transcripts you can read along with and a resume point that remembers where you stopped.' },
  { icon: 'sparkles', title: 'Quizzes written from the lecture', body: 'Questions are drawn from the transcript itself, at the difficulty your instructor chooses, so practice matches what was taught.' },
  { icon: 'timer', title: 'Timed assessments', body: 'Pass marks, time limits and attempt limits, graded on the server, so a result means something.' },
  { icon: 'award', title: 'Certificates you can verify', body: 'Finish a course and earn a certificate with a credential ID anyone can check, then share it on your profile.' },
  { icon: 'message-circle', title: 'A community feed', body: 'Post updates and articles, react, comment, and follow what other learners are building.' },
  { icon: 'shield-check', title: 'Protected by design', body: 'Content is streamed with session-locked links and watermarked, and your role is decided on the server, never in the browser.' },
];

const STEPS = [
  { n: '1', title: 'Sign in with Google', body: 'No new password. One click and your profile is ready.' },
  { n: '2', title: 'Enroll and learn', body: 'Watch lessons, open the materials, and take the quizzes as you go.' },
  { n: '3', title: 'Get certified', body: 'Pass the assessments, claim your certificate, and show it off.' },
];

function HeroPreview() {
  return (
    <div className="land-preview" aria-hidden="true">
      <div className="lp-card lp-course">
        <div className="lp-cover"><Icon name="play" size={30} strokeWidth={1.6} /></div>
        <div className="lp-body">
          <strong>Python in 30 Minutes</strong>
          <span>Module 2 · Lists and loops</span>
          <div className="lp-bar"><div style={{ width: '64%' }} /></div>
          <div className="lp-row"><span>64% complete</span><em>Continue</em></div>
        </div>
      </div>
      <div className="lp-card lp-quiz">
        <span className="lp-tag"><Icon name="sparkles" size={13} /> From the transcript</span>
        <p>Which symbol starts a comment in Python?</p>
        <div className="lp-option">// double slash</div>
        <div className="lp-option on"><Icon name="check" size={14} strokeWidth={2.6} /> # hash</div>
      </div>
      <div className="lp-card lp-cert">
        <div className="lp-cert-icon"><Icon name="award" size={22} /></div>
        <div><strong>Certificate earned</strong><span>Credential ID ABCD-EFGH-JKLM</span></div>
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
    <div className="land">
      <section className="land-hero">
        <div className="land-hero-copy">
          <span className="land-eyebrow"><Logo size={20} /> GradientNovaAI</span>
          <h1>Learn it. Prove it.<br /><span className="grad">Share it.</span></h1>
          <p>Video courses with quizzes generated from the lectures themselves, timed assessments, certificates you can verify, and a community to learn alongside.</p>
          <div className="land-cta">
            <Link className="btn btn-primary btn-lg" to="/login">Get started <Icon name="arrow-right" size={18} /></Link>
            <a className="btn btn-lg" href="#how">How it works</a>
          </div>
          <p className="land-note">Free to join with your Google account.</p>
        </div>
        <HeroPreview />
      </section>

      <section className="land-section" id="features">
        <h2>Everything you need to actually finish a course</h2>
        <p className="land-lead">Built around how people really learn: watch, practise, get tested, and be recognised.</p>
        <div className="land-grid">
          {FEATURES.map((f) => (
            <article className="land-feature" key={f.title}>
              <div className="land-icon"><Icon name={f.icon} size={22} /></div>
              <h3>{f.title}</h3>
              <p>{f.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="land-section land-how" id="how">
        <h2>How it works</h2>
        <div className="land-steps">
          {STEPS.map((s) => (
            <div className="land-step" key={s.n}>
              <span className="land-step-n">{s.n}</span>
              <h3>{s.title}</h3>
              <p>{s.body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="land-teach">
        <div>
          <span className="land-eyebrow dark"><Icon name="upload" size={16} /> For instructors</span>
          <h2>Upload a lecture. Get a course.</h2>
          <p>Drop in a recording and its transcript. Generate quiz questions in the difficulty you want, review them, set the pass mark, and publish. Then watch learners progress on your dashboard.</p>
          <ul>
            <li><Icon name="check" size={16} strokeWidth={2.6} /> Questions drafted by AI, approved by you</li>
            <li><Icon name="check" size={16} strokeWidth={2.6} /> Materials with view-only or download control</li>
            <li><Icon name="check" size={16} strokeWidth={2.6} /> Completion, quiz and engagement analytics</li>
          </ul>
        </div>
        <div className="land-teach-card" aria-hidden="true">
          <span className="lt-example">Example dashboard</span>
          <div className="lt-row"><span>Learners</span><strong>1,248</strong></div>
          <div className="lt-row"><span>Completion rate</span><strong>68%</strong></div>
          <div className="lt-bars"><i style={{ height: '38%' }} /><i style={{ height: '52%' }} /><i style={{ height: '46%' }} /><i style={{ height: '70%' }} /><i style={{ height: '64%' }} /><i style={{ height: '88%' }} /></div>
        </div>
      </section>

      <section className="land-final">
        <h2>Ready to start learning?</h2>
        <p>Sign in with Google and pick your first course.</p>
        <Link className="btn btn-primary btn-lg" to="/login">Get started <Icon name="arrow-right" size={18} /></Link>
      </section>

      <footer className="land-footer">
        <span><Logo size={18} /> GradientNovaAI</span>
        <span>Learn, practise and share your progress.</span>
      </footer>
    </div>
  );
}
