import { Router } from 'express';
import { z } from 'zod';
import { db, withTx } from '../db';
import { conflict, notFound } from '../errors';
import { clientIp, requirePermission } from '../middleware/auth';
import { audit } from '../services/audit';
import { asyncHandler, idParam, money, parse } from '../util';

export const catalogRouter = Router();

const categorySchema = z.object({ name: z.string().trim().min(1).max(80), description: z.string().max(300).optional(), active: z.boolean().optional() });
const unitSchema = z.object({ code: z.string().trim().min(1).max(12), name: z.string().trim().min(1).max(40), active: z.boolean().optional() });

catalogRouter.get('/categories', asyncHandler(async (_req, res) => {
  res.json(await db.query(`SELECT id, name, description, active FROM categories ORDER BY name`));
}));
catalogRouter.post('/categories', requirePermission('products.write'), asyncHandler(async (req, res) => {
  const b = parse(categorySchema, req.body);
  res.status(201).json(await db.one(`INSERT INTO categories (name, description) VALUES ($1,$2) RETURNING id, name, description, active`, [b.name, b.description ?? null]));
}));
catalogRouter.put('/categories/:id', requirePermission('products.write'), asyncHandler(async (req, res) => {
  const b = parse(categorySchema, req.body);
  const row = await db.one(`UPDATE categories SET name=$2, description=$3, active=COALESCE($4, active) WHERE id=$1 RETURNING id, name, description, active`,
    [idParam(req), b.name, b.description ?? null, b.active ?? null]);
  if (!row) throw notFound('Categoría');
  res.json(row);
}));

catalogRouter.get('/units', asyncHandler(async (_req, res) => {
  res.json(await db.query(`SELECT id, code, name, active FROM units ORDER BY name`));
}));
catalogRouter.post('/units', requirePermission('settings.write'), asyncHandler(async (req, res) => {
  const b = parse(unitSchema, req.body);
  res.status(201).json(await db.one(`INSERT INTO units (code, name) VALUES ($1,$2) RETURNING id, code, name, active`, [b.code, b.name]));
}));
catalogRouter.put('/units/:id', requirePermission('settings.write'), asyncHandler(async (req, res) => {
  const b = parse(unitSchema, req.body);
  const row = await db.one(`UPDATE units SET code=$2, name=$3, active=COALESCE($4, active) WHERE id=$1 RETURNING id, code, name, active`,
    [idParam(req), b.code, b.name, b.active ?? null]);
  if (!row) throw notFound('Unidad');
  res.json(row);
}));

// ───────── Productos ─────────
const productSchema = z.object({
  sku: z.string().trim().min(1).max(40),
  name: z.string().trim().min(1).max(120),
  description: z.string().max(500).optional(),
  categoryId: z.number().int().positive(),
  unitId: z.number().int().positive(),
  minStock: z.number().min(0).max(1_000_000),
  costUnit: money.default(0),
  salePrice: money.nullable().optional(),
  active: z.boolean().optional(),
});

export const PRODUCT_SELECT = `
  SELECT p.id, p.sku, p.name, p.description, p.category_id, c.name AS category_name, p.unit_id, u.name AS unit_name, u.code AS unit_code,
         p.min_stock, p.cost_unit, p.sale_price, p.active, p.created_at, p.updated_at,
         i.on_hand, i.reserved, (i.on_hand - i.reserved) AS available,
         CASE WHEN i.on_hand - i.reserved <= 0 THEN 'out'
              WHEN i.on_hand - i.reserved <= p.min_stock THEN 'low' ELSE 'ok' END AS stock_status
    FROM products p JOIN categories c ON c.id = p.category_id JOIN units u ON u.id = p.unit_id
    JOIN inventory i ON i.product_id = p.id`;

catalogRouter.get('/products', requirePermission('products.read', 'inventory.read'), asyncHandler(async (req, res) => {
  const where: string[] = []; const params: unknown[] = [];
  const add = (sql: string, v: unknown) => { params.push(v); where.push(sql.replace('?', `$${params.length}`)); };
  if (req.query.q) add('(p.name ILIKE ? OR p.sku ILIKE ?)', `%${req.query.q}%`);
  if (req.query.categoryId) add('p.category_id = ?', Number(req.query.categoryId));
  if (req.query.active === 'true' || req.query.active === 'false') add('p.active = ?', req.query.active === 'true');
  if (['low', 'out', 'ok'].includes(String(req.query.stock))) {
    where.push(`(CASE WHEN i.on_hand - i.reserved <= 0 THEN 'out' WHEN i.on_hand - i.reserved <= p.min_stock THEN 'low' ELSE 'ok' END) = '${req.query.stock}'`);
  }
  res.json(await db.query(`${PRODUCT_SELECT} ${where.length ? 'WHERE ' + where.join(' AND ') : ''} ORDER BY p.name`, params));
}));

catalogRouter.get('/products/:id', requirePermission('products.read', 'inventory.read'), asyncHandler(async (req, res) => {
  const row = await db.one(`${PRODUCT_SELECT} WHERE p.id = $1`, [idParam(req)]);
  if (!row) throw notFound('Producto');
  res.json(row);
}));

