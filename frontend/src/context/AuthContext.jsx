import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, API_BASE } from '../api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const me = await api.get('/api/me');
      if (me.authenticated) {
        setUser(me.user);
      } else {
        const devStored = import.meta.env.DEV && typeof window !== 'undefined' ? localStorage.getItem('dev_user') : null;
        if (devStored) {
          try { setUser(JSON.parse(devStored)); } catch { setUser(null); }
        } else {
          setUser(null);
        }
      }
    } catch {
      const devStored = import.meta.env.DEV && typeof window !== 'undefined' ? localStorage.getItem('dev_user') : null;
      if (devStored) {
        try { setUser(JSON.parse(devStored)); } catch { setUser(null); }
      } else {
        setUser(null);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const login = () => {
    window.location.href = `${API_BASE}/oauth2/authorization/google`;
  };

  const logout = async () => {
    await api.post('/logout');
    setUser(null);
    window.location.href = '/';
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
