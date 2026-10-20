import { db, Tx, withTx } from '../db';
import { badRequest, conflict, forbidden, notFound } from '../errors';
import { AuthUser } from '../middleware/auth';
import { applyMovement, lockInventory, round } from './inventory';
import { nextFolio, refreshShortages } from './shortages';
import { notify } from './notifications';
import { audit } from './audit';
import { emitRestaurant, emitStaff } from '../realtime';

export type OrderStatus =
  | 'nuevo' | 'pendiente_revision' | 'en_preparacion' | 'parcialmente_surtido'
  | 'listo_entrega' | 'en_entrega' | 'entregado' | 'cancelado';

const TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  nuevo: ['pendiente_revision', 'en_preparacion', 'cancelado'],
  pendiente_revision: ['en_preparacion', 'cancelado'],
  en_preparacion: ['parcialmente_surtido', 'listo_entrega', 'cancelado'],
  parcialmente_surtido: ['en_preparacion', 'listo_entrega', 'cancelado'],
  listo_entrega: ['en_entrega', 'cancelado'],
  en_entrega: ['entregado'],
  entregado: [],
  cancelado: [],
};

interface OrderRow {
  id: number; folio: string; restaurantId: number; status: OrderStatus;
  stockExitedAt: Date | null; receivedAt: Date | null;
}
interface ItemRow {
  id: number; productId: number; productName: string; qtyRequested: number;
  qtyAuthorized: number | null; qtyReserved: number; qtyPrepared: number; qtyDelivered: number;
}

const target = (i: ItemRow) => i.qtyAuthorized ?? i.qtyRequested;

async function lockOrder(tx: Tx, id: number): Promise<OrderRow> {
  const o = await tx.one<OrderRow>(
    `SELECT id, folio, restaurant_id, status, stock_exited_at, received_at FROM orders WHERE id = $1 FOR UPDATE`, [id]);
  if (!o) throw notFound('Pedido');
  return o;
}
const loadItems = (tx: Tx, orderId: number) =>
  tx.query<ItemRow>(`SELECT id, product_id, product_name, qty_requested, qty_authorized, qty_reserved, qty_prepared, qty_delivered
                       FROM order_items WHERE order_id = $1 ORDER BY id`, [orderId]);

function assertTransition(from: OrderStatus, to: OrderStatus) {
  if (!TRANSITIONS[from].includes(to)) {
    throw conflict(`No se puede pasar el pedido de "${from}" a "${to}"`);
  }
}