catalogRouter.post('/products', requirePermission('products.write'), asyncHandler(async (req, res) => {
  const b = parse(productSchema, req.body);
  const id = await withTx(async (tx) => {
    const p = await tx.one<{ id: number }>(
      `INSERT INTO products (sku, name, description, category_id, unit_id, min_stock, cost_unit, sale_price, active)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,COALESCE($9,true)) RETURNING id`,
      [b.sku, b.name, b.description ?? null, b.categoryId, b.unitId, b.minStock, b.costUnit, b.salePrice ?? null, b.active ?? null]);
    await tx.query(`INSERT INTO inventory (product_id) VALUES ($1)`, [p!.id]);
    await audit(tx, { userId: req.user!.id, action: 'producto.crear', entity: 'product', entityId: p!.id, ip: clientIp(req) });
    return p!.id;
  });
  res.status(201).json(await db.one(`${PRODUCT_SELECT} WHERE p.id = $1`, [id]));
}));

catalogRouter.put('/products/:id', requirePermission('products.write'), asyncHandler(async (req, res) => {
  const b = parse(productSchema, req.body);
  const id = idParam(req);
  const row = await db.one(
    `UPDATE products SET sku=$2, name=$3, description=$4, category_id=$5, unit_id=$6, min_stock=$7, cost_unit=$8, sale_price=$9,
            active=COALESCE($10, active) WHERE id=$1 RETURNING id`,
    [id, b.sku, b.name, b.description ?? null, b.categoryId, b.unitId, b.minStock, b.costUnit, b.salePrice ?? null, b.active ?? null]);
  if (!row) throw notFound('Producto');
  await audit(db, { userId: req.user!.id, action: 'producto.editar', entity: 'product', entityId: id, ip: clientIp(req) });
  res.json(await db.one(`${PRODUCT_SELECT} WHERE p.id = $1`, [id]));
}));

for (const [path, active] of [['activate', true], ['deactivate', false]] as const) {
  catalogRouter.post(`/products/:id/${path}`, requirePermission('products.write'), asyncHandler(async (req, res) => {
    const id = idParam(req);
    if (!(await db.one(`UPDATE products SET active = $2 WHERE id = $1 RETURNING id`, [id, active]))) throw notFound('Producto');
    await audit(db, { userId: req.user!.id, action: `producto.${path}`, entity: 'product', entityId: id, ip: clientIp(req) });
    res.json(await db.one(`${PRODUCT_SELECT} WHERE p.id = $1`, [id]));
  }));
}

catalogRouter.delete('/products/:id', requirePermission('products.write'), asyncHandler(async (req, res) => {
  const id = idParam(req);
  const used = await db.one(
    `SELECT 1 FROM inventory_movements WHERE product_id = $1
     UNION ALL SELECT 1 FROM order_items WHERE product_id = $1
     UNION ALL SELECT 1 FROM purchase_order_items WHERE product_id = $1
     UNION ALL SELECT 1 FROM shortages WHERE product_id = $1 LIMIT 1`, [id]);
  if (used) throw conflict('El producto tiene movimientos o documentos relacionados; desactívalo en lugar de eliminarlo');
  await withTx(async (tx) => {
    await tx.query(`DELETE FROM inventory WHERE product_id = $1`, [id]);
    await tx.query(`DELETE FROM restaurant_product_access WHERE product_id = $1`, [id]);
    if (!(await tx.one(`DELETE FROM products WHERE id = $1 RETURNING id`, [id]))) throw notFound('Producto');
    await audit(tx, { userId: req.user!.id, action: 'producto.eliminar', entity: 'product', entityId: id, ip: clientIp(req) });
  });
  res.status(204).end();
}));

// Catálogo para restaurantes: solo productos activos y autorizados, con la disponibilidad confirmada por el servidor
catalogRouter.get('/restaurant-catalog', requirePermission('catalog.read'), asyncHandler(async (req, res) => {
  const showPrices = (await db.one<{ value: boolean }>(`SELECT value FROM settings WHERE key = 'show_prices'`))?.value === true;
  const rows = await db.query<any>(
    `SELECT p.id, p.sku, p.name, p.description, c.id AS category_id, c.name AS category_name, u.name AS unit_name, p.sale_price,
            GREATEST(i.on_hand - i.reserved, 0) AS available,
            CASE WHEN i.on_hand - i.reserved <= 0 THEN 'out' WHEN i.on_hand - i.reserved <= p.min_stock THEN 'low' ELSE 'ok' END AS stock_status
       FROM products p JOIN categories c ON c.id = p.category_id JOIN units u ON u.id = p.unit_id JOIN inventory i ON i.product_id = p.id
      WHERE p.active AND c.active
        AND (NOT EXISTS (SELECT 1 FROM restaurant_product_access a WHERE a.restaurant_id = $1)
             OR EXISTS (SELECT 1 FROM restaurant_product_access a WHERE a.restaurant_id = $1 AND a.product_id = p.id))
      ORDER BY c.name, p.name`, [req.user!.restaurantId]);
  res.json(rows.map(({ salePrice, ...r }) => (showPrices ? { ...r, price: salePrice } : r)));
}));
