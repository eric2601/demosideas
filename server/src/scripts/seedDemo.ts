import bcrypt from 'bcryptjs';
import { config } from '../config';
import { db, pool } from '../db';
import { AuthUser } from '../middleware/auth';
import { ROLE_PERMISSIONS, RoleCode } from '../permissions';
import * as orders from '../services/orders';
import * as purchases from '../services/purchases';
import { runMigrations } from './migrate';

export const DEMO_PASSWORD = 'Demo-Temporal-2026';

const USERS: { username: string; fullName: string; role: RoleCode; restaurant?: string }[] = [
  { username: 'admin.demo', fullName: 'Administrador (DEMO)', role: 'admin' },
  { username: 'almacen.demo', fullName: 'Encargado de almacén (DEMO)', role: 'almacen' },
  { username: 'compras.demo', fullName: 'Compras (DEMO)', role: 'compras' },
  { username: 'entrega.demo', fullName: 'Repartidor (DEMO)', role: 'entrega' },
  { username: 'rest1.demo', fullName: 'Responsable Restaurante 1 (DEMO)', role: 'restaurante', restaurant: 'RD1' },
  { username: 'rest2.demo', fullName: 'Responsable Restaurante 2 (DEMO)', role: 'restaurante', restaurant: 'RD2' },
];

// [sku, nombre, categoría, unidad, mínimo, costo, precio, existencia inicial]
const PRODUCTS: [string, string, string, string, number, number, number, number][] = [
  ['ABA-001', 'Arroz grano largo 1 kg', 'Abarrotes', 'kg', 20, 28, 34, 80],
  ['ABA-002', 'Frijol negro 1 kg', 'Abarrotes', 'kg', 20, 32, 39, 60],
  ['ABA-003', 'Aceite vegetal 1 L', 'Abarrotes', 'lt', 15, 45, 55, 30],
  ['ABA-004', 'Azúcar estándar 1 kg', 'Abarrotes', 'kg', 15, 24, 29, 12],
  ['ABA-005', 'Sal refinada 1 kg', 'Abarrotes', 'kg', 10, 12, 16, 40],
  ['ABA-006', 'Harina de trigo 1 kg', 'Abarrotes', 'kg', 15, 20, 25, 8],
  ['LAC-001', 'Leche entera 1 L', 'Lácteos y refrigerados', 'lt', 24, 22, 27, 48],
  ['LAC-002', 'Queso Oaxaca 1 kg', 'Lácteos y refrigerados', 'kg', 8, 140, 170, 6],
  ['LAC-003', 'Crema ácida 1 L', 'Lácteos y refrigerados', 'lt', 6, 55, 68, 0],
  ['LIM-001', 'Detergente multiusos 5 L', 'Limpieza', 'pza', 5, 90, 115, 14],
  ['LIM-002', 'Bolsas de basura (paquete 10)', 'Limpieza', 'paq', 10, 35, 45, 25],
  ['LIM-003', 'Servilletas (caja 100 paq.)', 'Limpieza', 'caja', 4, 210, 260, 9],
];

const user = async (username: string): Promise<AuthUser> => {
  const u = await db.one<any>(
    `SELECT u.id, u.username, u.full_name, r.code AS role, u.restaurant_id FROM users u JOIN roles r ON r.id = u.role_id WHERE u.username = $1`, [username]);
  return { ...u, permissions: ROLE_PERMISSIONS[u.role as RoleCode] };
};

