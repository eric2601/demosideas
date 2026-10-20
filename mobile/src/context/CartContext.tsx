import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import * as Crypto from 'expo-crypto';
import { IdempotencyAttempt, StoredAttempt } from '../lib/idempotency';
import { clampQuantity } from '../lib/format';
import { storage } from '../lib/storage';
import type { CatalogProduct, CreateOrderBody } from '../types';
import { useAuth } from './AuthContext';

export interface CartLine {
  productId: number;
  sku: string;
  name: string;
  unitName: string;
  quantity: number;
}

interface PersistedCart { userId: number; lines: CartLine[]; notes: string }

interface CartState {
  lines: CartLine[];
  notes: string;
  setNotes: (n: string) => void;
  quantityOf: (productId: number) => number;
  setQuantity: (p: Pick<CatalogProduct, 'id' | 'sku' | 'name' | 'unitName'>, qty: number) => void;
  remove: (productId: number) => void;
  clear: () => void;
  count: number;
  buildBody: () => CreateOrderBody;
  /** Clave de idempotencia del intento actual (estable mientras el pedido no cambie) */
  currentKey: () => string;
  /** Hay un intento de envío previo sin confirmar (para mostrar "Reintentar") */
  hasPendingAttempt: () => boolean;
  /** Llamar tras un envío exitoso */
  completeAttempt: () => void;
}

const Ctx = createContext<CartState | null>(null);

export function CartProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const userId = user?.id ?? null;
  const [lines, setLines] = useState<CartLine[]>([]);
  const [notes, setNotesState] = useState('');
  const attempt = useRef(new IdempotencyAttempt(() => Crypto.randomUUID()));
  const loadedFor = useRef<number | null>(null);

  // Cargar carrito guardado del usuario (sobrevive a reinicios de la app)
  useEffect(() => {
    if (userId === null) {
      loadedFor.current = null;
      setLines([]);
      setNotesState('');
      attempt.current = new IdempotencyAttempt(() => Crypto.randomUUID());
      return;
    }
    (async () => {
      const saved = await storage.getJson<PersistedCart>('cart');
      const att = await storage.getJson<StoredAttempt>('attempt');
      if (saved && saved.userId === userId) {
        setLines(saved.lines);
        setNotesState(saved.notes ?? '');
        attempt.current = new IdempotencyAttempt(() => Crypto.randomUUID(), att);
      }
      loadedFor.current = userId;
    })();
  }, [userId]);

  useEffect(() => {
    if (userId === null || loadedFor.current !== userId) return;
    storage.setJson('cart', lines.length ? ({ userId, lines, notes } satisfies PersistedCart) : null);
  }, [lines, notes, userId]);

  const setQuantity = useCallback<CartState['setQuantity']>((p, qty) => {
    const q = clampQuantity(qty);
    setLines((cur) => {
      if (q <= 0) return cur.filter((l) => l.productId !== p.id);
      const exists = cur.some((l) => l.productId === p.id);
      if (exists) return cur.map((l) => (l.productId === p.id ? { ...l, quantity: q } : l));
      return [...cur, { productId: p.id, sku: p.sku, name: p.name, unitName: p.unitName, quantity: q }];
    });
  }, []);

  const value = useMemo<CartState>(() => ({
    lines,
    notes,
    setNotes: setNotesState,
    quantityOf: (id) => lines.find((l) => l.productId === id)?.quantity ?? 0,
    setQuantity,
    remove: (id) => setLines((cur) => cur.filter((l) => l.productId !== id)),
    clear: () => { setLines([]); setNotesState(''); attempt.current.clear(); storage.setJson('attempt', null); },
    count: lines.length,
    buildBody: () => ({
      items: lines.map((l) => ({ productId: l.productId, quantity: l.quantity })),
      ...(notes.trim() ? { notes: notes.trim() } : {}),
    }),
    currentKey: () => {
      const body: CreateOrderBody = {
        items: lines.map((l) => ({ productId: l.productId, quantity: l.quantity })),
        ...(notes.trim() ? { notes: notes.trim() } : {}),
      };
      const key = attempt.current.keyFor(body);
      storage.setJson('attempt', attempt.current.snapshot());
      return key;
    },
    hasPendingAttempt: () => attempt.current.snapshot() !== null,
    completeAttempt: () => { attempt.current.clear(); storage.setJson('attempt', null); },
  }), [lines, notes, setQuantity]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useCart(): CartState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useCart fuera de CartProvider');
  return v;
}
