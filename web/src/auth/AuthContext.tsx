import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, getToken, setToken, setUnauthorizedHandler } from '../api/client';
import type { LoginResponse, Permission, SessionUser } from '../types';

/** Error lanzado cuando un usuario de restaurante intenta entrar al panel. */
export class RestaurantRoleError extends Error {
  constructor() {
    super('Los usuarios de restaurante no pueden usar el panel web. Usa la app Android para hacer pedidos.');
  }
}

interface AuthState {
  user: SessionUser | null;
  token: string | null;
  loading: boolean;
  sessionExpired: boolean;
  login: (username: string, password: string) => Promise<SessionUser>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  /** true si el usuario tiene AL MENOS UNO de los permisos indicados (solo UX; el servidor valida). */
  can: (...perms: Permission[]) => boolean;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const [user, setUser] = useState<SessionUser | null>(null);
  const [token, setTokenState] = useState<string | null>(getToken());
  const [loading, setLoading] = useState<boolean>(!!getToken());
  const [sessionExpired, setSessionExpired] = useState(false);

  const clear = useCallback((expired: boolean) => {
    setToken(null);
    setTokenState(null);
    setUser(null);
    setSessionExpired(expired);
    qc.clear();
  }, [qc]);

  // 401 en cualquier llamada: volver al login
  useEffect(() => {
    setUnauthorizedHandler(() => { if (getToken()) clear(true); });
    return () => setUnauthorizedHandler(null);
  }, [clear]);

  // Restaurar sesión desde sessionStorage
  useEffect(() => {
    if (!getToken()) return;
    api.get<SessionUser>('/auth/me')
      .then((u) => {
        if (u.role === 'restaurante') clear(false);
        else setUser(u);
      })
      .catch(() => clear(false))
      .finally(() => setLoading(false));
  }, [clear]);

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.post<LoginResponse>('/auth/login', { username, password });
    if (res.user.role === 'restaurante') throw new RestaurantRoleError();
    qc.clear();
    setToken(res.token);
    setTokenState(res.token);
    setUser(res.user);
    setSessionExpired(false);
    return res.user;
  }, [qc]);

  const logout = useCallback(async () => {
    try { await api.post('/auth/logout'); } catch { /* da igual si falla */ }
    clear(false);
  }, [clear]);

  const refreshUser = useCallback(async () => {
    setUser(await api.get<SessionUser>('/auth/me'));
  }, []);

  const can = useCallback((...perms: Permission[]) => !!user && perms.some((p) => user.permissions.includes(p)), [user]);

  const value = useMemo(
    () => ({ user, token, loading, sessionExpired, login, logout, refreshUser, can }),
    [user, token, loading, sessionExpired, login, logout, refreshUser, can],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth fuera de AuthProvider');
  return v;
}
