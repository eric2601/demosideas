import { db, Tx, withTx } from '../db';
import { badRequest, conflict, notFound } from '../errors';
import { AuthUser } from '../middleware/auth';
import { applyMovement, round } from './inventory';
import { INCOMING_SQL, nextFolio, ORDER_SHORT_SQL, refreshShortages } from './shortages';
import { notify } from './notifications';
import { audit } from './audit';

export type PurchaseStatus =
  'borrador' | 'pendiente_autorizacion' | 'autorizada' | 'parcialmente_recibida' | 'recibida' | 'cancelada';

/** Sugerencias: faltante real menos lo que ya viene en órdenes de compra vigentes. */
export async function purchaseSuggestions() {
  return db.query(
    `SELECT p.id AS product_id, p.sku, p.name, u.name AS unit_name, i.on_hand, i.reserved,
            (i.on_hand - i.reserved) AS available, p.min_stock, p.cost_unit AS estimated_unit_cost,
            COALESCE(os.order_short, 0) AS order_short,
            GREATEST(COALESCE(os.order_short,0) + p.min_stock - (i.on_hand - i.reserved), 0) AS shortage,
            COALESCE(inc.incoming, 0) AS incoming,
            GREATEST(GREATEST(COALESCE(os.order_short,0) + p.min_stock - (i.on_hand - i.reserved), 0) - COALESCE(inc.incoming,0), 0) AS suggested,
            GREATEST(GREATEST(COALESCE(os.order_short,0) + p.min_stock - (i.on_hand - i.reserved), 0) - COALESCE(inc.incoming,0), 0) * p.cost_unit AS estimated_amount,
            sh.id AS shortage_id, sh.status AS shortage_status,
            (SELECT s.name FROM goods_receipts gr JOIN goods_receipt_items gi ON gi.receipt_id = gr.id
               JOIN suppliers s ON s.id = gr.supplier_id WHERE gi.product_id = p.id ORDER BY gr.received_at DESC LIMIT 1) AS last_supplier,
            (SELECT s.id FROM goods_receipts gr JOIN goods_receipt_items gi ON gi.receipt_id = gr.id
               JOIN suppliers s ON s.id = gr.supplier_id WHERE gi.product_id = p.id ORDER BY gr.received_at DESC LIMIT 1) AS last_supplier_id
       FROM products p JOIN inventory i ON i.product_id = p.id JOIN units u ON u.id = p.unit_id
       LEFT JOIN (${ORDER_SHORT_SQL}) os ON os.product_id = p.id
       LEFT JOIN (${INCOMING_SQL}) inc ON inc.product_id = p.id
       LEFT JOIN shortages sh ON sh.product_id = p.id AND sh.status <> 'resuelto'
      WHERE p.active
        AND (GREATEST(COALESCE(os.order_short,0) + p.min_stock - (i.on_hand - i.reserved), 0) > 0 OR COALESCE(inc.incoming,0) > 0)
      ORDER BY suggested DESC, p.name`);
}

interface POItemInput { productId: number; quantity: number; unitCost: number }
export interface POInput { supplierId: number; items: POItemInput[]; shortageIds?: number[]; expectedDate?: string; notes?: string }

async function writeItems(tx: Tx, poId: number, items: POItemInput[]) {
  const ids = items.map((i) => i.productId);
  if (new Set(ids).size !== ids.length) throw badRequest('Hay productos repetidos en la orden');
  const found = await tx.query<{ id: number; active: boolean }>(`SELECT id, active FROM products WHERE id = ANY($1::int[])`, [ids]);
  if (found.length !== ids.length || found.some((p) => !p.active)) throw badRequest('Algún producto no existe o está inactivo');
  const shortages = await tx.query<{ productId: number; quantityShort: number }>(
    `SELECT product_id, quantity_short FROM shortages WHERE product_id = ANY($1::int[]) AND status <> 'resuelto'`, [ids]);
  const orig = new Map(shortages.map((s) => [s.productId, s.quantityShort]));
  for (const i of items) {
    await tx.query(
      `INSERT INTO purchase_order_items (purchase_order_id, product_id, qty_ordered, unit_cost, original_shortage) VALUES ($1,$2,$3,$4,$5)`,
      [poId, i.productId, i.quantity, i.unitCost, orig.get(i.productId) ?? null]);
  }
}

async function linkShortages(tx: Tx, poId: number, shortageIds: number[]) {
  for (const sid of shortageIds) {
    const s = await tx.one<{ id: number }>(`SELECT id FROM shortages WHERE id = $1 AND status <> 'resuelto' FOR UPDATE`, [sid]);
    if (!s) throw badRequest(`El faltante ${sid} no existe o ya fue resuelto`);
    await tx.query(`INSERT INTO purchase_order_shortages VALUES ($1,$2) ON CONFLICT DO NOTHING`, [poId, sid]);
    await tx.query(`UPDATE shortages SET status = 'en_proceso_compra' WHERE id = $1 AND status IN ('pendiente','en_revision')`, [sid]);
  }
}

