import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import Alert from '../components/Alert';

/** Opens an invite link: joins the team, then shows the hackathon. */
export default function JoinTeam() {
  const { code } = useParams();
  const navigate = useNavigate();
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    api.joinTeam(code)
      .then((team) => { if (!cancelled) navigate(`/hackathons/${team.hackathonId}`, { replace: true }); })
      .catch((err) => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [code, navigate]);

  return (
    <div className="container">
      {error ? (
        <>
          <Alert error={error} />
          <Link className="btn" to="/hackathons">All hackathons</Link>
        </>
      ) : <p className="pdf-loading">Joining your team...</p>}
    </div>
  );
}
