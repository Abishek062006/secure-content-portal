import { useEffect } from 'react';

/** The feed and profile sit on a light-grey page like other social networks; other pages keep the app's own backdrop. */
export default function useSocialBackground() {
  useEffect(() => {
    document.body.classList.add('social-bg');
    return () => document.body.classList.remove('social-bg');
  }, []);
}
