import { Router } from 'express';
import { z } from 'zod';
import { db } from '../db';
import { forbidden } from '../errors';
import { clientIp, requirePermission } from '../middleware/auth';
import * as orders from '../services/orders';
import { asyncHandler, idParam, pageParams, parse, qty } from '../util';

export const ordersRouter = Router();

ordersRouter.get('/orders', requirePermission('orders.read', 'orders.own'), asyncHandler(async (req, res) => {
  const { page, pageSize, offset } = pageParams(req.query);
  const user = req.user!;
  const result = await orders.listOrders({
    status: req.query.status ? String(req.query.status) : undefined,
    q: req.query.q ? String(req.query.q) : undefined,
    // Un restaurante solo ve sus propios pedidos, sin importar los parámetros
    restaurantId: user.restaurantId ?? (req.query.restaurantId ? Number(req.query.restaurantId) : undefined),
    roleScope: user, limit: pageSize, offset,
  });
  res.json({ ...result, page, pageSize });
}));

ordersRouter.get('/orders/:id', requirePermission('orders.read', 'orders.own'), asyncHandler(async (req, res) => {
  res.json(await orders.getOrder(idParam(req), req.user!));
}));

ordersRouter.post('/orders', requirePermission('orders.create'), asyncHandler(async (req, res) => {
  const b = parse(z.object({
    items: z.array(z.object({ productId: z.number().int().positive(), quantity: qty })).min(1, 'El pedido no tiene productos').max(200),
    notes: z.string().trim().max(500).optional(),
  }), req.body);
  const key = req.header('Idempotency-Key')?.slice(0, 100);
  const { orderId, duplicate } = await orders.createOrder(req.user!, { ...b, idempotencyKey: key }, clientIp(req));
  res.status(duplicate ? 200 : 201).json(await orders.getOrder(orderId, req.user!));
}));

const manage = requirePermission('orders.manage');
const respond = (fn: (id: number, req: any) => Promise<unknown>) =>
  asyncHandler(async (req, res) => {
    const id = idParam(req);
    await fn(id, req);
    res.json(await orders.getOrder(id, req.user!));
  });

ordersRouter.post('/orders/:id/review', manage, respond((id, req) => orders.markReviewing(id, req.user)));
ordersRouter.post('/orders/:id/accept', manage, respond((id, req) => orders.acceptOrder(id, req.user, parse(z.object({
  items: z.array(z.object({ itemId: z.number().int().positive(), authorizedQuantity: z.number().min(0).max(1_000_000) })).optional(),
  note: z.string().max(300).optional(),
}), req.body ?? {}))));
ordersRouter.post('/orders/:id/reserve-pending', manage, respond((id, req) => orders.reserveOrderPending(id, req.user)));
ordersRouter.post('/orders/:id/prepare', manage, respond((id, req) => orders.prepareOrder(id, req.user, parse(z.object({
  items: z.array(z.object({ itemId: z.number().int().positive(), preparedQuantity: z.number().min(0).max(1_000_000) })).optional(),
  closePartial: z.boolean().optional(), note: z.string().max(300).optional(),
}), req.body ?? {}))));
ordersRouter.post('/orders/:id/dispatch', requirePermission('orders.manage', 'deliveries.update'), respond((id, req) =>
  orders.dispatchOrder(id, req.user, parse(z.object({ assignedTo: z.number().int().positive().optional() }), req.body ?? {}))));
ordersRouter.post('/orders/:id/deliver', requirePermission('orders.manage', 'deliveries.update'), respond((id, req) => orders.deliverOrder(id, req.user)));
ordersRouter.post('/orders/:id/confirm-reception', requirePermission('orders.confirm_reception'), respond((id, req) => orders.confirmReception(id, req.user)));
ordersRouter.post('/orders/:id/cancel', requirePermission('orders.manage', 'orders.own'), respond(async (id, req) => {
  const { reason } = parse(z.object({ reason: z.string().trim().min(3, 'Indica el motivo').max(300) }), req.body);
  if (req.user.role === 'restaurante' && !req.user.permissions.includes('orders.own')) throw forbidden();
  await orders.cancelOrder(id, req.user, reason);
}));

ordersRouter.get('/deliveries', requirePermission('deliveries.read'), asyncHandler(async (req, res) => {
  const mine = req.user!.role === 'entrega';
  res.json(await db.query(
    `SELECT o.id AS order_id, o.folio, o.status AS order_status, r.name AS restaurant_name, r.address AS restaurant_address,
            d.status, d.dispatched_at, d.delivered_at, d.received_at, a.full_name AS assigned_to_name, d.assigned_to
       FROM orders o JOIN restaurants r ON r.id = o.restaurant_id
       LEFT JOIN deliveries d ON d.order_id = o.id LEFT JOIN users a ON a.id = d.assigned_to
      WHERE o.status IN ('listo_entrega','en_entrega','entregado')
        ${mine ? 'AND (d.assigned_to IS NULL OR d.assigned_to = $1)' : ''}
      ORDER BY (o.status = 'entregado'), o.created_at DESC LIMIT 200`, mine ? [req.user!.id] : []));
}));

ordersRouter.get('/delivery-staff', requirePermission('orders.manage', 'deliveries.read'), asyncHandler(async (_req, res) => {
  res.json(await db.query(`SELECT u.id, u.full_name FROM users u JOIN roles r ON r.id = u.role_id WHERE r.code = 'entrega' AND u.active ORDER BY u.full_name`));
}));
