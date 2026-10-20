import type { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { config } from '../config';
import { db } from '../db';
import { forbidden, unauthorized } from '../errors';
import { Permission, ROLE_PERMISSIONS, RoleCode } from '../permissions';

export interface AuthUser {
  id: number;
  username: string;
  fullName: string;
  role: RoleCode;
  restaurantId: number | null;
  permissions: readonly Permission[];
}

declare global {
  namespace Express {
    interface Request { user?: AuthUser }
  }
}

export function signToken(userId: number) {
  return jwt.sign({ uid: userId }, config.jwtSecret, { expiresIn: config.jwtExpiresIn } as jwt.SignOptions);
}

/** Valida el token y recarga el usuario de BD: una cuenta desactivada pierde acceso de inmediato. */
export async function authenticate(req: Request, _res: Response, next: NextFunction) {
  try {
    const header = req.headers.authorization;
    if (!header?.startsWith('Bearer ')) throw unauthorized();
    let uid: number;
    try {
      uid = (jwt.verify(header.slice(7), config.jwtSecret) as unknown as { uid: number }).uid;
    } catch {
      throw unauthorized();
    }
    const row = await db.one<{ id: number; username: string; fullName: string; role: RoleCode; restaurantId: number | null }>(
      `SELECT u.id, u.username, u.full_name, r.code AS role, u.restaurant_id
         FROM users u JOIN roles r ON r.id = u.role_id
        WHERE u.id = $1 AND u.active
          AND (u.restaurant_id IS NULL OR EXISTS (SELECT 1 FROM restaurants x WHERE x.id = u.restaurant_id AND x.active))`,
      [uid]);
    if (!row) throw unauthorized();
    req.user = { ...row, permissions: ROLE_PERMISSIONS[row.role] };
    next();
  } catch (err) {
    next(err);
  }
}

export const requirePermission = (...perms: Permission[]) => (req: Request, _res: Response, next: NextFunction) => {
  if (!req.user) return next(unauthorized());
  if (!perms.some((p) => req.user!.permissions.includes(p))) return next(forbidden());
  next();
};

export const clientIp = (req: Request) => req.ip ?? req.socket.remoteAddress ?? null;
