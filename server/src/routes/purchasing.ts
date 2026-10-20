import { Router } from 'express';
import { z } from 'zod';
import { db, withTx } from '../db';
import { badRequest, conflict, notFound } from '../errors';
import { clientIp, requirePermission } from '../middleware/auth';
import { audit } from '../services/audit';
import { nextFolio, refreshShortage } from '../services/shortages';
import * as purchases from '../services/purchases';
import { asyncHandler, idParam, money, pageParams, parse, qty } from '../util';

export const purchasingRouter = Router();

// ───────── Proveedores ─────────
const supplierSchema = z.object({
  name: z.string().trim().min(1).max(120), contact: z.string().max(120).optional(), phone: z.string().max(30).optional(),
  email: z.string().email().max(120).optional().or(z.literal('')), address: z.string().max(250).optional(), active: z.boolean().optional(),
});
purchasingRouter.get('/suppliers', requirePermission('suppliers.read'), asyncHandler(async (_req, res) => {
  res.json(await db.query(`SELECT id, name, contact, phone, email, address, active FROM suppliers ORDER BY name`));
}));
purchasingRouter.post('/suppliers', requirePermission('suppliers.write'), asyncHandler(async (req, res) => {
  const b = parse(supplierSchema, req.body);
  res.status(201).json(await db.one(
    `INSERT INTO suppliers (name, contact, phone, email, address) VALUES ($1,$2,$3,$4,$5) RETURNING id, name, contact, phone, email, address, active`,
    [b.name, b.contact ?? null, b.phone ?? null, b.email || null, b.address ?? null]));
}));
purchasingRouter.put('/suppliers/:id', requirePermission('suppliers.write'), asyncHandler(async (req, res) => {
  const b = parse(supplierSchema, req.body);
  const row = await db.one(
    `UPDATE suppliers SET name=$2, contact=$3, phone=$4, email=$5, address=$6, active=COALESCE($7, active) WHERE id=$1
     RETURNING id, name, contact, phone, email, address, active`,
    [idParam(req), b.name, b.contact ?? null, b.phone ?? null, b.email || null, b.address ?? null, b.active ?? null]);
  if (!row) throw notFound('Proveedor');
  res.json(row);
}));

// ───────── Faltantes ─────────
const SHORTAGE_SELECT = `
  SELECT s.id, s.folio, s.product_id, p.sku, p.name AS product_name, un.name AS unit_name, s.quantity_required, s.on_hand_snapshot,
         s.available_snapshot, s.quantity_short, s.reason, s.priority, s.status, s.notes, s.detected_at, s.resolved_at,
         i.on_hand AS current_on_hand, (i.on_hand - i.reserved) AS current_available,
         COALESCE((SELECT json_agg(json_build_object('id', po.id, 'folio', po.folio, 'status', po.status))
                     FROM purchase_order_shortages l JOIN purchase_orders po ON po.id = l.purchase_order_id WHERE l.shortage_id = s.id), '[]') AS purchase_orders
    FROM shortages s JOIN products p ON p.id = s.product_id JOIN units un ON un.id = p.unit_id JOIN inventory i ON i.product_id = s.product_id`;

purchasingRouter.get('/shortages', requirePermission('shortages.read'), asyncHandler(async (req, res) => {
  const where: string[] = []; const params: unknown[] = [];
  if (req.query.status === 'open') where.push(`s.status <> 'resuelto'`);
  else if (req.query.status) { params.push(String(req.query.status)); where.push(`s.status = $${params.length}`); }
  if (req.query.priority) { params.push(String(req.query.priority)); where.push(`s.priority = $${params.length}`); }
  res.json(await db.query(
    `${SHORTAGE_SELECT} ${where.length ? 'WHERE ' + where.join(' AND ') : ''}
      ORDER BY (s.status = 'resuelto'), CASE s.priority WHEN 'alta' THEN 0 WHEN 'media' THEN 1 ELSE 2 END, s.detected_at DESC`, params));
}));

purchasingRouter.get('/shortages/:id', requirePermission('shortages.read'), asyncHandler(async (req, res) => {
  const row = await db.one(`${SHORTAGE_SELECT} WHERE s.id = $1`, [idParam(req)]);
  if (!row) throw notFound('Faltante');
  res.json(row);
}));

