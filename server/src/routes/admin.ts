import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { config } from '../config';
import { db, withTx } from '../db';
import { badRequest, notFound } from '../errors';
import { clientIp, requirePermission } from '../middleware/auth';
import { audit } from '../services/audit';
import { asyncHandler, idParam, pageParams, parse } from '../util';
import { passwordSchema } from './auth';

export const adminRouter = Router();

// ───────── Restaurantes ─────────
const restaurantSchema = z.object({
  code: z.string().trim().min(1).max(20), name: z.string().trim().min(1).max(120), address: z.string().max(250).optional(),
  managerName: z.string().trim().min(1).max(120), phone: z.string().max(30).optional(), active: z.boolean().optional(),
});
const RESTAURANT_SELECT = `
  SELECT r.id, r.code, r.name, r.address, r.manager_name, r.phone, r.active, r.created_at,
         (SELECT string_agg(u.username, ', ' ORDER BY u.username) FROM users u WHERE u.restaurant_id = r.id) AS usernames
    FROM restaurants r`;

adminRouter.get('/restaurants', requirePermission('restaurants.read'), asyncHandler(async (_req, res) => {
  res.json(await db.query(`${RESTAURANT_SELECT} ORDER BY r.name`));
}));
adminRouter.post('/restaurants', requirePermission('restaurants.write'), asyncHandler(async (req, res) => {
  const b = parse(restaurantSchema.extend({
    user: z.object({ username: z.string().trim().min(3).max(40).regex(/^[a-zA-Z0-9._-]+$/), fullName: z.string().trim().min(1).max(120), password: passwordSchema }).optional(),
  }), req.body);
  const id = await withTx(async (tx) => {
    const r = await tx.one<{ id: number }>(
      `INSERT INTO restaurants (code, name, address, manager_name, phone) VALUES ($1,$2,$3,$4,$5) RETURNING id`,
      [b.code, b.name, b.address ?? null, b.managerName, b.phone ?? null]);
    if (b.user) {
      await tx.query(
        `INSERT INTO users (username, full_name, password_hash, role_id, restaurant_id)
         VALUES ($1,$2,$3,(SELECT id FROM roles WHERE code = 'restaurante'),$4)`,
        [b.user.username, b.user.fullName, await bcrypt.hash(b.user.password, config.bcryptRounds), r!.id]);
    }
    await audit(tx, { userId: req.user!.id, action: 'restaurante.crear', entity: 'restaurant', entityId: r!.id, ip: clientIp(req) });
    return r!.id;
  });
  res.status(201).json(await db.one(`${RESTAURANT_SELECT} WHERE r.id = $1`, [id]));
}));
adminRouter.put('/restaurants/:id', requirePermission('restaurants.write'), asyncHandler(async (req, res) => {
  const b = parse(restaurantSchema, req.body);
  const id = idParam(req);
  if (!(await db.one(
    `UPDATE restaurants SET code=$2, name=$3, address=$4, manager_name=$5, phone=$6, active=COALESCE($7, active) WHERE id=$1 RETURNING id`,
    [id, b.code, b.name, b.address ?? null, b.managerName, b.phone ?? null, b.active ?? null]))) throw notFound('Restaurante');
  await audit(db, { userId: req.user!.id, action: 'restaurante.editar', entity: 'restaurant', entityId: id, ip: clientIp(req) });
  res.json(await db.one(`${RESTAURANT_SELECT} WHERE r.id = $1`, [id]));
}));
adminRouter.put('/restaurants/:id/products', requirePermission('restaurants.write'), asyncHandler(async (req, res) => {
  const { productIds } = parse(z.object({ productIds: z.array(z.number().int().positive()).max(1000) }), req.body);
  const id = idParam(req);
  await withTx(async (tx) => {
    await tx.query(`DELETE FROM restaurant_product_access WHERE restaurant_id = $1`, [id]);
    for (const pid of productIds) await tx.query(`INSERT INTO restaurant_product_access VALUES ($1,$2)`, [id, pid]);
    await audit(tx, { userId: req.user!.id, action: 'restaurante.catalogo', entity: 'restaurant', entityId: id, detail: { count: productIds.length } });
  });
  res.status(204).end();
}));
adminRouter.get('/restaurants/:id/products', requirePermission('restaurants.read'), asyncHandler(async (req, res) => {
  res.json((await db.query<{ productId: number }>(`SELECT product_id FROM restaurant_product_access WHERE restaurant_id = $1`, [idParam(req)])).map((r) => r.productId));
}));

