import { useCallback, useEffect, useRef, useState } from 'react';
import Icon from './Icon';

/** A horizontally scrolling row of cards with round arrow buttons that appear when there is more to see. */
export default function Carousel({ children }) {
  const track = useRef(null);
  const [canLeft, setCanLeft] = useState(false);
  const [canRight, setCanRight] = useState(false);

  const update = useCallback(() => {
    const el = track.current;
    if (!el) return;
    setCanLeft(el.scrollLeft > 4);
    setCanRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 4);
  }, []);

  useEffect(() => {
    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, [update, children]);

  const scroll = (direction) => track.current?.scrollBy({ left: direction * track.current.clientWidth * 0.85, behavior: 'smooth' });

  return (
    <div className="carousel">
      {canLeft && <button type="button" className="carousel-btn left" aria-label="Previous" onClick={() => scroll(-1)}><Icon name="chevron-left" size={22} /></button>}
      <div className="carousel-track" ref={track} onScroll={update}>{children}</div>
      {canRight && <button type="button" className="carousel-btn right" aria-label="Next" onClick={() => scroll(1)}><Icon name="chevron-right" size={22} /></button>}
    </div>
  );
}
