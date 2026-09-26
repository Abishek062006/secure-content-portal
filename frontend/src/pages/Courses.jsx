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

const sameSet = (a, b) => a.length === b.length && a.every((c) => b.some((d) => d.id === c.id));

export default function Courses() {
  const { user } = useAuth();
  const [courses, setCourses] = useState([]);
  const [learning, setLearning] = useState([]);
  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');

  useEffect(() => {
    api.get('/api/courses').then(setCourses).catch(() => setCourses([])).finally(() => setLoading(false));
    api.get('/api/me/learning').then(setLearning).catch(() => setLearning([]));
    api.get('/api/profile').then(setMe).catch(() => setMe(null));
  }, []);

  const categories = useMemo(
    () => [...new Set(courses.map((c) => (c.category || '').trim()).filter(Boolean))].sort((a, b) => a.localeCompare(b)),
    [courses],
  );

  const filtering = search.trim() !== '' || category !== '';
  const results = useMemo(() => {
    const query = search.trim().toLowerCase();
    return courses.filter((c) => (!category || c.category === category)
      && (!query || [c.title, c.description, c.category, c.instructorName].some((v) => (v || '').toLowerCase().includes(query))));
  }, [courses, search, category]);

  const inProgress = learning.filter((i) => i.course.progressPercent < 100);

  // Rows of the home view; a row that would only repeat one already shown is left out.
  const rows = useMemo(() => {
    const candidates = [];
    const popular = [...courses].filter((c) => c.enrollmentCount > 0).sort((a, b) => b.enrollmentCount - a.enrollmentCount);
    if (popular.length) candidates.push({ title: 'Trending courses', courses: popular });
    candidates.push({ title: 'New courses', courses: [...courses].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)) });
    const free = courses.filter((c) => c.pricing?.free);
    if (free.length) candidates.push({ title: 'Free courses', courses: free });
    const discounted = courses.filter((c) => c.pricing?.discountActive);
    if (discounted.length) candidates.push({ title: 'Limited-time offers', courses: discounted });
    categories.slice(0, 5).forEach((name) => candidates.push({ title: `Top courses in ${name}`, courses: courses.filter((c) => c.category === name) }));
    const shown = [];
    return candidates.filter((row) => {
      if (shown.some((s) => sameSet(s.courses, row.courses))) return false;
      shown.push(row);
      return true;
    });
  }, [courses, categories]);

  const firstName = (user?.displayName || '').split(' ')[0];

  return (
    <div className="catalog">
      <div className="cat-strip" role="tablist" aria-label="Categories">
        <div className="cat-strip-inner">
          <button type="button" role="tab" aria-selected={category === ''} className={category === '' ? 'active' : ''} onClick={() => setCategory('')}>All courses</button>
          {categories.map((name) => (
            <button key={name} type="button" role="tab" aria-selected={category === name} className={category === name ? 'active' : ''} onClick={() => setCategory(name)}>{name}</button>
          ))}
        </div>
      </div>

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
          <Section title={`${results.length} course${results.length === 1 ? '' : 's'}${category ? ` in ${category}` : ''}`}>
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
