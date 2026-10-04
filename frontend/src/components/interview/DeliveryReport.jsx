import { deliveryMeasures, deliverySummary, deliveryTrends } from '../../lib/delivery';

const STATUS_TEXT = { good: 'On track', watch: 'Worth a look' };

/**
 * How the answers were delivered, from the timing measured in the learner's browser: pace, time before starting and long pauses,
 * with a plain note on each and how they compare with earlier interviews. Feedback only; it doesn't change the score.
 */
export default function DeliveryReport({ questions, trend, sessionId }) {
  const summary = deliverySummary(questions);

  if (!summary) {
    return (
      <section className="progress-card">
        <header><h2>How you delivered your answers</h2></header>
        <p className="field-hint">No speech timing was recorded for this interview. Answer by voice to see your pace and pauses.</p>
      </section>
    );
  }

  const measures = deliveryMeasures(summary);
  const trends = deliveryTrends(summary, trend, sessionId);
  const noisyOnly = summary.spoken > 0 && summary.pace == null && summary.longPausesPerAnswer == null;

  return (
    <section className="progress-card delivery-report">
      <header>
        <h2>How you delivered your answers</h2>
        <span className="field-hint">{summary.spoken} spoken, {summary.typed} typed</span>
      </header>
      <p className="field-hint">Measured in your browser while you answered. It doesn't change your score.</p>

      {measures.length > 0 && (
        <ul className="delivery-list">
          {measures.map((m) => (
            <li key={m.key}>
              <div className="delivery-head">
                <strong>{m.title}</strong>
                <span className="delivery-value">{m.value}</span>
                <span className={`delivery-status ${m.status}`} title={STATUS_TEXT[m.status]}>{m.label}</span>
              </div>
              <p>{m.note}{m.extra ? ` ${m.extra}` : ''}</p>
            </li>
          ))}
        </ul>
      )}

      {noisyOnly && (
        <p className="field-hint">Your spoken answers were too noisy to measure pace and pauses. A quiet room and headphones help.</p>
      )}
      {!noisyOnly && summary.unclear > 0 && (
        <p className="field-hint">
          {summary.unclear} spoken answer{summary.unclear === 1 ? ' was' : 's were'} too noisy to measure, so {summary.unclear === 1 ? 'it is' : 'they are'} left
          out of the pace and pauses.
        </p>
      )}
      {summary.spoken === 0 && <p className="field-hint">You typed every answer. Answer by voice to see your pace and pauses too.</p>}

      {trends.length > 0 && (
        <div className="delivery-trends">
          <h3 className="report-subhead">Compared with your earlier interviews</h3>
          <ul>
            {trends.map((t) => (
              <li key={t.key}>
                <span>{t.title}</span>
                <span className="delivery-sequence">
                  {t.values.map((v, i) => (
                    <span key={i} className={i === t.values.length - 1 ? 'now' : ''}>{v}</span>
                  ))}
                </span>
                {t.unit && <span className="field-hint">{t.unit}</span>}
              </li>
            ))}
          </ul>
          <p className="field-hint">Oldest first. The last number is this interview.</p>
        </div>
      )}
    </section>
  );
}