/** Alta manual; si ya hay un faltante abierto del producto no se duplica: se actualiza el existente. */
purchasingRouter.post('/shortages', requirePermission('shortages.write'), asyncHandler(async (req, res) => {
  const b = parse(z.object({
    productId: z.number().int().positive(), quantityRequired: qty, priority: z.enum(['baja', 'media', 'alta']).default('media'),
    reason: z.enum(['solicitud_compra', 'inventario_fisico', 'manual']).default('manual'), notes: z.string().max(500).optional(),
  }), req.body);
  const out = await withTx(async (tx) => {
    const inv = await tx.one<{ onHand: number; reserved: number }>(`SELECT on_hand, reserved FROM inventory WHERE product_id = $1`, [b.productId]);
    if (!inv) throw badRequest('Producto inexistente');
    const open = await tx.one<{ id: number }>(`SELECT id FROM shortages WHERE product_id = $1 AND status <> 'resuelto' FOR UPDATE`, [b.productId]);
    if (open) {
      await tx.query(`UPDATE shortages SET quantity_required = GREATEST(quantity_required, $2), quantity_short = GREATEST(quantity_short, $2),
                      priority = CASE WHEN $3 = 'alta' THEN 'alta' ELSE priority END, notes = COALESCE($4, notes) WHERE id = $1`,
        [open.id, b.quantityRequired, b.priority, b.notes ?? null]);
      return { id: open.id, duplicate: true };
    }
    const folio = await nextFolio(tx, 'seq_shortage_folio', 'FAL');
    const row = await tx.one<{ id: number }>(
      `INSERT INTO shortages (folio, product_id, quantity_required, on_hand_snapshot, available_snapshot, quantity_short, reason, priority, notes, created_by)
       VALUES ($1,$2,$3,$4,$5,$3,$6,$7,$8,$9) RETURNING id`,
      [folio, b.productId, b.quantityRequired, inv.onHand, inv.onHand - inv.reserved, b.reason, b.priority, b.notes ?? null, req.user!.id]);
    await audit(tx, { userId: req.user!.id, action: 'faltante.crear', entity: 'shortage', entityId: row!.id, ip: clientIp(req) });
    return { id: row!.id, duplicate: false };
  });
  res.status(out.duplicate ? 200 : 201).json(await db.one(`${SHORTAGE_SELECT} WHERE s.id = $1`, [out.id]));
}));

purchasingRouter.patch('/shortages/:id', requirePermission('shortages.write'), asyncHandler(async (req, res) => {
  const b = parse(z.object({
    status: z.enum(['pendiente', 'en_revision', 'en_proceso_compra', 'parcialmente_resuelto', 'resuelto']).optional(),
    priority: z.enum(['baja', 'media', 'alta']).optional(), notes: z.string().max(500).optional(),
  }), req.body);
  const id = idParam(req);
  await withTx(async (tx) => {
    const cur = await tx.one<{ status: string }>(`SELECT status FROM shortages WHERE id = $1 FOR UPDATE`, [id]);
    if (!cur) throw notFound('Faltante');
    if (cur.status === 'resuelto') throw conflict('El faltante ya está resuelto');
    await tx.query(
      `UPDATE shortages SET status = COALESCE($2, status), priority = COALESCE($3, priority), notes = COALESCE($4, notes),
              resolved_at = CASE WHEN $2 = 'resuelto' THEN now() ELSE resolved_at END WHERE id = $1`,
      [id, b.status ?? null, b.priority ?? null, b.notes ?? null]);
    await audit(tx, { userId: req.user!.id, action: 'faltante.actualizar', entity: 'shortage', entityId: id, detail: b, ip: clientIp(req) });
  });
  res.json(await db.one(`${SHORTAGE_SELECT} WHERE s.id = $1`, [id]));
}));

purchasingRouter.post('/shortages/refresh', requirePermission('shortages.write'), asyncHandler(async (_req, res) => {
  await withTx(async (tx) => {
    const ids = await tx.query<{ id: number }>(`SELECT id FROM products WHERE active ORDER BY id`);
    for (const p of ids) await refreshShortage(tx, p.id);
  });
  res.status(204).end();
}));