// ───────── Usuarios ─────────
const USER_SELECT = `
  SELECT u.id, u.username, u.full_name, u.active, u.must_change_password, u.last_login_at, u.created_at, u.locked_until,
         r.code AS role, r.name AS role_name, u.restaurant_id, rs.name AS restaurant_name
    FROM users u JOIN roles r ON r.id = u.role_id LEFT JOIN restaurants rs ON rs.id = u.restaurant_id`;
const userBase = z.object({
  username: z.string().trim().min(3).max(40).regex(/^[a-zA-Z0-9._-]+$/, 'Solo letras, números, punto, guion y guion bajo'),
  fullName: z.string().trim().min(1).max(120),
  role: z.enum(['admin', 'almacen', 'compras', 'entrega', 'restaurante']),
  restaurantId: z.number().int().positive().nullable().optional(),
  active: z.boolean().optional(),
});
const checkRoleScope = (b: z.infer<typeof userBase>) => {
  if (b.role === 'restaurante' && !b.restaurantId) throw badRequest('Un usuario de restaurante requiere restaurante');
  if (b.role !== 'restaurante' && b.restaurantId) throw badRequest('Solo los usuarios de restaurante se asocian a un restaurante');
};

adminRouter.get('/roles', requirePermission('users.read'), asyncHandler(async (_req, res) => {
  res.json(await db.query(`SELECT id, code, name FROM roles ORDER BY id`));
}));
adminRouter.get('/users', requirePermission('users.read'), asyncHandler(async (_req, res) => {
  res.json(await db.query(`${USER_SELECT} ORDER BY u.username`));
}));
adminRouter.post('/users', requirePermission('users.write'), asyncHandler(async (req, res) => {
  const b = parse(userBase.extend({ password: passwordSchema, mustChangePassword: z.boolean().optional() }), req.body);
  checkRoleScope(b);
  const row = await db.one<{ id: number }>(
    `INSERT INTO users (username, full_name, password_hash, role_id, restaurant_id, must_change_password)
     VALUES ($1,$2,$3,(SELECT id FROM roles WHERE code = $4),$5,$6) RETURNING id`,
    [b.username, b.fullName, await bcrypt.hash(b.password, config.bcryptRounds), b.role, b.restaurantId ?? null, b.mustChangePassword ?? true]);
  await audit(db, { userId: req.user!.id, action: 'usuario.crear', entity: 'user', entityId: row!.id, detail: { role: b.role }, ip: clientIp(req) });
  res.status(201).json(await db.one(`${USER_SELECT} WHERE u.id = $1`, [row!.id]));
}));
adminRouter.put('/users/:id', requirePermission('users.write'), asyncHandler(async (req, res) => {
  const b = parse(userBase, req.body);
  checkRoleScope(b);
  const id = idParam(req);
  if (id === req.user!.id && (b.active === false || b.role !== 'admin')) throw badRequest('No puedes desactivarte ni quitarte el rol de administrador');
  if (!(await db.one(
    `UPDATE users SET username=$2, full_name=$3, role_id=(SELECT id FROM roles WHERE code=$4), restaurant_id=$5, active=COALESCE($6, active) WHERE id=$1 RETURNING id`,
    [id, b.username, b.fullName, b.role, b.restaurantId ?? null, b.active ?? null]))) throw notFound('Usuario');
  await audit(db, { userId: req.user!.id, action: 'usuario.editar', entity: 'user', entityId: id, detail: { role: b.role, active: b.active }, ip: clientIp(req) });
  res.json(await db.one(`${USER_SELECT} WHERE u.id = $1`, [id]));
}));
adminRouter.post('/users/:id/reset-password', requirePermission('users.write'), asyncHandler(async (req, res) => {
  const { password } = parse(z.object({ password: passwordSchema }), req.body);
  const id = idParam(req);
  if (!(await db.one(
    `UPDATE users SET password_hash=$2, must_change_password=true, failed_logins=0, locked_until=NULL WHERE id=$1 RETURNING id`,
    [id, await bcrypt.hash(password, config.bcryptRounds)]))) throw notFound('Usuario');
  await audit(db, { userId: req.user!.id, action: 'usuario.reset_password', entity: 'user', entityId: id, ip: clientIp(req) });
  res.status(204).end();
}));