async function main() {
  if (config.isProduction && process.env.ALLOW_DEMO_SEED !== 'true') {
    console.error('Los datos de demostración no se cargan en producción. Define ALLOW_DEMO_SEED=true si es intencional.');
    process.exit(1);
  }
  await runMigrations();
  if ((await db.one<{ n: number }>(`SELECT count(*)::int AS n FROM products`))!.n > 0) {
    console.error('La base ya contiene productos; no se cargan datos de demostración.');
    process.exit(1);
  }
  const hash = await bcrypt.hash(DEMO_PASSWORD, config.bcryptRounds);

  for (const [code, name, mgr] of [['RD1', 'Restaurante Demo 1 (prueba)', 'Responsable Demo 1'], ['RD2', 'Restaurante Demo 2 (prueba)', 'Responsable Demo 2']]) {
    await db.query(`INSERT INTO restaurants (code, name, manager_name, address) VALUES ($1,$2,$3,'Dirección ficticia 123')`, [code, name, mgr]);
  }
  for (const u of USERS) {
    await db.query(
      `INSERT INTO users (username, full_name, password_hash, role_id, restaurant_id)
       VALUES ($1,$2,$3,(SELECT id FROM roles WHERE code=$4),(SELECT id FROM restaurants WHERE code=$5))`,
      [u.username, u.fullName, hash, u.role, u.restaurant ?? null]);
  }
  for (const c of new Set(PRODUCTS.map((p) => p[2]))) await db.query(`INSERT INTO categories (name) VALUES ($1)`, [c]);
  for (const [sku, name, cat, unit, min, cost, price] of PRODUCTS) {
    const p = await db.one<{ id: number }>(
      `INSERT INTO products (sku, name, description, category_id, unit_id, min_stock, cost_unit, sale_price)
       VALUES ($1,$2,'DATO DE PRUEBA',(SELECT id FROM categories WHERE name=$3),(SELECT id FROM units WHERE code=$4),$5,$6,$7) RETURNING id`,
      [sku, name, cat, unit, min, cost, price]);
    await db.query(`INSERT INTO inventory (product_id) VALUES ($1)`, [p!.id]);
  }
  const sup = await db.one<{ id: number }>(
    `INSERT INTO suppliers (name, contact, phone) VALUES ('Proveedor Demo (datos de prueba)', 'Contacto ficticio', '000-000-0000') RETURNING id`);

  const admin = await user('admin.demo');
  const almacen = await user('almacen.demo');
  const compras = await user('compras.demo');
  const entrega = await user('entrega.demo');
  const rest1 = await user('rest1.demo');
  const rest2 = await user('rest2.demo');
  const idOf = async (sku: string) => (await db.one<{ id: number }>(`SELECT id FROM products WHERE sku = $1`, [sku]))!.id;

  // Entradas iniciales (generan movimientos históricos reales)
  await purchases.createReceipt(almacen, {
    supplierId: sup!.id, invoiceRef: 'DEMO-0001', notes: 'Inventario inicial de demostración',
    items: await Promise.all(PRODUCTS.filter((p) => p[7] > 0).map(async (p) => ({ productId: await idOf(p[0]), quantity: p[7], unitCost: p[5] }))),
  }, null);

  // Pedido 1: se surte completo y se entrega
  const o1 = await orders.createOrder(rest1, { items: [{ productId: await idOf('ABA-001'), quantity: 10 }, { productId: await idOf('LAC-001'), quantity: 12 }], notes: 'Pedido de demostración', idempotencyKey: 'demo-1' }, null);
  await orders.acceptOrder(o1.orderId, almacen, {});
  await orders.prepareOrder(o1.orderId, almacen, {});
  await orders.dispatchOrder(o1.orderId, almacen, { assignedTo: entrega.id });
  await orders.deliverOrder(o1.orderId, entrega);
  // Pedido 2: excede existencias (crema agotada, harina insuficiente) → surtido parcial y faltante
  await orders.createOrder(rest2, { items: [{ productId: await idOf('LAC-003'), quantity: 5 }, { productId: await idOf('ABA-006'), quantity: 12 }, { productId: await idOf('LIM-002'), quantity: 5 }], notes: 'Pedido con faltantes (demo)', idempotencyKey: 'demo-2' }, null);
  // Pedido 3: nuevo, sin atender
  await orders.createOrder(rest1, { items: [{ productId: await idOf('ABA-003'), quantity: 6 }, { productId: await idOf('LIM-003'), quantity: 2 }], idempotencyKey: 'demo-3' }, null);

  // Orden de compra de demostración contra los faltantes detectados
  const open = await db.query<{ id: number; productId: number; quantityShort: number }>(`SELECT id, product_id, quantity_short FROM shortages WHERE status <> 'resuelto'`);
  const poId = await purchases.createPurchase(compras, {
    supplierId: sup!.id, notes: 'Orden de compra de demostración',
    items: await Promise.all(open.slice(0, 3).map(async (s) => ({ productId: s.productId, quantity: Math.ceil(s.quantityShort), unitCost: (await db.one<{ costUnit: number }>(`SELECT cost_unit FROM products WHERE id=$1`, [s.productId]))!.costUnit }))),
    shortageIds: open.slice(0, 3).map((s) => s.id),
  }, null);
  await purchases.submitPurchase(compras, poId);
  await purchases.authorizePurchase(admin, poId);

  console.log('Datos de demostración cargados (ficticios).');
  console.log(`Usuarios: ${USERS.map((u) => u.username).join(', ')}`);
  console.log(`Contraseña TEMPORAL de todos los usuarios demo: ${DEMO_PASSWORD}`);
}

main().then(() => pool.end()).catch((e) => { console.error(e); process.exit(1); });
