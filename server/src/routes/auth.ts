import { Router } from 'express';
import bcrypt from 'bcryptjs';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { config } from '../config';
import { db } from '../db';
import { unauthorized, badRequest, AppError } from '../errors';
import { authenticate, clientIp, signToken } from '../middleware/auth';
import { ROLE_PERMISSIONS, RoleCode } from '../permissions';
import { audit } from '../services/audit';
import { asyncHandler, parse } from '../util';

export const authRouter = Router();

const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: config.loginMaxAttempts,
  standardHeaders: true,
  legacyHeaders: false,
  skip: () => config.isTest,
  handler: (_req, _res, next) => next(new AppError(429, 'TOO_MANY_ATTEMPTS', 'Demasiados intentos. Espera unos minutos.')),
});

const LOCK_AFTER = 5;
const LOCK_MINUTES = 15;

export const passwordSchema = z.string().min(8, 'Mínimo 8 caracteres').max(128)
  .refine((p) => /[A-Za-z]/.test(p) && /\d/.test(p), 'Debe contener letras y números');

async function profile(userId: number) {
  const u = await db.one<any>(
    `SELECT u.id, u.username, u.full_name, u.must_change_password, r.code AS role, r.name AS role_name,
            rs.id AS restaurant_id, rs.name AS restaurant_name, rs.code AS restaurant_code
       FROM users u JOIN roles r ON r.id = u.role_id LEFT JOIN restaurants rs ON rs.id = u.restaurant_id WHERE u.id = $1`, [userId]);
  return {
    id: u.id, username: u.username, fullName: u.fullName, role: u.role as RoleCode, roleName: u.roleName,
    mustChangePassword: u.mustChangePassword,
    restaurant: u.restaurantId ? { id: u.restaurantId, name: u.restaurantName, code: u.restaurantCode } : null,
    permissions: ROLE_PERMISSIONS[u.role as RoleCode],
  };
}

authRouter.post('/login', loginLimiter, asyncHandler(async (req, res) => {
  const { username, password } = parse(z.object({ username: z.string().min(1).max(60), password: z.string().min(1).max(128) }), req.body);
  const u = await db.one<any>(
    `SELECT u.id, u.password_hash, u.active, u.failed_logins, u.locked_until, rs.active AS restaurant_active, u.restaurant_id
       FROM users u LEFT JOIN restaurants rs ON rs.id = u.restaurant_id WHERE lower(u.username) = lower($1)`, [username]);
  if (u?.lockedUntil && new Date(u.lockedUntil) > new Date()) {
    throw new AppError(423, 'LOCKED', 'Cuenta bloqueada temporalmente por intentos fallidos. Intenta más tarde.');
  }
  // Se compara siempre contra un hash para no revelar si el usuario existe por tiempo de respuesta
  const hash = u?.passwordHash ?? '$2a$12$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidinva';
  const ok = await bcrypt.compare(password, hash).catch(() => false);
  const enabled = u && u.active && (u.restaurantId === null || u.restaurantActive);
  if (!u || !ok || !enabled) {
    if (u) {
      await db.query(
        `UPDATE users SET failed_logins = failed_logins + 1,
                locked_until = CASE WHEN failed_logins + 1 >= $2 THEN now() + ($3 || ' minutes')::interval ELSE locked_until END
          WHERE id = $1`, [u.id, LOCK_AFTER, String(LOCK_MINUTES)]);
    }
    await audit(db, { userId: u?.id, action: 'auth.login_fallido', detail: { username }, ip: clientIp(req) });
    throw unauthorized('Usuario o contraseña incorrectos');
  }
  await db.query(`UPDATE users SET failed_logins = 0, locked_until = NULL, last_login_at = now() WHERE id = $1`, [u.id]);
  await audit(db, { userId: u.id, action: 'auth.login', ip: clientIp(req) });
  res.json({ token: signToken(u.id), user: await profile(u.id) });
}));

authRouter.get('/me', authenticate, asyncHandler(async (req, res) => {
  res.json(await profile(req.user!.id));
}));

authRouter.post('/logout', authenticate, asyncHandler(async (req, res) => {
  await audit(db, { userId: req.user!.id, action: 'auth.logout', ip: clientIp(req) });
  res.status(204).end();
}));

authRouter.post('/change-password', authenticate, asyncHandler(async (req, res) => {
  const body = parse(z.object({ currentPassword: z.string().min(1), newPassword: passwordSchema }), req.body);
  const u = await db.one<{ passwordHash: string }>(`SELECT password_hash FROM users WHERE id = $1`, [req.user!.id]);
  if (!(await bcrypt.compare(body.currentPassword, u!.passwordHash))) throw badRequest('La contraseña actual no es correcta');
  await db.query(`UPDATE users SET password_hash = $2, must_change_password = false WHERE id = $1`,
    [req.user!.id, await bcrypt.hash(body.newPassword, config.bcryptRounds)]);
  await audit(db, { userId: req.user!.id, action: 'auth.cambio_password', ip: clientIp(req) });
  res.status(204).end();
}));
