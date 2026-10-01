import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Avatar from './Avatar';
import { useAuth } from '../context/AuthContext';
import { mediaUrl } from '../lib/media';
import { api } from '../api';

/** The small "you" card on the left of the feed: banner, picture, name, headline and a few numbers. */
export default function ProfileCard({ me }) {
  const { user } = useAuth();
  const [connectionCount, setConnectionCount] = useState(0);

  useEffect(() => {
    if (user) {
      api.getConnectionsCount()
        .then((res) => setConnectionCount(res?.count || 0))
        .catch(() => setConnectionCount(0));
    }
  }, [user]);

  if (!me) return <aside className="side-card profile-card skeleton-card" />;

  return (
    <aside className="side-card profile-card">
      <div className="profile-card-banner" style={me.bannerUrl ? { backgroundImage: `url(${mediaUrl(me.bannerUrl)})` } : undefined} />
      <div className="profile-card-avatar"><Avatar name={me.name} url={me.avatarUrl} userId={me.userId} size={72} /></div>
      <div className="profile-card-body">
        <Link to="/profile" className="profile-card-name">{me.name}</Link>
        <p className="muted">{me.headline || 'Add a headline to tell people what you do'}</p>
      </div>
      <div className="profile-card-stats">
        <Link to="/network"><span>Connections</span><strong>{connectionCount}</strong></Link>
        {!user?.admin && <Link to="/profile"><span>Certificates</span><strong>{me.certificates?.length || 0}</strong></Link>}
        <Link to="/profile"><span>Posts</span><strong>{me.postCount || 0}</strong></Link>
        {!user?.admin && <Link to="/my-learning"><span>My learning</span><strong>→</strong></Link>}
      </div>
    </aside>
  );
}
