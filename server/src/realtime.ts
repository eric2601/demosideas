import type { Server as HttpServer } from 'http';
import { Server } from 'socket.io';
import jwt from 'jsonwebtoken';
import { config } from './config';
import { db } from './db';
import { isStaffRole, RoleCode } from './permissions';

let io: Server | null = null;

export function initRealtime(server: HttpServer) {
  io = new Server(server, {
    cors: { origin: config.corsOrigins.length ? config.corsOrigins : false },
    pingInterval: 10_000,
    pingTimeout: 8_000,
  });

  io.use(async (socket, next) => {
    try {
      const token = socket.handshake.auth?.token as string | undefined;
      if (!token) return next(new Error('unauthorized'));
      const payload = jwt.verify(token, config.jwtSecret) as unknown as { uid: number };
      const user = await db.one<{ id: number; role: RoleCode; restaurantId: number | null }>(
        `SELECT u.id, r.code AS role, u.restaurant_id FROM users u JOIN roles r ON r.id = u.role_id
         WHERE u.id = $1 AND u.active`, [payload.uid]);
      if (!user) return next(new Error('unauthorized'));
      socket.data.user = user;
      next();
    } catch {
      next(new Error('unauthorized'));
    }
  });

  io.on('connection', (socket) => {
    const user = socket.data.user as { id: number; role: RoleCode; restaurantId: number | null };
    if (isStaffRole(user.role)) socket.join('staff');
    else if (user.restaurantId) socket.join(`restaurant:${user.restaurantId}`);
  });
  return io;
}

export const emitStaff = (event: string, payload: unknown) => io?.to('staff').emit(event, payload);
export const emitRestaurant = (restaurantId: number, event: string, payload: unknown) =>
  io?.to(`restaurant:${restaurantId}`).emit(event, payload);
export const closeRealtime = () => io?.close();
