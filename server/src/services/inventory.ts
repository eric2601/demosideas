import { Tx } from '../db';
import { badRequest, conflict } from '../errors';
import { notify } from './notifications';
import { emitStaff } from '../realtime';

export type MovementType =
  | 'entrada_compra' | 'salida_pedido' | 'reserva' | 'liberacion_reserva'
  | 'ajuste_positivo' | 'ajuste_negativo' | 'devolucion' | 'correccion_autorizada';

export interface MovementInput {
  productId: number;
  type: MovementType;
  /** Magnitud positiva del movimiento */
  quantity: number;
  /** Efecto sobre la existencia física */
  deltaOnHand: number;
  /** Efecto sobre la cantidad reservada */
  deltaReserved: number;
  userId: number | null;
  referenceType?: string;
  referenceId?: number;
  referenceFolio?: string;
  reason?: string;
  notes?: string;
}

type StockLevel = 'ok' | 'low' | 'out';
const level = (onHand: number, reserved: number, minStock: number): StockLevel => {
  const available = onHand - reserved;
  if (available <= 0) return 'out';
  return available <= minStock ? 'low' : 'ok';
};

/** Bloquea las filas de inventario (orden fijo por producto para evitar interbloqueos). */
export async function lockInventory(tx: Tx, productIds: number[]) {
  const ids = [...new Set(productIds)].sort((a, b) => a - b);
  const rows = await tx.query<{ productId: number; onHand: number; reserved: number; minStock: number; name: string }>(
    `SELECT i.product_id, i.on_hand, i.reserved, p.min_stock, p.name
       FROM inventory i JOIN products p ON p.id = i.product_id
      WHERE i.product_id = ANY($1::int[]) ORDER BY i.product_id FOR UPDATE OF i`,
    [ids],
  );
  return new Map(rows.map((r) => [r.productId, r]));
}

/**
 * Único punto de escritura de existencias: valida invariantes, actualiza el saldo,
 * registra el movimiento auditable y dispara alertas de agotado / stock mínimo.
 * Debe llamarse dentro de una transacción.
 */
export async function applyMovement(tx: Tx, m: MovementInput) {
  const cur = (await lockInventory(tx, [m.productId])).get(m.productId);
  if (!cur) throw badRequest(`El producto ${m.productId} no tiene registro de inventario`);

  const onHandAfter = round(cur.onHand + m.deltaOnHand);
  const reservedAfter = round(cur.reserved + m.deltaReserved);
  if (onHandAfter < 0) throw conflict(`Existencia insuficiente de "${cur.name}"`);
  if (reservedAfter < 0) throw conflict(`Reserva inconsistente de "${cur.name}"`);
  if (reservedAfter > onHandAfter) {
    throw conflict(`La operación comprometería mercancía ya reservada de "${cur.name}"`);
  }

  await tx.query(
    `UPDATE inventory SET on_hand = $2, reserved = $3, last_movement_at = now() WHERE product_id = $1`,
    [m.productId, onHandAfter, reservedAfter],
  );
  await tx.query(
    `INSERT INTO inventory_movements
       (product_id, type, quantity, delta_on_hand, delta_reserved, on_hand_before, on_hand_after,
        reserved_before, reserved_after, reference_type, reference_id, reference_folio, reason, notes, user_id)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)`,
    [m.productId, m.type, m.quantity, m.deltaOnHand, m.deltaReserved, cur.onHand, onHandAfter,
     cur.reserved, reservedAfter, m.referenceType ?? null, m.referenceId ?? null, m.referenceFolio ?? null,
     m.reason ?? null, m.notes ?? null, m.userId],
  );

  const before = level(cur.onHand, cur.reserved, cur.minStock);
  const after = level(onHandAfter, reservedAfter, cur.minStock);
  if (after !== before && after !== 'ok') {
    const out = after === 'out';
    await notify(tx, {
      type: out ? 'producto_agotado' : 'stock_minimo',
      title: out ? `Producto agotado: ${cur.name}` : `Stock bajo: ${cur.name}`,
      body: `Disponible: ${round(onHandAfter - reservedAfter)} (mínimo ${cur.minStock})`,
      payload: { productId: m.productId },
    });
  }
  tx.afterCommit(() => {
    emitStaff('inventory:changed', { productId: m.productId, onHand: onHandAfter, reserved: reservedAfter });
  });

  return { onHandBefore: cur.onHand, onHandAfter, reservedBefore: cur.reserved, reservedAfter };
}

export const round = (n: number) => Math.round(n * 1000) / 1000;

export async function availableOf(tx: Tx, productId: number): Promise<number> {
  const row = await tx.one<{ available: number }>(
    `SELECT on_hand - reserved AS available FROM inventory WHERE product_id = $1`, [productId]);
  return row?.available ?? 0;
}