// ───────── Compras ─────────
const poSchema = z.object({
  supplierId: z.number().int().positive(),
  items: z.array(z.object({ productId: z.number().int().positive(), quantity: qty, unitCost: money })).min(1).max(200),
  shortageIds: z.array(z.number().int().positive()).optional(),
  expectedDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
  notes: z.string().max(500).optional(),
});

purchasingRouter.get('/purchases/suggestions', requirePermission('purchases.read'), asyncHandler(async (_req, res) => {
  res.json(await purchases.purchaseSuggestions());
}));

const PO_SELECT = `
  SELECT po.id, po.folio, po.status, po.supplier_id, s.name AS supplier_name, po.expected_date, po.notes, po.created_at,
         po.submitted_at, po.authorized_at, po.cancelled_at, po.cancel_reason, cu.full_name AS created_by_name, au.full_name AS authorized_by_name,
         (SELECT COALESCE(SUM(qty_ordered * unit_cost),0) FROM purchase_order_items WHERE purchase_order_id = po.id) AS total_amount,
         (SELECT COALESCE(SUM(qty_ordered),0) FROM purchase_order_items WHERE purchase_order_id = po.id) AS qty_ordered,
         (SELECT COALESCE(SUM(qty_received),0) FROM purchase_order_items WHERE purchase_order_id = po.id) AS qty_received
    FROM purchase_orders po JOIN suppliers s ON s.id = po.supplier_id JOIN users cu ON cu.id = po.created_by LEFT JOIN users au ON au.id = po.authorized_by`;

purchasingRouter.get('/purchases', requirePermission('purchases.read'), asyncHandler(async (req, res) => {
  const { page, pageSize, offset } = pageParams(req.query);
  const params: unknown[] = []; let w = '';
  if (req.query.status) { params.push(String(req.query.status)); w = `WHERE po.status = $1`; }
  const total = await db.one<{ n: number }>(`SELECT count(*)::int AS n FROM purchase_orders po ${w}`, params);
  res.json({ data: await db.query(`${PO_SELECT} ${w} ORDER BY po.id DESC LIMIT ${pageSize} OFFSET ${offset}`, params), total: total!.n, page, pageSize });
}));

purchasingRouter.get('/purchases/:id', requirePermission('purchases.read'), asyncHandler(async (req, res) => {
  const id = idParam(req);
  const po = await db.one(`${PO_SELECT} WHERE po.id = $1`, [id]);
  if (!po) throw notFound('Orden de compra');
  const items = await db.query(
    `SELECT i.id, i.product_id, p.sku, p.name AS product_name, un.name AS unit_name, i.qty_ordered, i.qty_received,
            (i.qty_ordered - i.qty_received) AS qty_pending, i.unit_cost, i.original_shortage, (i.qty_ordered * i.unit_cost) AS line_total
       FROM purchase_order_items i JOIN products p ON p.id = i.product_id JOIN units un ON un.id = p.unit_id
      WHERE i.purchase_order_id = $1 ORDER BY i.id`, [id]);
  const shortages = await db.query(
    `SELECT s.id, s.folio, s.status, p.name AS product_name FROM purchase_order_shortages l
       JOIN shortages s ON s.id = l.shortage_id JOIN products p ON p.id = s.product_id WHERE l.purchase_order_id = $1`, [id]);
  const receipts = await db.query(
    `SELECT r.id, r.folio, r.received_at, r.invoice_ref, r.total_amount FROM goods_receipts r WHERE r.purchase_order_id = $1 ORDER BY r.id`, [id]);
  res.json({ ...po, items, shortages, receipts });
}));

