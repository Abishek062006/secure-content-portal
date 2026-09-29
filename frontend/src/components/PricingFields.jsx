import PriceTag from './PriceTag';

const rupees = new Intl.NumberFormat('en-IN');

/** "2026-09-30T18:00" (what a datetime-local input holds, in the admin's timezone) to an ISO instant, or ''. */
export function toIso(local) {
  return local ? new Date(local).toISOString() : '';
}

export function toLocalInput(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function pricingFromCourse(course) {
  const p = course?.pricing;
  return {
    type: course?.accessType === 'REGISTER' ? 'REGISTER' : p && !p.free ? 'PAID' : 'FREE',
    price: p && !p.free ? p.priceRupees : '',
    percent: p?.discountPercent ?? 0,
    start: toLocalInput(p?.discountStart),
    end: toLocalInput(p?.discountEnd),
  };
}

/** What the API expects for a course's price and access type (a REGISTER course sends no price at all —
 *  access is granted by an admin's decision, not a payment). */
export function pricingPayload(value) {
  const paid = value.type === 'PAID';
  const price = paid ? Number(value.price) || 0 : 0;
  const percent = paid && price > 0 ? Number(value.percent) || 0 : 0;
  return {
    priceRupees: price,
    discountPercent: percent,
    discountStart: percent > 0 ? toIso(value.start) || null : null,
    discountEnd: percent > 0 ? toIso(value.end) || null : null,
    accessType: value.type === 'REGISTER' ? 'REGISTER' : 'OPEN',
  };
}

/** Free, paid (with an optional discount), or register — where a learner asks and an admin decides who gets in. */
export default function PricingFields({ value, onChange, disabled }) {
  const set = (patch) => onChange({ ...value, ...patch });
  const price = Number(value.price) || 0;
  const percent = Number(value.percent) || 0;
  const preview = {
    free: value.type !== 'PAID' || price === 0,
    priceRupees: price,
    discountActive: value.type === 'PAID' && price > 0 && percent > 0,
    discountPercent: percent,
    finalPriceRupees: Math.round((price * (100 - percent)) / 100),
    discountEnd: value.end ? new Date(value.end).toISOString() : null,
  };

  return (
    <fieldset className="pricing-fields" disabled={disabled}>
      <legend>Access</legend>
      <div className="type-choice">
        <label><input type="radio" name="access-type" checked={value.type === 'FREE'} onChange={() => set({ type: 'FREE' })} /> Free</label>
        <label><input type="radio" name="access-type" checked={value.type === 'PAID'} onChange={() => set({ type: 'PAID' })} /> Paid</label>
        <label><input type="radio" name="access-type" checked={value.type === 'REGISTER'} onChange={() => set({ type: 'REGISTER' })} /> Register
          <span className="field-hint">(learners request access, you approve)</span></label>
      </div>

      {value.type === 'PAID' && (
        <>
          <div className="field">
            <label htmlFor="price">Price (₹)</label>
            <input id="price" type="number" min={1} max={1000000} step={1} value={value.price} required
                   placeholder="e.g. 1249" onChange={(e) => set({ price: e.target.value })} />
          </div>

          <div className="field">
            <label htmlFor="discount">
              Discount: <strong>{percent === 0 ? 'none' : `${percent}% off`}</strong>
              {percent > 0 && price > 0 && ` (learners pay ₹${rupees.format(preview.finalPriceRupees)} instead of ₹${rupees.format(price)})`}
            </label>
            <div className="count-row">
              <input id="discount" className="level-slider" type="range" min={0} max={100} step={1} value={percent}
                     onChange={(e) => set({ percent: Number(e.target.value) })} />
              <input type="number" min={0} max={100} step={1} value={percent} aria-label="Discount percent"
                     onChange={(e) => set({ percent: Math.min(100, Math.max(0, Number(e.target.value) || 0)) })} />
            </div>
            <div className="level-ticks reuse-ticks" aria-hidden="true"><span>0%</span><span>50%</span><span>100%</span></div>
          </div>

          {percent > 0 && (
            <div className="counts">
              <label className="count-field">Discount starts (optional)
                <input type="datetime-local" value={value.start} onChange={(e) => set({ start: e.target.value })} />
              </label>
              <label className="count-field">Discount ends
                <input type="datetime-local" value={value.end} required onChange={(e) => set({ end: e.target.value })} />
              </label>
            </div>
          )}

          <div className="pricing-preview">
            <span className="field-hint">Learners will see:</span> <PriceTag pricing={preview} />
          </div>
          <p className="field-hint">Payments aren't connected yet, so this only shows the price; learners can still enroll without paying.</p>
        </>
      )}

      {value.type === 'REGISTER' && (
        <p className="field-hint">
          Learners see the full course — modules, outcomes, everything — but instead of enrolling directly they
          send a request. Nothing opens for them until you approve it from Registration requests.
        </p>
      )}
    </fieldset>
  );
}
