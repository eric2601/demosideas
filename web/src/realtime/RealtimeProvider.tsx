import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { App, Button } from 'antd';
import { io, type Socket } from 'socket.io-client';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { getToken } from '../api/client';
import { useAppConfig } from '../api/hooks';
import { fmtTime } from '../utils/format';
import { installAudioUnlock, playNotificationSound, readLocalSound, writeLocalSound } from './sound';
import type {
  NotificationNewEvent, OrderNewEvent,
} from '../types';

export type ConnectionState = 'connecting' | 'connected' | 'disconnected';

interface RealtimeState {
  connection: ConnectionState;
  /** Interruptor local del sonido (se combina con la configuración del servidor). */
  localSound: boolean;
  setLocalSound: (on: boolean) => void;
  testSound: () => void;
}

const Ctx = createContext<RealtimeState | null>(null);

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { user, token } = useAuth();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { notification } = App.useApp();
  const { data: config } = useAppConfig();
  const [connection, setConnection] = useState<ConnectionState>('disconnected');
  const [localSound, setLocalSoundState] = useState(readLocalSound);

  // Referencias para que los manejadores del socket vean siempre el valor actual sin reconectar
  const soundOn = useRef(true);
  soundOn.current = localSound && (config?.notificationSound ?? true);
  const navRef = useRef(navigate);
  navRef.current = navigate;
  const seen = useRef(new Map<string, number>());

  useEffect(() => { installAudioUnlock(); }, []);

  const setLocalSound = useCallback((on: boolean) => { setLocalSoundState(on); writeLocalSound(on); }, []);

  useEffect(() => {
    if (!user || !token) { setConnection('disconnected'); return; }

    let debounce: number | undefined;
    const refreshAll = () => {
      window.clearTimeout(debounce);
      debounce = window.setTimeout(() => { void qc.invalidateQueries(); }, 250);
    };
    const firstSeen = (key: string) => {
      const now = Date.now();
      for (const [k, t] of seen.current) if (now - t > 60_000) seen.current.delete(k);
      if (seen.current.has(key)) return false;
      seen.current.set(key, now);
      return true;
    };
    const beep = () => { if (soundOn.current) playNotificationSound(); };
    const goto = (path: string, key: string) => { notification.destroy(key); navRef.current(path); };

    const showNewOrder = (p: { orderId: number; folio: string; restaurantName: string; itemCount: number; at: string }) => {
      const key = `order-${p.orderId}`;
      if (!firstSeen(key)) return;
      beep();
      notification.open({
        key,
        type: 'info',
        message: `Nuevo pedido ${p.folio}`,
        description: (
          <div>
            <div><b>{p.restaurantName}</b></div>
            <div>{fmtTime(p.at)} · {p.itemCount} producto{p.itemCount === 1 ? '' : 's'}</div>
          </div>
        ),
        actions: <Button type="primary" size="small" onClick={() => goto(`/pedidos/${p.orderId}`, key)}>Ver pedido</Button>,
        placement: 'topRight',
        duration: 20,
      });
    };

    const socket: Socket = io({
      auth: (cb) => cb({ token: getToken() }),
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionDelayMax: 8000,
    });
    setConnection('connecting');
    let connectedBefore = false;

    socket.on('connect', () => {
      setConnection('connected');
      // Los eventos no se reenvían: al reconectar se recargan los datos por REST
      if (connectedBefore) refreshAll();
      connectedBefore = true;
    });
    socket.on('disconnect', () => setConnection('disconnected'));
    socket.on('connect_error', () => setConnection('disconnected'));
    socket.io.on('reconnect_attempt', () => setConnection('connecting'));

    socket.on('order:new', (e: OrderNewEvent) => {
      showNewOrder({ ...e, at: new Date().toISOString() });
      refreshAll();
    });

    socket.on('notification:new', (n: NotificationNewEvent) => {
      void qc.invalidateQueries({ queryKey: ['notifications'] });
      const p = n.payload ?? {};
      if (n.type === 'pedido_nuevo' && p.orderId) {
        showNewOrder({
          orderId: p.orderId, folio: p.folio ?? '', restaurantName: p.restaurantName ?? n.body ?? '',
          itemCount: p.itemCount ?? 0, at: p.receivedAt ?? n.createdAt,
        });
        return;
      }
      const key = `notif-${n.id}`;
      if (!firstSeen(key)) return;
      beep();
      const warn = n.type === 'producto_agotado' || n.type === 'stock_minimo' || n.type === 'pedido_cancelado';
      const link = p.orderId ? `/pedidos/${p.orderId}` : n.type === 'compra_recibida' ? '/entradas' : '/inventario';
      notification.open({
        key,
        type: warn ? 'warning' : 'info',
        message: n.title,
        description: n.body,
        actions: <Button size="small" onClick={() => goto(link, key)}>Abrir</Button>,
        placement: 'topRight',
        duration: 10,
      });
    });

    socket.on('order:updated', refreshAll);
    socket.on('inventory:changed', refreshAll);

    return () => {
      window.clearTimeout(debounce);
      socket.removeAllListeners();
      socket.disconnect();
      setConnection('disconnected');
    };
  }, [user, token, qc, notification]);

  const value = useMemo(
    () => ({ connection, localSound, setLocalSound, testSound: playNotificationSound }),
    [connection, localSound, setLocalSound],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useRealtime(): RealtimeState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useRealtime fuera de RealtimeProvider');
  return v;
}
