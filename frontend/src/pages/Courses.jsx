import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import Avatar from '../components/Avatar';
import Carousel from '../components/Carousel';
import { CatalogCard, LearningCard } from '../components/CourseCard';
import Icon from '../components/Icon';

function Section({ title, link, children }) {
  return (
    <section className="cat-section">
      <div className="cat-section-head">
        <h2>{title}</h2>
        {link && <Link to={link.to} className="cat-link">{link.label}</Link>}
      </div>
      {children}
    </section>
  );
}

export default function Courses() {
  const { user } = useAuth();
  const [courses, setCourses] = useState([]);
  const [learning, setLearning] = useState([]);
  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => {
    api.get('/api/courses').then(setCourses).catch(() => setCourses([])).finally(() => setLoading(false));
    api.get('/api/me/learning').then(setLearning).catch(() => setLearning([]));
    api.get('/api/profile').then(setMe).catch(() => setMe(null));
  }, []);

  const filtering = search.trim() !== '';
  const results = useMemo(() => {
    const query = search.trim().toLowerCase();
    return courses.filter((c) => !query || [c.title, c.description, c.category, c.instructorName].some((v) => (v || '').toLowerCase().includes(query)));
  }, [courses, search]);

  const inProgress = learning.filter((i) => i.course.progressPercent < 100);

  // The home view: courses picked for this learner, what everyone is taking, then the rest by popularity and by domain.
  const rows = useMemo(() => {
    const enrolledIds = new Set(learning.map((i) => i.course.id));
    const interests = new Set(learning.map((i) => i.course.category).filter(Boolean));
    const topCount = Math.max(1, ...courses.map((c) => c.enrollmentCount || 0));
    // A steady per-day shuffle so "explore" isn't the same order every visit but doesn't jump around on re-render.
    const day = new Date().toISOString().slice(0, 10);
    const jitter = (id) => [...`${id}${day}`].reduce((h, ch) => (h * 31 + ch.charCodeAt(0)) % 1000, 7) / 1000;

    const recommended = courses
      .filter((c) => !enrolledIds.has(c.id))
      .map((c) => ({ c, score: (interests.has(c.category) ? 2 : 0) + (c.enrollmentCount || 0) / topCount + jitter(c.id) * 0.4 }))
      .sort((a, b) => b.score - a.score)
      .map((x) => x.c);
    const popular = [...courses].filter((c) => (c.enrollmentCount || 0) > 0).sort((a, b) => b.enrollmentCount - a.enrollmentCount);
    const shown = new Set([...recommended.slice(0, 10), ...popular.slice(0, 10)].map((c) => c.id));
    const explore = courses
      .filter((c) => !shown.has(c.id))
      .map((c) => ({ c, score: (c.enrollmentCount || 0) / topCount + jitter(c.id) }))
      .sort((a, b) => b.score - a.score)
      .map((x) => x.c);

    const out = [];
    if (recommended.length) out.push({ title: 'Recommended for you', courses: recommended });
    if (popular.length) out.push({ title: 'Popular courses', courses: popular });
    if (explore.length) out.push({ title: 'More courses to explore', courses: explore });
    const byDomain = {};
    courses.forEach((c) => { if (c.category) (byDomain[c.category] = byDomain[c.category] || []).push(c); });
    Object.entries(byDomain)
      .filter(([, list]) => list.length >= 3)
      .sort((a, b) => b[1].length - a[1].length)
      .slice(0, 3)
      .forEach(([name, list]) => out.push({ title: `Top courses in ${name}`, courses: [...list].sort((a, b) => (b.enrollmentCount || 0) - (a.enrollmentCount || 0)) }));
    return out;
  }, [courses, learning]);

  // "R S Abishek" should greet as Abishek: use the longest part of the name.
  const isAdmin = Boolean(user?.admin);
  const firstName = (user?.displayName || '').split(/\s+/).sort((a, b) => b.length - a.length)[0] || '';

  if (isAdmin) {
    // Admins only browse and preview: a search box and the whole catalog, without recommendations.
    const list = search.trim() ? results : [...courses].sort((a, b) => a.title.localeCompare(b.title));
    return (
      <div className="catalog">
        <div className="container-wide catalog-body">
          <header className="welcome">
            <div className="welcome-text">
              <h1>Courses</h1>
              <p>Search and preview the published courses as learners see them.</p>
            </div>
            <label className="catalog-search">
              <Icon name="search" size={18} />
              <input type="search" placeholder="Search courses" value={search} onChange={(e) => setSearch(e.target.value)} />
            </label>
          </header>
          {!loading && list.length === 0 && (
            <div className="empty-state"><p>{search.trim() ? 'No courses match your search.' : 'No courses have been published yet.'}</p></div>
          )}
          <div className="cat-grid" style={{ marginTop: 28 }}>
            {list.map((c) => <CatalogCard key={c.id} course={c} admin />)}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="catalog">
      <div className="container-wide catalog-body">
        <header className="welcome">
          <Avatar name={user?.displayName} url={me?.avatarUrl} userId={user?.id} size={64} />
          <div className="welcome-text">
            <h1>Welcome back, {firstName}</h1>
            <p>{user?.admin ? 'Browse the catalog as your learners see it.' : 'Pick up where you left off, or find something new to learn.'}</p>
          </div>
          <label className="catalog-search">
            <Icon name="search" size={18} />
            <input type="search" placeholder="Search courses" value={search} onChange={(e) => setSearch(e.target.value)} />
          </label>
        </header>

        {!loading && courses.length === 0 && (
          <div className="empty-state"><p>No courses have been published yet. Check back soon.</p></div>
        )}

        {filtering ? (
          <Section title={`${results.length} course${results.length === 1 ? '' : 's'} found`}>
            {results.length === 0
              ? <div className="empty-state"><p>No courses match your search.</p></div>
              : <div className="cat-grid">{results.map((c) => <CatalogCard key={c.id} course={c} admin={user?.admin} />)}</div>}
          </Section>
        ) : (
          <>
            {!user?.admin && inProgress.length > 0 && (
              <Section title="Let's start learning" link={{ to: '/my-learning', label: 'My learning' }}>
                <Carousel>{inProgress.map((item) => <LearningCard key={item.course.id} item={item} />)}</Carousel>
              </Section>
            )}
            {rows.map((row) => (
              <Section key={row.title} title={row.title}>
                <Carousel>{row.courses.map((c) => <CatalogCard key={c.id} course={c} admin={user?.admin} />)}</Carousel>
              </Section>
            ))}
          </>
        )}
      </div>
    </div>
  );
}
