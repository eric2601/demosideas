import bcrypt from 'bcryptjs';
import request from 'supertest';
import { createApp } from '../src/app';
import { db } from '../src/db';

export const app = createApp();
export const PASSWORD = 'Prueba-1234';

export async function resetData() {
  await db.query(`TRUNCATE TABLE notification_reads, notifications, order_events, deliveries, order_items, orders,
    purchase_order_shortages, shortages, goods_receipt_items, goods_receipts, purchase_order_items, purchase_orders, suppliers,
    inventory_movements, inventory, restaurant_product_access, products, categories, audit_logs, users, restaurants RESTART IDENTITY CASCADE`);
  for (const s of ['seq_order_folio', 'seq_receipt_folio', 'seq_purchase_folio', 'seq_shortage_folio']) await db.query(`ALTER SEQUENCE ${s} RESTART`);
}

export async function seedBasics() {
  await resetData();
  const hash = await bcrypt.hash(PASSWORD, 4);
  await db.query(`INSERT INTO restaurants (code, name, manager_name) VALUES ('R1','Rest 1','M1'),('R2','Rest 2','M2')`);
  const mk = (u: string, role: string, rest: string | null) =>
    db.query(`INSERT INTO users (username, full_name, password_hash, role_id, restaurant_id)
              VALUES ($1,$1,$2,(SELECT id FROM roles WHERE code=$3),(SELECT id FROM restaurants WHERE code=$4))`, [u, hash, role, rest]);
  await mk('admin', 'admin', null); await mk('almacen', 'almacen', null); await mk('compras', 'compras', null);
  await mk('entrega', 'entrega', null); await mk('r1', 'restaurante', 'R1'); await mk('r2', 'restaurante', 'R2');
  await db.query(`INSERT INTO categories (name) VALUES ('Cat')`);
  await db.query(`INSERT INTO suppliers (name) VALUES ('Prov')`);
}

export async function login(username: string) {
  const res = await request(app).post('/api/auth/login').send({ username, password: PASSWORD });
  if (res.status !== 200) throw new Error(`login ${username}: ${res.status}`);
  return res.body.token as string;
}
export const as = (token: string) => ({
  get: (url: string) => request(app).get(url).set('Authorization', `Bearer ${token}`),
  post: (url: string, body: object = {}, headers: Record<string, string> = {}) => request(app).post(url).set('Authorization', `Bearer ${token}`).set(headers).send(body),
  put: (url: string, body: object = {}) => request(app).put(url).set('Authorization', `Bearer ${token}`).send(body),
  patch: (url: string, body: object = {}) => request(app).patch(url).set('Authorization', `Bearer ${token}`).send(body),
  delete: (url: string) => request(app).delete(url).set('Authorization', `Bearer ${token}`),
});

export async function createProduct(api: ReturnType<typeof as>, sku: string, minStock = 0) {
  const res = await api.post('/api/products', { sku, name: `Producto ${sku}`, categoryId: 1, unitId: 1, minStock, costUnit: 10 });
  if (res.status !== 201) throw new Error(JSON.stringify(res.body));
  return res.body.id as number;
}
export async function stockUp(api: ReturnType<typeof as>, productId: number, quantity: number) {
  const res = await api.post('/api/receipts', { supplierId: 1, items: [{ productId, quantity, unitCost: 10 }] });
  if (res.status !== 201) throw new Error(JSON.stringify(res.body));
}
export const inv = async (productId: number) =>
  (await db.one<{ onHand: number; reserved: number }>(`SELECT on_hand, reserved FROM inventory WHERE product_id = $1`, [productId]))!;
