import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { api, session } from '../api/session';
import { storage } from '../lib/storage';
import type { User } from '../types';
import { useServer } from './ServerContext';

type Status = 'loading' | 'signedOut' | 'signedIn' | 'unreachable';

interface AuthState {
  status: Status;
  user: User | null;
  token: string | null;
  /** Mensaje para mostrar en la pantalla de acceso (sesión vencida, rol incorrecto...) */
  notice: string | null;
  clearNotice: () => void;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  retryRestore: () => void;
}

const Ctx = createContext<AuthState | null>(null);

export const ROLE_ERROR = 'Esta aplicación es solo para restaurantes. Entra con una cuenta de restaurante; el personal del almacén debe usar el panel web.';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const { ready, baseUrl } = useServer();
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<User | null>(null);
  const [token, setTokenState] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [restoreTick, setRestoreTick] = useState(0);
  const signedInRef = useRef(false);

  const dropSession = useCallback(async (message: string | null) => {
    session.setToken(null);
    setTokenState(null);
    setUser(null);
    signedInRef.current = false;
    await storage.setToken(null);
    setNotice(message);
    setStatus('signedOut');
  }, []);

  // 401 en cualquier petición autenticada: volver al inicio de sesión
  useEffect(() => {
    session.setUnauthorizedHandler(() => {
      if (signedInRef.current) dropSession('Tu sesión terminó. Vuelve a entrar con tu usuario y contraseña.');
    });
    return () => session.setUnauthorizedHandler(null);
  }, [dropSession]);

  // Restaurar sesión al abrir la app
  useEffect(() => {
    if (!ready) return;
    let cancelled = false;
    (async () => {
      const saved = await storage.getToken();
      if (!saved || !baseUrl) {
        if (!cancelled) setStatus('signedOut');
        return;
      }
      session.setToken(saved);
      try {
        const me = await api.me();
        if (cancelled) return;
        if (me.role !== 'restaurante' || !me.restaurant) {
          await dropSession(ROLE_ERROR);
          return;
        }
        setTokenState(saved);
        setUser(me);
        signedInRef.current = true;
        setStatus('signedIn');
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiError && e.isConnectionProblem) {
          setStatus('unreachable');
        } else if (e instanceof ApiError && e.status === 401) {
          await dropSession('Tu sesión terminó. Vuelve a entrar con tu usuario y contraseña.');
        } else {
          setStatus('unreachable');
        }
      }
    })();
    return () => { cancelled = true; };
  }, [ready, baseUrl, restoreTick, dropSession]);

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.login(username.trim(), password);
    if (res.user.role !== 'restaurante' || !res.user.restaurant) {
      // No se guarda nada: la cuenta no corresponde a esta app
      throw new ApiError(ROLE_ERROR, 'http', 403, 'WRONG_ROLE');
    }
    session.setToken(res.token);
    await storage.setToken(res.token);
    setTokenState(res.token);
    setUser(res.user);
    setNotice(null);
    signedInRef.current = true;
    setStatus('signedIn');
  }, []);

  const logout = useCallback(async () => {
    try { await api.logout(); } catch { /* sin conexión: igual se cierra localmente */ }
    await storage.setJson('cart', null);
    await dropSession(null);
  }, [dropSession]);

  const refreshUser = useCallback(async () => {
    const me = await api.me();
    setUser(me);
  }, []);

  const value = useMemo<AuthState>(() => ({
    status, user, token, notice,
    clearNotice: () => setNotice(null),
    login, logout, refreshUser,
    retryRestore: () => { setStatus('loading'); setRestoreTick((t) => t + 1); },
  }), [status, user, token, notice, login, logout, refreshUser]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth fuera de AuthProvider');
  return v;
}
