import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';
import NetInfo from '@react-native-community/netinfo';
import { io, Socket } from 'socket.io-client';
import { api } from '../api/session';
import { statusMessage } from '../lib/status';
import type { AppNotification, OrderUpdatedEvent } from '../types';
import { useAuth } from './AuthContext';
import { useServer } from './ServerContext';
import { useToast } from './ToastContext';

export type LinkState = 'online' | 'degraded' | 'offline';

interface ConnectionState {
  /** online = socket conectado y servidor responde; degraded = el servidor responde pero sin tiempo real; offline = sin servidor */
  link: LinkState;
  socketConnected: boolean;
  healthOk: boolean;
  /** Se incrementa con cada evento del servidor o reconexión: las pantallas lo usan para recargar por REST */
  dataVersion: number;
  unreadCount: number;
  refreshUnread: () => Promise<void>;
  setUnreadCount: (n: number) => void;
  checkNow: () => Promise<void>;
}

const Ctx = createContext<ConnectionState | null>(null);
const HEALTH_INTERVAL_MS = 15_000;

export function ConnectionProvider({ children }: { children: React.ReactNode }) {
  const { baseUrl } = useServer();
  const { status, token } = useAuth();
  const toast = useToast();
  const [socketConnected, setSocketConnected] = useState(false);
  const [healthOk, setHealthOk] = useState(true);
  const [dataVersion, setDataVersion] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0);
  const lastOrderToast = useRef(0);
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const bump = useCallback(() => setDataVersion((v) => v + 1), []);

  const checkNow = useCallback(async () => {
    if (!baseUrl) return;
    try {
      await api.health();
      setHealthOk(true);
    } catch {
      setHealthOk(false);
    }
  }, [baseUrl]);

  const refreshUnread = useCallback(async () => {
    try {
      const r = await api.notifications({ unread: true, pageSize: 1 });
      setUnreadCount(r.unreadCount);
    } catch {
      // se conserva el último valor
    }
  }, []);

  const signedIn = status === 'signedIn';

  // Health periódico (solo con la app en primer plano)
  useEffect(() => {
    if (!signedIn) return;
    checkNow();
    const t = setInterval(() => { if (AppState.currentState === 'active') checkNow(); }, HEALTH_INTERVAL_MS);
    const sub = AppState.addEventListener('change', (s) => { if (s === 'active') { checkNow(); bump(); } });
    const net = NetInfo.addEventListener((s) => { if (s.isConnected) { checkNow(); } else setHealthOk(false); });
    return () => { clearInterval(t); sub.remove(); net(); };
  }, [signedIn, checkNow, bump]);

  // Socket.IO con reconexión automática
  useEffect(() => {
    if (!signedIn || !baseUrl || !token) {
      setSocketConnected(false);
      return;
    }
    const socket: Socket = io(baseUrl, {
      auth: { token },
      transports: ['websocket', 'polling'],
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionDelayMax: 8000,
      timeout: 8000,
    });
    let firstConnect = true;

    socket.on('connect', () => {
      setSocketConnected(true);
      setHealthOk(true);
      if (!firstConnect) bump(); // al reconectar se recarga todo por REST
      firstConnect = false;
      refreshUnread();
    });
    socket.on('disconnect', () => setSocketConnected(false));
    socket.on('connect_error', () => setSocketConnected(false));

    socket.on('order:updated', (e: OrderUpdatedEvent) => {
      lastOrderToast.current = Date.now();
      toastRef.current.show(statusMessage(e.folio, e.status), e.status === 'cancelado' ? 'error' : 'info');
      bump();
    });
    socket.on('notification:new', (n: AppNotification) => {
      setUnreadCount((c) => c + 1);
      // Si order:updated ya mostró el aviso, no se repite
      if (Date.now() - lastOrderToast.current > 3000) toastRef.current.show(n.title || 'Tienes un aviso nuevo');
      bump();
    });

    return () => {
      socket.removeAllListeners();
      socket.disconnect();
      setSocketConnected(false);
    };
  }, [signedIn, baseUrl, token, bump, refreshUnread]);

  useEffect(() => {
    if (signedIn) refreshUnread();
  }, [signedIn, dataVersion, refreshUnread]);

  const link: LinkState = socketConnected && healthOk ? 'online' : healthOk && !socketConnected ? 'degraded' : 'offline';
  // Un socket conectado implica servidor accesible aunque el health tarde en refrescarse
  const effectiveLink: LinkState = socketConnected ? 'online' : link;

  const value = useMemo<ConnectionState>(() => ({
    link: effectiveLink, socketConnected, healthOk, dataVersion, unreadCount, refreshUnread, setUnreadCount, checkNow,
  }), [effectiveLink, socketConnected, healthOk, dataVersion, unreadCount, refreshUnread, checkNow]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useConnection(): ConnectionState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useConnection fuera de ConnectionProvider');
  return v;
}
