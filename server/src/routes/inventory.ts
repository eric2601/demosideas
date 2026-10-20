import { Router } from 'express';
import { z } from 'zod';
import { db, withTx } from '../db';
import { badRequest, notFound } from '../errors';
import { clientIp, requirePermission } from '../middleware/auth';
import { audit } from '../services/audit';
import { applyMovement, lockInventory, MovementType, round } from '../services/inventory';
import { refreshShortage } from '../services/shortages';
import { asyncHandler, idParam, parse, pageParams, qty } from '../util';

export const inventoryRouter = Router();

const INVENTORY_SELECT = `
  SELECT p.id AS product_id, p.sku, p.name, c.name AS category_name, u.name AS unit_name, p.active,
         i.on_hand, i.reserved, (i.on_hand - i.reserved) AS available, p.min_stock,
         i.last_count_qty, i.last_count_difference, i.last_counted_at, i.last_movement_at,
         CASE WHEN i.on_hand - i.reserved <= 0 THEN 'out'
              WHEN i.on_hand - i.reserved <= p.min_stock THEN 'low' ELSE 'ok' END AS stock_status
    FROM inventory i JOIN products p ON p.id = i.product_id JOIN categories c ON c.id = p.category_id JOIN units u ON u.id = p.unit_id`;

inventoryRouter.get('/inventory', requirePermission('inventory.read'), asyncHandler(async (req, res) => {
  const where: string[] = []; const params: unknown[] = [];
  if (req.query.q) { params.push(`%${req.query.q}%`); where.push(`(p.name ILIKE $1 OR p.sku ILIKE $1)`); }
  const rows = await db.query(`${INVENTORY_SELECT} ${where.length ? 'WHERE ' + where.join(' AND ') : ''} ORDER BY p.name`, params);
  res.json(req.query.status ? rows.filter((r) => r.stockStatus === req.query.status) : rows);
}));

inventoryRouter.get('/inventory/:productId', requirePermission('inventory.read'), asyncHandler(async (req, res) => {
  const row = await db.one(`${INVENTORY_SELECT} WHERE p.id = $1`, [idParam(req, 'productId')]);
  if (!row) throw notFound('Producto');
  res.json(row);
}));

const adjustmentSchema = z.object({
  productId: z.number().int().positive(),
  type: z.enum(['ajuste_positivo', 'ajuste_negativo', 'devolucion', 'correccion_autorizada']),
  direction: z.enum(['in', 'out']).optional(),
  quantity: qty,
  reason: z.string().trim().min(3, 'Indica el motivo').max(300),
  notes: z.string().max(500).optional(),
  orderId: z.number().int().positive().optional(),
});

/** Los ajustes son la única vía de corrección: generan movimientos nuevos, nunca editan el historial. */
inventoryRouter.post('/inventory/adjustments', requirePermission('inventory.adjust'), asyncHandler(async (req, res) => {
  const b = parse(adjustmentSchema, req.body);
  let sign = b.type === 'ajuste_negativo' ? -1 : 1;
  if (b.type === 'correccion_autorizada') {
    if (req.user!.role !== 'admin') throw badRequest('Las correcciones autorizadas solo las registra un administrador');
    if (!b.direction) throw badRequest('Indica si la corrección suma (in) o resta (out)');
    sign = b.direction === 'in' ? 1 : -1;
  }
  const result = await withTx(async (tx) => {
    let ref: { folio: string } | null = null;
    if (b.orderId) {
      ref = await tx.one(`SELECT folio FROM orders WHERE id = $1`, [b.orderId]);
      if (!ref) throw badRequest('El pedido relacionado no existe');
    }
    const r = await applyMovement(tx, {
      productId: b.productId, type: b.type as MovementType, quantity: b.quantity,
      deltaOnHand: sign * b.quantity, deltaReserved: 0, userId: req.user!.id,
      referenceType: b.orderId ? 'pedido' : 'ajuste', referenceId: b.orderId, referenceFolio: ref?.folio,
      reason: b.reason, notes: b.notes,
    });
    await refreshShortage(tx, b.productId, sign < 0 ? { userId: req.user!.id } : undefined);
    await audit(tx, { userId: req.user!.id, action: `inventario.${b.type}`, entity: 'product', entityId: b.productId,
      detail: { quantity: b.quantity, sign, reason: b.reason }, ip: clientIp(req) });
    return r;
  });
  res.status(201).json(result);
}));