// ───────── Notificaciones ─────────
adminRouter.get('/notifications', asyncHandler(async (req, res) => {
  const u = req.user!;
  const { pageSize, offset } = pageParams(req.query);
  const scope = u.restaurantId ? `n.audience = 'restaurant' AND n.restaurant_id = $1` : `n.audience = 'staff'`;
  const params: unknown[] = [u.restaurantId ?? u.id];
  const readJoin = `LEFT JOIN notification_reads nr ON nr.notification_id = n.id AND nr.user_id = ${u.restaurantId ? '$2' : '$1'}`;
  if (u.restaurantId) params.push(u.id);
  const unreadOnly = req.query.unread === 'true' ? 'AND nr.user_id IS NULL' : '';
  const data = await db.query(
    `SELECT n.id, n.type, n.title, n.body, n.payload, n.created_at, (nr.user_id IS NOT NULL) AS read
       FROM notifications n ${readJoin} WHERE ${scope} ${unreadOnly} ORDER BY n.id DESC LIMIT ${pageSize} OFFSET ${offset}`, params);
  const unread = await db.one<{ n: number }>(
    `SELECT count(*)::int AS n FROM notifications n ${readJoin} WHERE ${scope} AND nr.user_id IS NULL`, params);
  res.json({ data, unreadCount: unread!.n });
}));
adminRouter.post('/notifications/read-all', asyncHandler(async (req, res) => {
  const u = req.user!;
  await db.query(
    `INSERT INTO notification_reads (notification_id, user_id)
     SELECT n.id, $1 FROM notifications n WHERE ${u.restaurantId ? `n.audience = 'restaurant' AND n.restaurant_id = $2` : `n.audience = 'staff'`}
     ON CONFLICT DO NOTHING`, u.restaurantId ? [u.id, u.restaurantId] : [u.id]);
  res.status(204).end();
}));
adminRouter.post('/notifications/:id/read', asyncHandler(async (req, res) => {
  const u = req.user!;
  const n = await db.one(
    `SELECT 1 FROM notifications WHERE id = $1 AND ${u.restaurantId ? `audience='restaurant' AND restaurant_id = $2` : `audience='staff'`}`,
    u.restaurantId ? [idParam(req), u.restaurantId] : [idParam(req)]);
  if (!n) throw notFound('Notificación');
  await db.query(`INSERT INTO notification_reads (notification_id, user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING`, [idParam(req), u.id]);
  res.status(204).end();
}));

// ───────── Configuración y auditoría ─────────
adminRouter.get('/settings', requirePermission('settings.read'), asyncHandler(async (_req, res) => {
  const rows = await db.query<{ key: string; value: unknown }>(`SELECT key, value FROM settings`);
  res.json(Object.fromEntries(rows.map((r) => [r.key, r.value])));
}));
const settingsSchema = z.object({
  company_name: z.string().trim().min(1).max(100).optional(),
  timezone: z.string().refine((tz) => { try { new Intl.DateTimeFormat('es', { timeZone: tz }); return true; } catch { return false; } }, 'Zona horaria inválida').optional(),
  show_prices: z.boolean().optional(),
  notification_sound: z.boolean().optional(),
}).strict();
adminRouter.put('/settings', requirePermission('settings.write'), asyncHandler(async (req, res) => {
  const b = parse(settingsSchema, req.body);
  for (const [k, v] of Object.entries(b)) {
    await db.query(`INSERT INTO settings (key, value) VALUES ($1,$2) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()`, [k, JSON.stringify(v)]);
  }
  await audit(db, { userId: req.user!.id, action: 'configuracion.editar', detail: b, ip: clientIp(req) });
  const rows = await db.query<{ key: string; value: unknown }>(`SELECT key, value FROM settings`);
  res.json(Object.fromEntries(rows.map((r) => [r.key, r.value])));
}));
adminRouter.get('/audit', requirePermission('audit.read'), asyncHandler(async (req, res) => {
  const { page, pageSize, offset } = pageParams(req.query);
  const total = await db.one<{ n: number }>(`SELECT count(*)::int AS n FROM audit_logs`);
  res.json({
    data: await db.query(
      `SELECT a.id, a.action, a.entity, a.entity_id, a.detail, a.ip, a.created_at, u.username
         FROM audit_logs a LEFT JOIN users u ON u.id = a.user_id ORDER BY a.id DESC LIMIT ${pageSize} OFFSET ${offset}`),
    total: total!.n, page, pageSize,
  });
}));