export const createPurchase = (user: AuthUser, input: POInput, ip: string | null) => withTx(async (tx) => {
  const sup = await tx.one<{ active: boolean }>(`SELECT active FROM suppliers WHERE id = $1`, [input.supplierId]);
  if (!sup?.active) throw badRequest('Proveedor inválido o inactivo');
  const folio = await nextFolio(tx, 'seq_purchase_folio', 'OC');
  const po = await tx.one<{ id: number }>(
    `INSERT INTO purchase_orders (folio, supplier_id, expected_date, notes, created_by) VALUES ($1,$2,$3,$4,$5) RETURNING id`,
    [folio, input.supplierId, input.expectedDate ?? null, input.notes ?? null, user.id]);
  await writeItems(tx, po!.id, input.items);
  await linkShortages(tx, po!.id, input.shortageIds ?? []);
  await audit(tx, { userId: user.id, action: 'compra.crear', entity: 'purchase_order', entityId: po!.id, ip });
  return po!.id;
});

async function lockPO(tx: Tx, id: number) {
  const po = await tx.one<{ id: number; folio: string; status: PurchaseStatus; supplierId: number }>(
    `SELECT id, folio, status, supplier_id FROM purchase_orders WHERE id = $1 FOR UPDATE`, [id]);
  if (!po) throw notFound('Orden de compra');
  return po;
}

export const updatePurchase = (user: AuthUser, id: number, input: POInput) => withTx(async (tx) => {
  const po = await lockPO(tx, id);
  if (po.status !== 'borrador') throw conflict('Solo se pueden editar órdenes en borrador');
  await tx.query(`UPDATE purchase_orders SET supplier_id = $2, expected_date = $3, notes = $4 WHERE id = $1`,
    [id, input.supplierId, input.expectedDate ?? null, input.notes ?? null]);
  await tx.query(`DELETE FROM purchase_order_items WHERE purchase_order_id = $1`, [id]);
  await writeItems(tx, id, input.items);
  await linkShortages(tx, id, input.shortageIds ?? []);
  await audit(tx, { userId: user.id, action: 'compra.editar', entity: 'purchase_order', entityId: id });
});

export const submitPurchase = (user: AuthUser, id: number) => withTx(async (tx) => {
  const po = await lockPO(tx, id);
  if (po.status !== 'borrador') throw conflict('Solo una orden en borrador puede enviarse a autorización');
  await tx.query(`UPDATE purchase_orders SET status = 'pendiente_autorizacion', submitted_at = now() WHERE id = $1`, [id]);
  await audit(tx, { userId: user.id, action: 'compra.enviar', entity: 'purchase_order', entityId: id });
});

export const authorizePurchase = (user: AuthUser, id: number) => withTx(async (tx) => {
  const po = await lockPO(tx, id);
  if (po.status !== 'pendiente_autorizacion') throw conflict('La orden no está pendiente de autorización');
  await tx.query(`UPDATE purchase_orders SET status = 'autorizada', authorized_by = $2, authorized_at = now() WHERE id = $1`, [id, user.id]);
  await audit(tx, { userId: user.id, action: 'compra.autorizar', entity: 'purchase_order', entityId: id });
});

export const cancelPurchase = (user: AuthUser, id: number, reason: string) => withTx(async (tx) => {
  const po = await lockPO(tx, id);
  if (!['borrador', 'pendiente_autorizacion', 'autorizada'].includes(po.status)) {
    throw conflict('Una orden con recepciones o ya cerrada no puede cancelarse');
  }
  await tx.query(`UPDATE purchase_orders SET status = 'cancelada', cancelled_at = now(), cancel_reason = $2 WHERE id = $1`, [id, reason]);
  // Los faltantes que solo dependían de esta orden regresan a pendiente
  await tx.query(
    `UPDATE shortages s SET status = 'pendiente'
      WHERE s.status = 'en_proceso_compra'
        AND EXISTS (SELECT 1 FROM purchase_order_shortages l WHERE l.shortage_id = s.id AND l.purchase_order_id = $1)
        AND NOT EXISTS (SELECT 1 FROM purchase_order_shortages l JOIN purchase_orders o ON o.id = l.purchase_order_id
                         WHERE l.shortage_id = s.id AND o.id <> $1 AND o.status IN ('borrador','pendiente_autorizacion','autorizada','parcialmente_recibida'))`, [id]);
  await audit(tx, { userId: user.id, action: 'compra.cancelar', entity: 'purchase_order', entityId: id, detail: { reason } });
});

export interface ReceiptItemInput { productId: number; quantity: number; unitCost: number; purchaseOrderItemId?: number }
export interface ReceiptInput { supplierId?: number; purchaseOrderId?: number; invoiceRef?: string; notes?: string; items: ReceiptItemInput[] }