/** Conteo físico: registra la diferencia contra el sistema y genera el ajuste correspondiente. */
inventoryRouter.post('/inventory/counts', requirePermission('inventory.adjust'), asyncHandler(async (req, res) => {
  const b = parse(z.object({ productId: z.number().int().positive(), countedQuantity: z.number().min(0).max(1_000_000), notes: z.string().max(500).optional() }), req.body);
  const out = await withTx(async (tx) => {
    const cur = (await lockInventory(tx, [b.productId])).get(b.productId);
    if (!cur) throw notFound('Producto');
    const diff = round(b.countedQuantity - cur.onHand);
    if (diff !== 0) {
      await applyMovement(tx, {
        productId: b.productId, type: diff > 0 ? 'ajuste_positivo' : 'ajuste_negativo', quantity: Math.abs(diff),
        deltaOnHand: diff, deltaReserved: 0, userId: req.user!.id, referenceType: 'conteo',
        reason: 'Inventario físico', notes: b.notes,
      });
      if (diff < 0) await refreshShortage(tx, b.productId, { reason: 'inventario_fisico', userId: req.user!.id });
    }
    await tx.query(`UPDATE inventory SET last_count_qty = $2, last_count_difference = $3, last_counted_at = now() WHERE product_id = $1`,
      [b.productId, b.countedQuantity, diff]);
    await audit(tx, { userId: req.user!.id, action: 'inventario.conteo', entity: 'product', entityId: b.productId, detail: { counted: b.countedQuantity, difference: diff }, ip: clientIp(req) });
    return { systemQuantity: cur.onHand, countedQuantity: b.countedQuantity, difference: diff };
  });
  res.status(201).json(out);
}));

inventoryRouter.get('/movements', requirePermission('movements.read'), asyncHandler(async (req, res) => {
  const { page, pageSize, offset } = pageParams(req.query);
  const where: string[] = []; const params: unknown[] = [];
  const add = (sql: string, v: unknown) => { params.push(v); where.push(sql.replace('?', `$${params.length}`)); };
  if (req.query.productId) add('m.product_id = ?', Number(req.query.productId));
  if (req.query.type) add('m.type = ?', String(req.query.type));
  if (req.query.from) add('m.created_at >= ?', String(req.query.from));
  if (req.query.to) add('m.created_at < ?', String(req.query.to));
  if (req.query.q) {
    params.push(`%${req.query.q}%`);
    const n = params.length;
    where.push(`(p.name ILIKE $${n} OR p.sku ILIKE $${n} OR m.reference_folio ILIKE $${n})`);
  }
  const w = where.length ? `WHERE ${where.join(' AND ')}` : '';
  const total = await db.one<{ n: number }>(`SELECT count(*)::int AS n FROM inventory_movements m JOIN products p ON p.id = m.product_id ${w}`, params);
  const data = await db.query(
    `SELECT m.id, m.product_id, p.sku, p.name AS product_name, m.type, m.quantity, m.delta_on_hand, m.delta_reserved,
            m.on_hand_before, m.on_hand_after, m.reserved_before, m.reserved_after, m.reference_type, m.reference_id,
            m.reference_folio, m.reason, m.notes, m.created_at, u.full_name AS user_name
       FROM inventory_movements m JOIN products p ON p.id = m.product_id LEFT JOIN users u ON u.id = m.user_id
       ${w} ORDER BY m.id DESC LIMIT ${pageSize} OFFSET ${offset}`, params);
  res.json({ data, total: total!.n, page, pageSize });
}));
