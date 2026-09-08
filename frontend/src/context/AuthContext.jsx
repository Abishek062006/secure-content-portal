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
      // eslint-disable-next-line no-console
      console.log('[auth] /api/me resolved', me);
      setUser(me.authenticated ? me.user : null);
    } catch (err) {
      // eslint-disable-next-line no-console
      console.error('[auth] /api/me failed', err);
      setUser(null);
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
