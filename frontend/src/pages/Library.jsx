import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { contentTypeIcon } from '../components/Icons';

const CONTENT_TYPES = [
  { value: 'VIDEO', label: 'Video' },
  { value: 'PDF', label: 'PDF' },
  { value: 'HTML', label: 'HTML page' },
];

function formatDate(iso) {
  return new Date(iso).toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
}

export default function Library() {
  const [searchParams, setSearchParams] = useSearchParams();
  const search = searchParams.get('search') || '';
  const type = searchParams.get('type') || '';
  const category = searchParams.get('category') || '';

  const [items, setItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get('/api/content/categories').then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    setLoading(true);
    const query = new URLSearchParams();
    if (search) query.set('search', search);
    if (type) query.set('type', type);
    if (category) query.set('category', category);
    api.get(`/api/content?${query.toString()}`)
      .then(setItems)
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [search, type, category]);

  const hasFilters = Boolean(search || type || category);

  function onSubmit(e) {
    e.preventDefault();
    const form = e.currentTarget;
    const next = {};
    const s = form.search.value.trim();
    const t = form.type.value;
    const c = form.category.value;
    if (s) next.search = s;
    if (t) next.type = t;
    if (c) next.category = c;
    setSearchParams(next);
  }

  return (
    <div className="container-wide">
      <div className="page-head">
        <h1>Library</h1>
      </div>

      <form className="search-bar" onSubmit={onSubmit}>
        <input type="search" name="search" placeholder="Search titles and descriptions…" defaultValue={search} key={search} />
        <select name="type" defaultValue={type} key={`type-${type}`}>
          <option value="">All types</option>
          {CONTENT_TYPES.map((t) => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>
        <select name="category" defaultValue={category} key={`category-${category}`}>
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
        <button type="submit" className="btn btn-primary">Search</button>
        {hasFilters && (
          <Link className="btn" to="/library">Clear</Link>
        )}
      </form>

      {!loading && items.length === 0 && (
        <div className="empty-state">
          <p>{hasFilters ? 'No content matches your search.' : 'No content has been uploaded yet.'}</p>
        </div>
      )}

      {items.length > 0 && (
        <div className="content-grid">
          {items.map((item) => (
            <Link className="content-card" key={item.id} to={`/content/${item.id}`}>
              <div className={`content-card-thumb thumb-${item.contentType}`}>
                {contentTypeIcon(item.contentType)}
                <span>{CONTENT_TYPES.find((t) => t.value === item.contentType)?.label || item.contentType}</span>
              </div>
              <div className="content-card-body">
                <h2>{item.title}</h2>
                {item.description && <p>{item.description}</p>}
                <div className="content-card-meta">
                  {item.category && <span className="badge">{item.category}</span>}
                  <span>{formatDate(item.createdAt)}</span>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
