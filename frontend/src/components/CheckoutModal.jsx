import { useEffect, useState } from 'react';
import { api } from '../api';
import Modal from './Modal';

const rupees = new Intl.NumberFormat('en-IN');

const PROVIDER_LABEL = { STRIPE: 'Card (Stripe)', RAZORPAY: 'Card / UPI (Razorpay)', SIMULATED: 'Test payment (dev only)' };

function loadRazorpayScript() {
  return new Promise((resolve) => {
    if (window.Razorpay) { resolve(true); return; }
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

/** Checkout for a paid course: picks whichever gateways the backend actually has keys for (plus a
 *  simulated one-click path when that's explicitly turned on for local testing), creates the order
 *  server-side — the price always comes from the course's own pricing, never from this page — and hands
 *  off to the gateway. */
export default function CheckoutModal({ open, course, onClose, onSuccess }) {
  const [config, setConfig] = useState(null);
  const [provider, setProvider] = useState(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!open) return;
    setError(null);
    setDone(false);
    setBusy(false);
    api.get('/api/payments/config')
      .then((res) => { setConfig(res); setProvider(res.availableProviders[0] || null); })
      .catch((err) => setError(err.message));
  }, [open]);

  async function pay() {
    if (!provider) return;
    setBusy(true);
    setError(null);
    try {
      const order = await api.post('/api/payments/create-order', { courseId: course.id, provider });

      if (provider === 'STRIPE') {
        if (order.checkoutUrl) window.location.href = order.checkoutUrl;
        return;
      }

      if (provider === 'RAZORPAY') {
        const loaded = await loadRazorpayScript();
        if (!loaded) throw new Error('Could not load the Razorpay checkout — check your connection and try again.');
        const rzp = new window.Razorpay({
          key: order.keyId,
          amount: order.amountRupees * 100,
          currency: order.currency,
          name: 'GradientNovaAI',
          description: order.courseTitle,
          order_id: order.orderId,
          handler: async (response) => {
            try {
              await api.post('/api/payments/razorpay/verify', {
                paymentOrderId: order.paymentOrderId,
                razorpayOrderId: response.razorpay_order_id,
                razorpayPaymentId: response.razorpay_payment_id,
                razorpaySignature: response.razorpay_signature,
              });
              setDone(true);
              setTimeout(() => { onSuccess(); onClose(); }, 1200);
            } catch (err) {
              setError(err.message);
            } finally {
              setBusy(false);
            }
          },
          modal: { ondismiss: () => setBusy(false) },
        });
        rzp.open();
        return;
      }

      // SIMULATED — one click, no real gateway involved.
      await api.post('/api/payments/simulated/complete', { paymentOrderId: order.paymentOrderId });
      setDone(true);
      setTimeout(() => { onSuccess(); onClose(); }, 1000);
    } catch (err) {
      setError(err.message);
    } finally {
      if (provider !== 'RAZORPAY') setBusy(false);
    }
  }

  if (!course) return null;
  const finalPrice = course.pricing?.finalPriceRupees ?? course.pricing?.priceRupees ?? 0;

  return (
    <Modal open={open} title="Checkout" onClose={busy ? () => {} : onClose}>
      {done ? (
        <div className="checkout-done">
          <p>Payment received — you're enrolled.</p>
        </div>
      ) : (
        <div className="checkout-body">
          {error && <p className="form-error">{error}</p>}

          <div className="checkout-summary">
            <strong>{course.title}</strong>
            <span className="checkout-amount">₹{rupees.format(finalPrice)}</span>
          </div>

          {!config ? (
            <p className="field-hint">Loading payment options…</p>
          ) : config.availableProviders.length === 0 ? (
            <p className="field-hint">No payment method is available right now — please contact an admin.</p>
          ) : (
            <>
              <div className="field">
                <label>Pay with</label>
                <div className="type-choice">
                  {config.availableProviders.map((p) => (
                    <label key={p}>
                      <input type="radio" name="provider" checked={provider === p} onChange={() => setProvider(p)} />
                      {PROVIDER_LABEL[p] || p}
                    </label>
                  ))}
                </div>
              </div>
              <div className="form-actions">
                <button type="button" className="btn btn-primary btn-lg" onClick={pay} disabled={busy || !provider}>
                  {busy ? 'Processing…' : `Pay ₹${rupees.format(finalPrice)}`}
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </Modal>
  );
}
