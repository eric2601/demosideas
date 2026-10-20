import { Tx } from '../db';
import { emitRestaurant, emitStaff } from '../realtime';

export type NotificationType =
  | 'pedido_nuevo' | 'pedido_cancelado' | 'pedido_cantidades' | 'pedido_estado' | 'pedido_listo'
  | 'producto_agotado' | 'stock_minimo' | 'compra_recibida';

interface Params {
  type: NotificationType;
  title: string;
  body?: string;
  payload?: Record<string, unknown>;
  /** null = personal del almacén; número = restaurante destinatario */
  restaurantId?: number | null;
}

/** Guarda la notificación y la emite al confirmarse la transacción. */
export async function notify(tx: Tx, p: Params) {
  const audience = p.restaurantId ? 'restaurant' : 'staff';
  const row = await tx.one<{ id: number; createdAt: Date }>(
    `INSERT INTO notifications (type, title, body, payload, audience, restaurant_id)
     VALUES ($1,$2,$3,$4,$5,$6) RETURNING id, created_at`,
    [p.type, p.title, p.body ?? null, JSON.stringify(p.payload ?? {}), audience, p.restaurantId ?? null],
  );
  const message = { id: row!.id, type: p.type, title: p.title, body: p.body ?? null,
    payload: p.payload ?? {}, createdAt: row!.createdAt, read: false };
  tx.afterCommit(() => {
    if (p.restaurantId) emitRestaurant(p.restaurantId, 'notification:new', message);
    else emitStaff('notification:new', message);
  });
}
