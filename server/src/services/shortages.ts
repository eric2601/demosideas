import { Tx } from '../db';
import { notFound } from '../errors';
import { round } from './inventory';

export type ShortageReason =
  'pedido_excede' | 'stock_minimo' | 'agotado' | 'inventario_fisico' | 'solicitud_compra' | 'manual';

/** Demanda de pedidos activos que no pudo reservarse, por producto. */
export const ORDER_SHORT_SQL = `
  SELECT oi.product_id, SUM(GREATEST(COALESCE(oi.qty_authorized, oi.qty_requested) - oi.qty_reserved, 0)) AS order_short
    FROM order_items oi JOIN orders o ON o.id = oi.order_id
   WHERE o.status IN ('nuevo','pendiente_revision','en_preparacion','parcialmente_surtido','listo_entrega')
   GROUP BY oi.product_id`;

/** Cantidad por recibir de órdenes de compra vigentes, por producto. */
export const INCOMING_SQL = `
  SELECT poi.product_id, SUM(poi.qty_ordered - poi.qty_received) AS incoming
    FROM purchase_order_items poi JOIN purchase_orders po ON po.id = poi.purchase_order_id
   WHERE po.status IN ('borrador','pendiente_autorizacion','autorizada','parcialmente_recibida')
   GROUP BY poi.product_id`;

interface Calc {
  productId: number; onHand: number; reserved: number; available: number;
  minStock: number; orderShort: number; shortfall: number;
}

/**
 * Faltante = demanda de pedidos sin reservar + stock mínimo - disponible (nunca negativo).
 * Es la misma fórmula que usa el módulo de compras, por lo que ambos módulos son consistentes.
 */
export async function calcShortfall(tx: Tx, productId: number): Promise<Calc | null> {
  const r = await tx.one<Omit<Calc, 'available' | 'shortfall'>>(
    `SELECT p.id AS product_id, i.on_hand, i.reserved, p.min_stock,
            COALESCE((SELECT SUM(GREATEST(COALESCE(oi.qty_authorized, oi.qty_requested) - oi.qty_reserved, 0))
                        FROM order_items oi JOIN orders o ON o.id = oi.order_id
                       WHERE oi.product_id = p.id
                         AND o.status IN ('nuevo','pendiente_revision','en_preparacion','parcialmente_surtido','listo_entrega')), 0) AS order_short
       FROM products p JOIN inventory i ON i.product_id = p.id WHERE p.id = $1`, [productId]);
  if (!r) return null;
  const available = round(r.onHand - r.reserved);
  return { ...r, available, shortfall: round(Math.max(0, r.orderShort + r.minStock - available)) };
}

/**
 * Crea, actualiza o resuelve el faltante abierto del producto. Nunca duplica incidencias abiertas.
 * Se invoca tras cualquier operación que cambie existencias, reservas o demanda.
 */
export async function refreshShortage(tx: Tx, productId: number, hint?: { reason?: ShortageReason; userId?: number | null }) {
  const c = await calcShortfall(tx, productId);
  if (!c) return;
  const open = await tx.one<{ id: number; quantityShort: number; quantityRequired: number; status: string; reason: ShortageReason; priority: string }>(
    `SELECT id, quantity_short, quantity_required, status, reason, priority FROM shortages
      WHERE product_id = $1 AND status <> 'resuelto' FOR UPDATE`, [productId]);

  const autoReason: ShortageReason = c.orderShort > 0 ? 'pedido_excede' : c.available <= 0 ? 'agotado' : 'stock_minimo';
  const priority = c.available <= 0 || c.orderShort > 0 ? 'alta' : c.available <= c.minStock / 2 ? 'media' : 'baja';

  if (!open) {
    if (c.shortfall <= 0) return;
    const folio = await nextFolio(tx, 'seq_shortage_folio', 'FAL');
    await tx.query(
      `INSERT INTO shortages (folio, product_id, quantity_required, on_hand_snapshot, available_snapshot,
                              quantity_short, reason, priority, created_by)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
      [folio, productId, c.shortfall, c.onHand, c.available, c.shortfall, hint?.reason ?? autoReason, priority, hint?.userId ?? null]);
    return;
  }

  if (c.shortfall <= 0) {
    await tx.query(
      `UPDATE shortages SET status = 'resuelto', resolved_at = now(), quantity_short = 0,
              on_hand_snapshot = $2, available_snapshot = $3 WHERE id = $1`,
      [open.id, c.onHand, c.available]);
    return;
  }

  let status = open.status;
  if (c.shortfall < open.quantityShort && ['en_proceso_compra', 'parcialmente_resuelto'].includes(status)) {
    status = 'parcialmente_resuelto';
  }
  const keepReason = ['inventario_fisico', 'manual', 'solicitud_compra'].includes(open.reason);
  await tx.query(
    `UPDATE shortages SET quantity_short = $2, quantity_required = GREATEST(quantity_required, $2),
            on_hand_snapshot = $3, available_snapshot = $4, status = $5,
            reason = $6, priority = CASE WHEN $7 = 'alta' THEN 'alta' ELSE priority END
      WHERE id = $1`,
    [open.id, c.shortfall, c.onHand, c.available, status, keepReason ? open.reason : autoReason, priority]);
}

export async function refreshShortages(tx: Tx, productIds: number[]) {
  for (const id of [...new Set(productIds)].sort((a, b) => a - b)) await refreshShortage(tx, id);
}

export async function nextFolio(tx: Tx, sequence: string, prefix: string) {
  const row = await tx.one<{ n: number }>(`SELECT nextval('${sequence}') AS n`);
  return `${prefix}-${String(row!.n).padStart(6, '0')}`;
}

export async function getProductOrFail(tx: Tx, id: number) {
  const p = await tx.one<{ id: number; name: string; active: boolean }>(`SELECT id, name, active FROM products WHERE id = $1`, [id]);
  if (!p) throw notFound('Producto');
  return p;
}
