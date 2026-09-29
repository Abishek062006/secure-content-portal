import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api';

/** Stripe redirects here after checkout: /payment/success?session_id=...&course_id=...
 *  The backend asks Stripe directly whether the session was actually paid before enrolling anyone —
 *  this page never trusts the redirect alone. */
export default function PaymentSuccess() {
  const { search } = useLocation();
  const navigate = useNavigate();
  const [status, setStatus] = useState('verifying'); // verifying | success | error
  const [message, setMessage] = useState('');
  const [courseId, setCourseId] = useState(null);
  const [courseTitle, setCourseTitle] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(search);
    const sessionId = params.get('session_id');
    const fallbackCourseId = params.get('course_id');
    if (fallbackCourseId) setCourseId(fallbackCourseId);

    if (!sessionId) {
      setStatus('error');
      setMessage('No payment session was found in the link.');
      return;
    }
    api.get(`/api/payments/stripe/confirm?session_id=${encodeURIComponent(sessionId)}`)
      .then((res) => {
        setCourseId(res.courseId || fallbackCourseId);
        setCourseTitle(res.courseTitle || '');
        setStatus('success');
        setTimeout(() => navigate(`/courses/${res.courseId || fallbackCourseId}`), 2500);
      })
      .catch((err) => {
        setStatus('error');
        setMessage(err.message);
      });
  }, [search, navigate]);

  return (
    <div className="container payment-success">
      {status === 'verifying' && (
        <div className="payment-status">
          <h1>Confirming your payment</h1>
          <p className="field-hint">Checking with Stripe — this only takes a moment.</p>
        </div>
      )}

      {status === 'success' && (
        <div className="payment-status">
          <h1>You're enrolled</h1>
          {courseTitle && <p className="payment-course">{courseTitle}</p>}
          <p className="field-hint">Taking you to the course…</p>
          {courseId && <Link className="btn btn-primary" to={`/courses/${courseId}`}>Go now</Link>}
        </div>
      )}

      {status === 'error' && (
        <div className="payment-status">
          <h1>Payment not confirmed</h1>
          <p className="field-hint">{message}</p>
          <div className="form-actions">
            {courseId && <Link className="btn btn-primary" to={`/courses/${courseId}`}>Back to course</Link>}
            <Link className="btn" to="/my-learning">My learning</Link>
          </div>
        </div>
      )}
    </div>
  );
}
