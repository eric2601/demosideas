import { Router } from 'express';
import { db } from '../db';
import { requirePermission } from '../middleware/auth';
import { asyncHandler } from '../util';

export const dashboardRouter = Router();

dashboardRouter.get('/dashboard', requirePermission('dashboard.read'), asyncHandler(async (_req, res) => {
  const tz = (await db.one<{ value: string }>(`SELECT value FROM settings WHERE key = 'timezone'`))?.value ?? 'America/Mexico_City';
  const cards = await db.one(
    `SELECT
       (SELECT count(*)::int FROM products) AS total_products,
       (SELECT count(*)::int FROM products p JOIN inventory i ON i.product_id = p.id WHERE p.active AND i.on_hand - i.reserved > 0) AS with_stock,
       (SELECT count(*)::int FROM products p JOIN inventory i ON i.product_id = p.id WHERE p.active AND i.on_hand - i.reserved > 0 AND i.on_hand - i.reserved <= p.min_stock) AS low_stock,
       (SELECT count(*)::int FROM products p JOIN inventory i ON i.product_id = p.id WHERE p.active AND i.on_hand - i.reserved <= 0) AS out_of_stock,
       (SELECT count(*)::int FROM orders WHERE status IN ('nuevo','pendiente_revision')) AS orders_pending,
       (SELECT count(*)::int FROM orders WHERE status IN ('en_preparacion','parcialmente_surtido')) AS orders_preparing,
       (SELECT count(*)::int FROM orders WHERE status = 'listo_entrega') AS orders_ready,
       (SELECT count(*)::int FROM orders WHERE status = 'entregado') AS orders_delivered,
       (SELECT count(*)::int FROM shortages WHERE status <> 'resuelto') AS open_shortages,
       (SELECT count(*)::int FROM purchase_orders WHERE status IN ('borrador','pendiente_autorizacion','autorizada','parcialmente_recibida')) AS pending_purchases,
       (SELECT count(*)::int FROM goods_receipts WHERE (received_at AT TIME ZONE $1)::date = (now() AT TIME ZONE $1)::date) AS receipts_today`, [tz]);
  const recentOrders = await db.query(
    `SELECT o.id, o.folio, o.status, o.created_at, r.name AS restaurant_name,
            (SELECT count(*)::int FROM order_items i WHERE i.order_id = o.id) AS item_count
       FROM orders o JOIN restaurants r ON r.id = o.restaurant_id ORDER BY o.created_at DESC LIMIT 8`);
  const reorder = await db.query(
    `SELECT p.id AS product_id, p.sku, p.name, i.on_hand, i.reserved, (i.on_hand - i.reserved) AS available, p.min_stock,
            CASE WHEN i.on_hand - i.reserved <= 0 THEN 'out' ELSE 'low' END AS stock_status
       FROM products p JOIN inventory i ON i.product_id = p.id WHERE p.active AND i.on_hand - i.reserved <= p.min_stock
      ORDER BY (i.on_hand - i.reserved) ASC, p.name LIMIT 10`);
  const ordersByStatus = await db.query(`SELECT status, count(*)::int AS count FROM orders GROUP BY status ORDER BY status`);
  const movementsByDay = await db.query(
    `SELECT to_char(d.day, 'YYYY-MM-DD') AS day,
            COALESCE(SUM(m.delta_on_hand) FILTER (WHERE m.delta_on_hand > 0), 0) AS entries,
            COALESCE(-SUM(m.delta_on_hand) FILTER (WHERE m.delta_on_hand < 0), 0) AS exits
       FROM generate_series(((now() AT TIME ZONE $1)::date - 6), (now() AT TIME ZONE $1)::date, '1 day') AS d(day)
       LEFT JOIN inventory_movements m ON (m.created_at AT TIME ZONE $1)::date = d.day::date
      GROUP BY d.day ORDER BY d.day`, [tz]);
  res.json({ cards, recentOrders, reorder, ordersByStatus, movementsByDay });
}));
