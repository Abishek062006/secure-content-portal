import { Link } from 'react-router-dom';
import { mediaUrl } from '../lib/media';

/** A round profile picture, or the person's initial when they have none; links to their profile when given an id. */
export default function Avatar({ name, url, userId, size = 48 }) {
  const style = { width: size, height: size, fontSize: size * 0.4 };
  const inner = url
    ? <img className="avatar-img" src={mediaUrl(url)} alt="" style={style} referrerPolicy="no-referrer" />
    : <span className="avatar-initial" style={style}>{(name || '?').trim().slice(0, 1).toUpperCase()}</span>;
  return userId ? <Link to={`/profile/${userId}`} className="avatar-link" aria-label={`${name}'s profile`}>{inner}</Link> : inner;
}