purchasingRouter.post('/purchases', requirePermission('purchases.write'), asyncHandler(async (req, res) => {
  const id = await purchases.createPurchase(req.user!, parse(poSchema, req.body), clientIp(req));
  res.status(201).json(await db.one(`${PO_SELECT} WHERE po.id = $1`, [id]));
}));
purchasingRouter.put('/purchases/:id', requirePermission('purchases.write'), asyncHandler(async (req, res) => {
  const id = idParam(req);
  await purchases.updatePurchase(req.user!, id, parse(poSchema, req.body));
  res.json(await db.one(`${PO_SELECT} WHERE po.id = $1`, [id]));
}));
purchasingRouter.post('/purchases/:id/submit', requirePermission('purchases.write'), asyncHandler(async (req, res) => {
  await purchases.submitPurchase(req.user!, idParam(req)); res.status(204).end();
}));
purchasingRouter.post('/purchases/:id/authorize', requirePermission('purchases.authorize'), asyncHandler(async (req, res) => {
  await purchases.authorizePurchase(req.user!, idParam(req)); res.status(204).end();
}));
purchasingRouter.post('/purchases/:id/cancel', requirePermission('purchases.write', 'purchases.authorize'), asyncHandler(async (req, res) => {
  const { reason } = parse(z.object({ reason: z.string().trim().min(3).max(300) }), req.body);
  await purchases.cancelPurchase(req.user!, idParam(req), reason); res.status(204).end();
}));
purchasingRouter.post('/purchases/:id/receive', requirePermission('receipts.create'), asyncHandler(async (req, res) => {
  const b = parse(z.object({
    invoiceRef: z.string().max(60).optional(), notes: z.string().max(500).optional(),
    items: z.array(z.object({ productId: z.number().int().positive(), quantity: qty, unitCost: money })).min(1),
  }), req.body);
  const receiptId = await purchases.createReceipt(req.user!, { ...b, purchaseOrderId: idParam(req) }, clientIp(req));
  res.status(201).json({ receiptId });
}));

// ───────── Entradas de mercancía ─────────
purchasingRouter.get('/receipts', requirePermission('receipts.read'), asyncHandler(async (req, res) => {
  const { page, pageSize, offset } = pageParams(req.query);
  const total = await db.one<{ n: number }>(`SELECT count(*)::int AS n FROM goods_receipts`);
  const data = await db.query(
    `SELECT r.id, r.folio, r.received_at, r.invoice_ref, r.total_amount, r.notes, s.name AS supplier_name, u.full_name AS user_name,
            po.folio AS purchase_folio, (SELECT count(*)::int FROM goods_receipt_items i WHERE i.receipt_id = r.id) AS item_count
       FROM goods_receipts r JOIN suppliers s ON s.id = r.supplier_id JOIN users u ON u.id = r.user_id
       LEFT JOIN purchase_orders po ON po.id = r.purchase_order_id ORDER BY r.id DESC LIMIT ${pageSize} OFFSET ${offset}`);
  res.json({ data, total: total!.n, page, pageSize });
}));
purchasingRouter.get('/receipts/:id', requirePermission('receipts.read'), asyncHandler(async (req, res) => {
  const id = idParam(req);
  const r = await db.one(
    `SELECT r.id, r.folio, r.received_at, r.invoice_ref, r.total_amount, r.notes, s.name AS supplier_name, u.full_name AS user_name, po.folio AS purchase_folio
       FROM goods_receipts r JOIN suppliers s ON s.id = r.supplier_id JOIN users u ON u.id = r.user_id
       LEFT JOIN purchase_orders po ON po.id = r.purchase_order_id WHERE r.id = $1`, [id]);
  if (!r) throw notFound('Entrada');
  const items = await db.query(
    `SELECT i.product_id, p.sku, p.name AS product_name, i.quantity, i.unit_cost, i.line_total
       FROM goods_receipt_items i JOIN products p ON p.id = i.product_id WHERE i.receipt_id = $1 ORDER BY i.id`, [id]);
  res.json({ ...r, items });
}));
purchasingRouter.post('/receipts', requirePermission('receipts.create'), asyncHandler(async (req, res) => {
  const b = parse(z.object({
    supplierId: z.number().int().positive(), invoiceRef: z.string().max(60).optional(), notes: z.string().max(500).optional(),
    items: z.array(z.object({ productId: z.number().int().positive(), quantity: qty, unitCost: money })).min(1).max(200),
  }), req.body);
  const id = await purchases.createReceipt(req.user!, b, clientIp(req));
  res.status(201).json({ receiptId: id });
}));