async function setStatus(tx: Tx, o: OrderRow, to: OrderStatus, user: AuthUser, extra: { note?: string; changes?: unknown; event?: string } = {}) {
  assertTransition(o.status, to);
  await tx.query(`UPDATE orders SET status = $2 WHERE id = $1`, [o.id, to]);
  await tx.query(
    `INSERT INTO order_events (order_id, event, from_status, to_status, user_id, note, changes) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
    [o.id, extra.event ?? 'cambio_estado', o.status, to, user.id, extra.note ?? null,
     extra.changes ? JSON.stringify(extra.changes) : null]);
  const previous = o.status;
  o.status = to;
  tx.afterCommit(() => {
    const evt = { orderId: o.id, folio: o.folio, status: to, previousStatus: previous };
    emitStaff('order:updated', evt);
    emitRestaurant(o.restaurantId, 'order:updated', evt);
  });
}

/** Reserva lo que falte hasta el objetivo de cada partida, limitado a lo disponible. */
async function reserveMissing(tx: Tx, o: OrderRow, items: ItemRow[], user: AuthUser) {
  const stock = await lockInventory(tx, items.map((i) => i.productId));
  const changes: unknown[] = [];
  for (const it of items) {
    const need = round(target(it) - it.qtyReserved);
    const s = stock.get(it.productId)!;
    const grab = round(Math.min(need, Math.max(0, s.onHand - s.reserved)));
    if (grab <= 0) continue;
    await applyMovement(tx, {
      productId: it.productId, type: 'reserva', quantity: grab, deltaOnHand: 0, deltaReserved: grab,
      userId: user.id, referenceType: 'pedido', referenceId: o.id, referenceFolio: o.folio, reason: 'Reserva para pedido',
    });
    s.reserved = round(s.reserved + grab);
    it.qtyReserved = round(it.qtyReserved + grab);
    await tx.query(`UPDATE order_items SET qty_reserved = $2 WHERE id = $1`, [it.id, it.qtyReserved]);
    changes.push({ itemId: it.id, productId: it.productId, reservedAdded: grab });
  }
  return changes;
}

async function releaseReservation(tx: Tx, o: OrderRow, it: ItemRow, amount: number, user: AuthUser, reason: string) {
  if (amount <= 0) return;
  await applyMovement(tx, {
    productId: it.productId, type: 'liberacion_reserva', quantity: amount, deltaOnHand: 0, deltaReserved: -amount,
    userId: user.id, referenceType: 'pedido', referenceId: o.id, referenceFolio: o.folio, reason,
  });
  it.qtyReserved = round(it.qtyReserved - amount);
  await tx.query(`UPDATE order_items SET qty_reserved = $2 WHERE id = $1`, [it.id, it.qtyReserved]);
}

// ───────────────────────── Creación (restaurante) ─────────────────────────

export interface CreateOrderInput { items: { productId: number; quantity: number }[]; notes?: string; idempotencyKey?: string }

export async function createOrder(user: AuthUser, input: CreateOrderInput, ip: string | null) {
  if (!user.restaurantId) throw forbidden('Solo los usuarios de restaurante pueden crear pedidos');
  const restaurantId = user.restaurantId;

  if (input.idempotencyKey) {
    const existing = await db.one<{ id: number }>(
      `SELECT id FROM orders WHERE restaurant_id = $1 AND idempotency_key = $2`, [restaurantId, input.idempotencyKey]);
    if (existing) return { orderId: existing.id, duplicate: true };
  }

  // Consolida partidas repetidas
  const merged = new Map<number, number>();
  for (const i of input.items) merged.set(i.productId, round((merged.get(i.productId) ?? 0) + i.quantity));

  try {
    const orderId = await withTx(async (tx) => {
      const ids = [...merged.keys()];
      const products = await tx.query<{ id: number; sku: string; name: string; unitName: string; salePrice: number | null; active: boolean }>(
        `SELECT p.id, p.sku, p.name, u.name AS unit_name, p.sale_price, p.active FROM products p JOIN units u ON u.id = p.unit_id
          WHERE p.id = ANY($1::int[])`, [ids]);
      if (products.length !== ids.length) throw badRequest('Algún producto del pedido no existe');
      const inactive = products.find((p) => !p.active);
      if (inactive) throw badRequest(`El producto "${inactive.name}" no está disponible para pedidos`);

      const restricted = await tx.one<{ n: number }>(
        `SELECT count(*)::int AS n FROM restaurant_product_access WHERE restaurant_id = $1`, [restaurantId]);
      if (restricted!.n > 0) {
        const allowed = await tx.query<{ productId: number }>(
          `SELECT product_id FROM restaurant_product_access WHERE restaurant_id = $1 AND product_id = ANY($2::int[])`, [restaurantId, ids]);
        if (allowed.length !== ids.length) throw forbidden('Algún producto no está autorizado para tu restaurante');
      }

      const folio = await nextFolio(tx, 'seq_order_folio', 'PED');
      const order = await tx.one<{ id: number }>(
        `INSERT INTO orders (folio, restaurant_id, requested_by, notes, idempotency_key)
         VALUES ($1,$2,$3,$4,$5) RETURNING id`,
        [folio, restaurantId, user.id, input.notes ?? null, input.idempotencyKey ?? null]);

      for (const p of products) {
        await tx.query(
          `INSERT INTO order_items (order_id, product_id, sku, product_name, unit_name, unit_price, qty_requested)
           VALUES ($1,$2,$3,$4,$5,$6,$7)`,
          [order!.id, p.id, p.sku, p.name, p.unitName, p.salePrice, merged.get(p.id)]);
      }
      const orderRow: OrderRow = { id: order!.id, folio, restaurantId, status: 'nuevo', stockExitedAt: null, receivedAt: null };
      const items = await loadItems(tx, order!.id);
      // Reserva atómica: las filas de inventario quedan bloqueadas, por lo que dos pedidos simultáneos no comprometen la misma mercancía
      await reserveMissing(tx, orderRow, items, user);
      await tx.query(
        `INSERT INTO order_events (order_id, event, to_status, user_id, note) VALUES ($1,'creado','nuevo',$2,$3)`,
        [order!.id, user.id, input.notes ?? null]);
      await refreshShortages(tx, items.map((i) => i.productId));

      const rest = await tx.one<{ name: string }>(`SELECT name FROM restaurants WHERE id = $1`, [restaurantId]);
      await notify(tx, {
        type: 'pedido_nuevo', title: `Nuevo pedido ${folio}`,
        body: `${rest!.name} · ${items.length} producto(s)`,
        payload: { orderId: order!.id, folio, restaurantId, restaurantName: rest!.name, itemCount: items.length, receivedAt: new Date().toISOString() },
      });
      await audit(tx, { userId: user.id, action: 'pedido.crear', entity: 'order', entityId: order!.id, ip });
      tx.afterCommit(() => { emitStaff('order:new', { orderId: order!.id, folio, restaurantId, restaurantName: rest!.name, itemCount: items.length }); });
      return order!.id;
    });
    return { orderId, duplicate: false };
  } catch (err: any) {
    // Doble envío concurrente con la misma clave: devolver el pedido ya creado
    if (err?.code === '23505' && input.idempotencyKey) {
      const existing = await db.one<{ id: number }>(
        `SELECT id FROM orders WHERE restaurant_id = $1 AND idempotency_key = $2`, [restaurantId, input.idempotencyKey]);
      if (existing) return { orderId: existing.id, duplicate: true };
    }
    throw err;
  }
}

// ───────────────────────── Acciones del almacén ─────────────────────────

export const markReviewing = (id: number, user: AuthUser) => withTx(async (tx) => {
  const o = await lockOrder(tx, id);
  await setStatus(tx, o, 'pendiente_revision', user);
  await tx.query(`UPDATE orders SET assigned_to = COALESCE(assigned_to, $2) WHERE id = $1`, [id, user.id]);
});

export const acceptOrder = (id: number, user: AuthUser, input: { items?: { itemId: number; authorizedQuantity: number }[]; note?: string }) =>
  withTx(async (tx) => {
    const o = await lockOrder(tx, id);
    if (!['nuevo', 'pendiente_revision', 'parcialmente_surtido', 'en_preparacion'].includes(o.status)) {
      throw conflict(`El pedido está en "${o.status}" y ya no admite cambios de cantidades`);
    }
    const items = await loadItems(tx, id);
    const changes: unknown[] = [];
    for (const upd of input.items ?? []) {
      const it = items.find((x) => x.id === upd.itemId);
      if (!it) throw badRequest(`La partida ${upd.itemId} no pertenece al pedido`);
      if (upd.authorizedQuantity > it.qtyRequested) throw badRequest('No se puede autorizar más de lo solicitado');
      if (upd.authorizedQuantity < it.qtyPrepared) throw badRequest(`"${it.productName}" ya tiene ${it.qtyPrepared} preparadas; ajusta primero la preparación`);
      const prev = target(it);
      if (prev === upd.authorizedQuantity) continue;
      it.qtyAuthorized = upd.authorizedQuantity;
      await tx.query(`UPDATE order_items SET qty_authorized = $2 WHERE id = $1`, [it.id, it.qtyAuthorized]);
      await releaseReservation(tx, o, it, Math.max(0, round(it.qtyReserved - it.qtyAuthorized)), user, 'Cantidad autorizada reducida');
      changes.push({ itemId: it.id, product: it.productName, from: prev, to: upd.authorizedQuantity });
    }
    for (const it of items) {
      if (it.qtyAuthorized === null) {
        it.qtyAuthorized = it.qtyRequested;
        await tx.query(`UPDATE order_items SET qty_authorized = $2 WHERE id = $1`, [it.id, it.qtyAuthorized]);
      }
    }
    const reserved = await reserveMissing(tx, o, items, user);
    if (o.status === 'nuevo' || o.status === 'pendiente_revision') {
      await setStatus(tx, o, 'en_preparacion', user, { note: input.note, changes: { quantities: changes, reserved } });
      await tx.query(`UPDATE orders SET assigned_to = COALESCE(assigned_to, $2) WHERE id = $1`, [id, user.id]);
    } else {
      await tx.query(`INSERT INTO order_events (order_id, event, user_id, note, changes) VALUES ($1,'cantidades_modificadas',$2,$3,$4)`,
        [id, user.id, input.note ?? null, JSON.stringify({ quantities: changes, reserved })]);
    }
    if (changes.length) {
      await notify(tx, { type: 'pedido_cantidades', title: `Cambios en cantidades del pedido ${o.folio}`,
        payload: { orderId: id, folio: o.folio, changes }, restaurantId: o.restaurantId });
    }
    await refreshShortages(tx, items.map((i) => i.productId));
    if (changes.length) await audit(tx, { userId: user.id, action: 'pedido.cantidades', entity: 'order', entityId: id, detail: changes });
  });

export const reserveOrderPending = (id: number, user: AuthUser) => withTx(async (tx) => {
  const o = await lockOrder(tx, id);
  if (['en_entrega', 'entregado', 'cancelado'].includes(o.status)) throw conflict('El pedido ya no admite reservas');
  const items = await loadItems(tx, id);
  const reserved = await reserveMissing(tx, o, items, user);
  if (reserved.length) {
    await tx.query(`INSERT INTO order_events (order_id, event, user_id, changes) VALUES ($1,'reserva_adicional',$2,$3)`,
      [id, user.id, JSON.stringify({ reserved })]);
  }
  await refreshShortages(tx, items.map((i) => i.productId));
  return reserved.length;
});

export const prepareOrder = (id: number, user: AuthUser, input: { items?: { itemId: number; preparedQuantity: number }[]; closePartial?: boolean; note?: string }) =>
  withTx(async (tx) => {
    const o = await lockOrder(tx, id);
    if (!['en_preparacion', 'parcialmente_surtido'].includes(o.status)) {
      throw conflict(`El pedido está en "${o.status}"; solo se prepara desde "en_preparacion"`);
    }
    const items = await loadItems(tx, id);
    for (const it of items) if (it.qtyAuthorized === null) it.qtyAuthorized = it.qtyRequested;
    await reserveMissing(tx, o, items, user);

    const chosen = new Map((input.items ?? []).map((i) => [i.itemId, i.preparedQuantity]));
    for (const itemId of chosen.keys()) if (!items.some((i) => i.id === itemId)) throw badRequest(`La partida ${itemId} no pertenece al pedido`);
    for (const it of items) {
      const prepared = chosen.has(it.id) ? chosen.get(it.id)! : it.qtyReserved;
      if (prepared > it.qtyReserved) throw badRequest(`No hay reservado suficiente de "${it.productName}" (reservado ${it.qtyReserved})`);
      if (prepared > target(it)) throw badRequest(`"${it.productName}": no se puede preparar más de lo autorizado`);
      it.qtyPrepared = prepared;
    }
    const allComplete = items.every((i) => i.qtyPrepared === target(i));
    if (!allComplete && input.closePartial && items.every((i) => i.qtyPrepared === 0)) {
      throw badRequest('No hay nada que surtir; cancela el pedido si no se atenderá');
    }
    for (const it of items) {
      // Al cerrar parcial, lo no surtido deja de ser demanda pendiente (queda trazado en el historial)
      await tx.query(`UPDATE order_items SET qty_prepared = $2, qty_authorized = $3 WHERE id = $1`,
        [it.id, it.qtyPrepared, input.closePartial && !allComplete ? it.qtyPrepared : target(it)]);
    }
    const next: OrderStatus = allComplete || input.closePartial ? 'listo_entrega' : 'parcialmente_surtido';
    const summary = items.map((i) => ({ itemId: i.id, product: i.productName, authorized: target(i), prepared: i.qtyPrepared }));
    if (next === o.status) {
      await tx.query(`INSERT INTO order_events (order_id, event, user_id, note, changes) VALUES ($1,'preparacion_actualizada',$2,$3,$4)`,
        [id, user.id, input.note ?? null, JSON.stringify(summary)]);
    } else {
      await setStatus(tx, o, next, user, { note: input.note, changes: summary, event: 'preparado' });
    }
    if (next === 'listo_entrega') {
      await notify(tx, { type: 'pedido_listo', title: `Pedido ${o.folio} listo para entregar`,
        payload: { orderId: id, folio: o.folio }, restaurantId: o.restaurantId });
    }
    await refreshShortages(tx, items.map((i) => i.productId));
  });

export const dispatchOrder = (id: number, user: AuthUser, input: { assignedTo?: number }) => withTx(async (tx) => {
  const o = await lockOrder(tx, id);
  if (o.stockExitedAt) throw conflict('La mercancía de este pedido ya salió del inventario');
  assertTransition(o.status, 'en_entrega');
  const items = await loadItems(tx, id);
  for (const it of items) {
    if (it.qtyPrepared > 0) {
      await applyMovement(tx, {
        productId: it.productId, type: 'salida_pedido', quantity: it.qtyPrepared,
        deltaOnHand: -it.qtyPrepared, deltaReserved: -it.qtyPrepared, userId: user.id,
        referenceType: 'pedido', referenceId: id, referenceFolio: o.folio, reason: 'Salida definitiva por entrega',
      });
    }
    // Lo reservado y no preparado regresa al disponible
    const leftover = round(it.qtyReserved - it.qtyPrepared);
    if (leftover > 0) {
      await applyMovement(tx, {
        productId: it.productId, type: 'liberacion_reserva', quantity: leftover, deltaOnHand: 0, deltaReserved: -leftover,
        userId: user.id, referenceType: 'pedido', referenceId: id, referenceFolio: o.folio, reason: 'Reserva sobrante al despachar',
      });
    }
    await tx.query(`UPDATE order_items SET qty_delivered = qty_prepared, qty_reserved = 0 WHERE id = $1`, [it.id]);
  }
  const assignee = input.assignedTo ?? (user.role === 'entrega' ? user.id : null);
  if (assignee) {
    const ok = await tx.one(`SELECT 1 FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = $1 AND u.active AND r.code IN ('entrega','almacen','admin')`, [assignee]);
    if (!ok) throw badRequest('El responsable de entrega no es válido');
  }
  await tx.query(`UPDATE orders SET stock_exited_at = now(), assigned_to = COALESCE($2, assigned_to) WHERE id = $1`, [id, assignee]);
  await tx.query(`INSERT INTO deliveries (order_id, assigned_to, status, dispatched_at) VALUES ($1,$2,'en_ruta',now())`, [id, assignee]);
  await setStatus(tx, o, 'en_entrega', user, { event: 'despachado' });
  await notify(tx, { type: 'pedido_estado', title: `Pedido ${o.folio} en camino`, payload: { orderId: id, folio: o.folio }, restaurantId: o.restaurantId });
  await refreshShortages(tx, items.map((i) => i.productId));
  await audit(tx, { userId: user.id, action: 'pedido.despachar', entity: 'order', entityId: id });
});

const finishDelivery = async (tx: Tx, o: OrderRow, user: AuthUser, byRestaurant: boolean) => {
  await tx.query(`UPDATE orders SET delivered_at = COALESCE(delivered_at, now()) WHERE id = $1`, [o.id]);
  await tx.query(`UPDATE deliveries SET status = 'entregada', delivered_at = COALESCE(delivered_at, now()) WHERE order_id = $1`, [o.id]);
  if (byRestaurant) {
    await tx.query(`UPDATE orders SET received_at = now(), received_by = $2 WHERE id = $1`, [o.id, user.id]);
    await tx.query(`UPDATE deliveries SET received_at = now(), received_by = $2 WHERE order_id = $1`, [o.id, user.id]);
  }
};

export const deliverOrder = (id: number, user: AuthUser) => withTx(async (tx) => {
  const o = await lockOrder(tx, id);
  if (user.role === 'entrega') {
    const d = await tx.one<{ assignedTo: number | null }>(`SELECT assigned_to FROM deliveries WHERE order_id = $1`, [id]);
    if (d?.assignedTo && d.assignedTo !== user.id) throw forbidden('Esta entrega está asignada a otra persona');
  }
  await setStatus(tx, o, 'entregado', user, { event: 'entregado' });
  await finishDelivery(tx, o, user, false);
  await notify(tx, { type: 'pedido_estado', title: `Pedido ${o.folio} entregado`, payload: { orderId: id, folio: o.folio }, restaurantId: o.restaurantId });
});

export const confirmReception = (id: number, user: AuthUser) => withTx(async (tx) => {
  const o = await lockOrder(tx, id);
  if (user.restaurantId !== o.restaurantId) throw notFound('Pedido');
  if (o.receivedAt) return; // idempotente
  if (o.status === 'en_entrega') {
    await setStatus(tx, o, 'entregado', user, { event: 'recepcion_confirmada' });
  } else if (o.status === 'entregado') {
    await tx.query(`INSERT INTO order_events (order_id, event, user_id) VALUES ($1,'recepcion_confirmada',$2)`, [id, user.id]);
  } else {
    throw conflict('El pedido aún no ha sido entregado');
  }
  await finishDelivery(tx, o, user, true);
  tx.afterCommit(() => { emitStaff('order:updated', { orderId: id, folio: o.folio, status: 'entregado', previousStatus: 'en_entrega' }); });
});

export const cancelOrder = (id: number, user: AuthUser, reason: string) => withTx(async (tx) => {
  const o = await lockOrder(tx, id);
  if (user.restaurantId !== null) {
    if (user.restaurantId !== o.restaurantId) throw notFound('Pedido');
    if (!['nuevo', 'pendiente_revision'].includes(o.status)) throw conflict('El pedido ya está en preparación; solicita la cancelación al almacén');
  }
  if (o.status === 'en_entrega') throw conflict('El pedido ya salió del almacén y no puede cancelarse');
  assertTransition(o.status, 'cancelado');
  const items = await loadItems(tx, id);
  for (const it of items) await releaseReservation(tx, o, it, it.qtyReserved, user, 'Pedido cancelado');
  await tx.query(`UPDATE orders SET cancel_reason = $2 WHERE id = $1`, [id, reason]);
  await setStatus(tx, o, 'cancelado', user, { note: reason, event: 'cancelado' });
  await refreshShortages(tx, items.map((i) => i.productId));
  const rest = await tx.one<{ name: string }>(`SELECT name FROM restaurants WHERE id = $1`, [o.restaurantId]);
  await notify(tx, { type: 'pedido_cancelado', title: `Pedido ${o.folio} cancelado`, body: `${rest!.name}: ${reason}`,
    payload: { orderId: id, folio: o.folio } });
  if (user.restaurantId === null) {
    await notify(tx, { type: 'pedido_cancelado', title: `Pedido ${o.folio} cancelado`, body: reason,
      payload: { orderId: id, folio: o.folio }, restaurantId: o.restaurantId });
  }
  await audit(tx, { userId: user.id, action: 'pedido.cancelar', entity: 'order', entityId: id, detail: { reason } });
});

// ───────────────────────── Consultas ─────────────────────────

export interface OrderFilter { status?: string; restaurantId?: number; q?: string; roleScope?: AuthUser; limit: number; offset: number }

export async function listOrders(f: OrderFilter) {
  const where: string[] = []; const params: unknown[] = [];
  const add = (sql: string, v: unknown) => { params.push(v); where.push(sql.replace('?', `$${params.length}`)); };
  if (f.status) add('o.status = ANY(string_to_array(?, \',\'))', f.status);
  if (f.restaurantId) add('o.restaurant_id = ?', f.restaurantId);
  if (f.q) add('(o.folio ILIKE ? OR r.name ILIKE ?)', `%${f.q}%`);
  if (f.roleScope?.role === 'entrega') where.push(`o.status IN ('listo_entrega','en_entrega','entregado')`);
  const w = where.length ? `WHERE ${where.join(' AND ')}` : '';
  const total = await db.one<{ n: number }>(
    `SELECT count(*)::int AS n FROM orders o JOIN restaurants r ON r.id = o.restaurant_id ${w}`, params);
  const rows = await db.query(
    `SELECT o.id, o.folio, o.status, o.notes, o.created_at, o.updated_at, o.delivered_at, o.received_at,
            r.id AS restaurant_id, r.name AS restaurant_name, u.full_name AS requested_by_name,
            a.full_name AS assigned_to_name,
            (SELECT count(*)::int FROM order_items i WHERE i.order_id = o.id) AS item_count,
            (SELECT COALESCE(SUM(i.qty_requested),0) FROM order_items i WHERE i.order_id = o.id) AS qty_requested,
            (SELECT COALESCE(SUM(i.qty_delivered),0) FROM order_items i WHERE i.order_id = o.id) AS qty_delivered
       FROM orders o JOIN restaurants r ON r.id = o.restaurant_id JOIN users u ON u.id = o.requested_by
       LEFT JOIN users a ON a.id = o.assigned_to ${w}
      ORDER BY o.created_at DESC LIMIT ${f.limit} OFFSET ${f.offset}`, params);
  return { data: rows, total: total!.n };
}

export async function getOrder(id: number, user: AuthUser) {
  const order = await db.one(
    `SELECT o.id, o.folio, o.status, o.notes, o.warehouse_notes, o.cancel_reason, o.created_at, o.updated_at,
            o.delivered_at, o.received_at, o.restaurant_id, r.name AS restaurant_name, r.address AS restaurant_address,
            u.full_name AS requested_by_name, a.full_name AS assigned_to_name, rb.full_name AS received_by_name
       FROM orders o JOIN restaurants r ON r.id = o.restaurant_id JOIN users u ON u.id = o.requested_by
       LEFT JOIN users a ON a.id = o.assigned_to LEFT JOIN users rb ON rb.id = o.received_by
      WHERE o.id = $1`, [id]);
  if (!order || (user.restaurantId && order.restaurantId !== user.restaurantId)) throw notFound('Pedido');
  const items = await db.query(
    `SELECT oi.id, oi.product_id, oi.sku, oi.product_name, oi.unit_name, oi.unit_price, oi.qty_requested,
            oi.qty_authorized, oi.qty_reserved, oi.qty_prepared, oi.qty_delivered,
            GREATEST(COALESCE(oi.qty_authorized, oi.qty_requested) - oi.qty_reserved, 0) AS qty_unreserved,
            GREATEST(oi.qty_requested - oi.qty_delivered, 0) AS qty_pending,
            (i.on_hand - i.reserved) AS available_now
       FROM order_items oi JOIN inventory i ON i.product_id = oi.product_id WHERE oi.order_id = $1 ORDER BY oi.id`, [id]);
  const events = await db.query(
    `SELECT e.id, e.event, e.from_status, e.to_status, e.note, e.changes, e.created_at, u.full_name AS user_name
       FROM order_events e LEFT JOIN users u ON u.id = e.user_id WHERE e.order_id = $1 ORDER BY e.id`, [id]);
  const delivery = await db.one(
    `SELECT d.status, d.dispatched_at, d.delivered_at, d.received_at, a.full_name AS assigned_to_name
       FROM deliveries d LEFT JOIN users a ON a.id = d.assigned_to WHERE d.order_id = $1`, [id]);
  // El restaurante no ve notas internas del historial de bodega ni existencias
  if (user.restaurantId) {
    const showPrices = (await db.one<{ value: boolean }>(`SELECT value FROM settings WHERE key = 'show_prices'`))?.value === true;
    return { ...order, items: items.map(({ availableNow, qtyReserved, qtyUnreserved, unitPrice, ...rest }) => (showPrices ? { ...rest, unitPrice } : rest)), events: events.filter((e) => e.event !== 'reserva_adicional'), delivery };
  }
  return { ...order, items, events, delivery };
}
