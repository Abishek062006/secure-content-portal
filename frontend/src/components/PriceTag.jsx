const rupees = new Intl.NumberFormat('en-IN');

export function formatPeriodEnd(iso) {
  return new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });
}

/**
 * A course's price: "Free", the plain price, or (while a discount runs) the original struck through,
 * the discounted price, the percentage off and when the offer ends.
 */
export default function PriceTag({ pricing, compact }) {
  if (!pricing) return null;
  if (pricing.free) return <span className="price-tag price-free">Free</span>;

  if (!pricing.discountActive) {
    return <span className="price-tag"><span className="price-now">₹{rupees.format(pricing.priceRupees)}</span></span>;
  }
  return (
    <span className={`price-tag${compact ? ' compact' : ''}`}>
      <span className="price-line">
        <span className="price-now">₹{rupees.format(pricing.finalPriceRupees)}</span>
        <s className="price-was">₹{rupees.format(pricing.priceRupees)}</s>
        <span className="price-off">{pricing.discountPercent}% off</span>
      </span>
      {pricing.discountEnd && <span className="price-ends">Offer ends {formatPeriodEnd(pricing.discountEnd)}</span>}
    </span>
  );
}
