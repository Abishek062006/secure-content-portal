import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Icon from '../components/Icon';
import { Logo } from '../components/Icons';

const POINTS = [
  { icon: 'video', title: 'Learn at your pace', text: 'Video lessons with transcripts, and a resume point that remembers you.' },
  { icon: 'sparkles', title: 'Practise what was taught', text: 'Quizzes drawn from the lectures, then timed assessments.' },
  { icon: 'award', title: 'Earn certificates', text: 'Credentials with an ID anyone can verify.' },
];

export default function Login() {
  const { login } = useAuth();
  const [searchParams] = useSearchParams();
  const error = searchParams.get('error');

  return (
    <div className="sign">
      <aside className="sign-side">
        <h2>Your learning, all in one place.</h2>
        <ul>
          {POINTS.map((p) => (
            <li key={p.title}>
              <span className="sign-point-icon"><Icon name={p.icon} size={20} /></span>
              <div><strong>{p.title}</strong><p>{p.text}</p></div>
            </li>
          ))}
        </ul>
      </aside>

      <main className="sign-main">
        <div className="sign-card">
          <Logo size={44} />
          <h1>Welcome</h1>
          <p className="sign-sub">Sign in or create your account with Google. It takes one click.</p>

          {error && <p className="field-error">{error}</p>}

          <button type="button" className="google-btn" onClick={login}>
            <svg width="18" height="18" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg">
              <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.9c1.7-1.57 2.7-3.88 2.7-6.62z" />
              <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.9-2.26c-.8.54-1.84.86-3.06.86-2.35 0-4.34-1.59-5.05-3.72H.9v2.33A9 9 0 0 0 9 18z" />
              <path fill="#FBBC05" d="M3.95 10.7A5.4 5.4 0 0 1 3.68 9c0-.59.1-1.16.27-1.7V4.97H.9A9 9 0 0 0 0 9c0 1.45.35 2.83.9 4.03l3.05-2.33z" />
              <path fill="#EA4335" d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A9 9 0 0 0 .9 4.97l3.05 2.33C4.66 5.17 6.65 3.58 9 3.58z" />
            </svg>
            Continue with Google
          </button>

          <p className="sign-fine"><Icon name="lock" size={14} /> We only use your Google name, email and photo. No password to remember.</p>
          <Link to="/" className="sign-back">← Back to home</Link>
        </div>
      </main>
    </div>
  );
}