/**
 * Registra una entrada de mercancía (directa o contra una orden de compra, completa o parcial).
 * Es el ÚNICO camino por el que una compra incrementa existencias.
 */
export const createReceipt = (user: AuthUser, input: ReceiptInput, ip: string | null) => withTx(async (tx) => {
  let supplierId = input.supplierId;
  let po: { id: number; folio: string; status: PurchaseStatus; supplierId: number } | null = null;
  if (input.purchaseOrderId) {
    po = await lockPO(tx, input.purchaseOrderId);
    if (!['autorizada', 'parcialmente_recibida'].includes(po.status)) {
      throw conflict('Solo se puede recibir mercancía de órdenes autorizadas o parcialmente recibidas');
    }
    supplierId = po.supplierId;
  }
  if (!supplierId) throw badRequest('Indica el proveedor');
  const sup = await tx.one<{ active: boolean }>(`SELECT active FROM suppliers WHERE id = $1`, [supplierId]);
  if (!sup) throw badRequest('Proveedor inexistente');

  const merged = new Map<number, ReceiptItemInput>();
  for (const it of input.items) {
    if (merged.has(it.productId)) throw badRequest('Hay productos repetidos en la entrada');
    merged.set(it.productId, it);
  }
  const folio = await nextFolio(tx, 'seq_receipt_folio', 'ENT');
  const lineAmount = (it: ReceiptItemInput) => Math.round(it.quantity * it.unitCost * 100) / 100;
  const total = Math.round([...merged.values()].reduce((sum, it) => sum + lineAmount(it), 0) * 100) / 100;
  const receipt = await tx.one<{ id: number }>(
    `INSERT INTO goods_receipts (folio, supplier_id, purchase_order_id, invoice_ref, notes, user_id, total_amount)
     VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id`,
    [folio, supplierId, po?.id ?? null, input.invoiceRef ?? null, input.notes ?? null, user.id, total]);

  const productIds: number[] = [];
  for (const it of [...merged.values()].sort((a, b) => a.productId - b.productId)) {
    const prod = await tx.one<{ name: string; active: boolean }>(`SELECT name, active FROM products WHERE id = $1`, [it.productId]);
    if (!prod) throw badRequest(`Producto ${it.productId} inexistente`);

    let poItemId: number | null = null;
    if (po) {
      const poi = await tx.one<{ id: number; qtyOrdered: number; qtyReceived: number }>(
        `SELECT id, qty_ordered, qty_received FROM purchase_order_items
          WHERE purchase_order_id = $1 AND product_id = $2 FOR UPDATE`, [po.id, it.productId]);
      if (!poi) throw badRequest(`"${prod.name}" no forma parte de la orden ${po.folio}`);
      const pending = round(poi.qtyOrdered - poi.qtyReceived);
      if (it.quantity > pending) throw conflict(`"${prod.name}": se recibe ${it.quantity} pero solo faltan ${pending} por recibir`);
      await tx.query(`UPDATE purchase_order_items SET qty_received = qty_received + $2 WHERE id = $1`, [poi.id, it.quantity]);
      poItemId = poi.id;
    }
    const lineTotal = lineAmount(it);
    await tx.query(
      `INSERT INTO goods_receipt_items (receipt_id, product_id, purchase_order_item_id, quantity, unit_cost, line_total) VALUES ($1,$2,$3,$4,$5,$6)`,
      [receipt!.id, it.productId, poItemId, it.quantity, it.unitCost, lineTotal]);
    await applyMovement(tx, {
      productId: it.productId, type: 'entrada_compra', quantity: it.quantity, deltaOnHand: it.quantity, deltaReserved: 0,
      userId: user.id, referenceType: 'entrada', referenceId: receipt!.id, referenceFolio: folio,
      reason: po ? `Recepción de ${po.folio}` : 'Entrada de mercancía', notes: input.invoiceRef ? `Ref. ${input.invoiceRef}` : undefined,
    });
    await tx.query(`UPDATE products SET cost_unit = $2 WHERE id = $1`, [it.productId, it.unitCost]);
    productIds.push(it.productId);
  }
  if (po) {
    const left = await tx.one<{ pending: number }>(
      `SELECT COALESCE(SUM(qty_ordered - qty_received),0) AS pending FROM purchase_order_items WHERE purchase_order_id = $1`, [po.id]);
    await tx.query(`UPDATE purchase_orders SET status = $2 WHERE id = $1`, [po.id, left!.pending > 0 ? 'parcialmente_recibida' : 'recibida']);
  }
  await refreshShortages(tx, productIds);
  await notify(tx, { type: 'compra_recibida', title: `Entrada ${folio} registrada`,
    body: po ? `Recepción de ${po.folio}` : 'Entrada directa', payload: { receiptId: receipt!.id, folio, purchaseOrderId: po?.id ?? null } });
  await audit(tx, { userId: user.id, action: 'entrada.crear', entity: 'goods_receipt', entityId: receipt!.id, ip });
  return receipt!.id;
});
