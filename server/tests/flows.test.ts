import { beforeAll, describe, expect, it } from 'vitest';
import { db, pool } from '../src/db';
import { afterAll } from 'vitest';
import { as, createProduct, inv, login, seedBasics, stockUp } from './helpers';

let admin: ReturnType<typeof as>, almacen: ReturnType<typeof as>, compras: ReturnType<typeof as>,
  r1: ReturnType<typeof as>, r2: ReturnType<typeof as>, entrega: ReturnType<typeof as>;

beforeAll(async () => {
  await seedBasics();
  admin = as(await login('admin')); almacen = as(await login('almacen')); compras = as(await login('compras'));
  r1 = as(await login('r1')); r2 = as(await login('r2')); entrega = as(await login('entrega'));
});
afterAll(() => pool.end());

describe('seguridad y permisos', () => {
  it('rechaza peticiones sin token y credenciales incorrectas', async () => {
    const { default: request } = await import('supertest');
    const { app } = await import('./helpers');
    expect((await request(app).get('/api/products')).status).toBe(401);
    expect((await request(app).post('/api/auth/login').send({ username: 'admin', password: 'mal' })).status).toBe(401);
  });
  it('un restaurante no accede a módulos del almacén', async () => {
    for (const url of ['/api/inventory', '/api/users', '/api/purchases', '/api/dashboard', '/api/movements']) {
      expect((await r1.get(url)).status, url).toBe(403);
    }
    expect((await r1.post('/api/receipts', { supplierId: 1, items: [{ productId: 1, quantity: 1, unitCost: 1 }] })).status).toBe(403);
  });
  it('compras no puede gestionar pedidos ni usuarios; el almacén no gestiona usuarios', async () => {
    expect((await compras.post('/api/orders/1/accept')).status).toBe(403);
    expect((await almacen.get('/api/users')).status).toBe(403);
  });
  it('las contraseñas se guardan con hash', async () => {
    const u = await db.one<{ passwordHash: string }>(`SELECT password_hash FROM users WHERE username = 'admin'`);
    expect(u!.passwordHash).toMatch(/^\$2[aby]\$/);
  });
});

describe('inventario y pedidos', () => {
  it('dos pedidos simultáneos no comprometen la misma mercancía', async () => {
    const pid = await createProduct(admin, 'CONC-1');
    await stockUp(almacen, pid, 10);
    const body = { items: [{ productId: pid, quantity: 8 }] };
    const [a, b] = await Promise.all([r1.post('/api/orders', body, { 'Idempotency-Key': 'a' }), r2.post('/api/orders', body, { 'Idempotency-Key': 'b' })]);
    expect([a.status, b.status]).toEqual([201, 201]); // ninguno se pierde
    const stock = await inv(pid);
    expect(stock.reserved).toBe(10); // 8 + 2, nunca más que la existencia
    expect(stock.onHand).toBe(10);
    const reservedParts = [a.body.items[0].qtyReserved ?? 0, b.body.items[0].qtyReserved ?? 0];
    // el restaurante no ve cantidades reservadas internas; verificar en BD
    const rows = await db.query<{ qtyReserved: number }>(`SELECT qty_reserved FROM order_items WHERE product_id = $1`, [pid]);
    expect(rows.map((r) => r.qtyReserved).sort()).toEqual([2, 8]);
    void reservedParts;
    // se registró el faltante del pedido que excede lo disponible
    const sh = await db.query(`SELECT * FROM shortages WHERE product_id = $1 AND status <> 'resuelto'`, [pid]);
    expect(sh).toHaveLength(1);
  });

  it('la idempotencia evita pedidos duplicados', async () => {
    const pid = await createProduct(admin, 'IDEM-1');
    await stockUp(almacen, pid, 50);
    const body = { items: [{ productId: pid, quantity: 3 }] };
    const rs = await Promise.all([1, 2, 3].map(() => r1.post('/api/orders', body, { 'Idempotency-Key': 'misma-clave' })));
    expect(new Set(rs.map((r) => r.body.id)).size).toBe(1);
    expect((await inv(pid)).reserved).toBe(3);
    const n = await db.one<{ n: number }>(`SELECT count(*)::int AS n FROM orders WHERE idempotency_key = 'misma-clave'`);
    expect(n!.n).toBe(1);
  });

  it('ciclo completo: reserva → salida única → entrega; no descuenta dos veces', async () => {
    const pid = await createProduct(admin, 'FLOW-1');
    await stockUp(almacen, pid, 20);
    const created = await r1.post('/api/orders', { items: [{ productId: pid, quantity: 5 }] }, { 'Idempotency-Key': 'flow' });
    const id = created.body.id;
    expect(await inv(pid)).toMatchObject({ onHand: 20, reserved: 5 });
    expect((await almacen.post(`/api/orders/${id}/accept`)).body.status).toBe('en_preparacion');
    expect((await almacen.post(`/api/orders/${id}/prepare`)).body.status).toBe('listo_entrega');
    expect((await almacen.post(`/api/orders/${id}/dispatch`)).body.status).toBe('en_entrega');
    expect(await inv(pid)).toMatchObject({ onHand: 15, reserved: 0 });
    expect((await almacen.post(`/api/orders/${id}/dispatch`)).status).toBe(409);
    expect((await almacen.post(`/api/orders/${id}/cancel`, { reason: 'ya salió' })).status).toBe(409);
    expect(await inv(pid)).toMatchObject({ onHand: 15, reserved: 0 });
    // otro restaurante no puede confirmar ni ver el pedido
    expect((await r2.get(`/api/orders/${id}`)).status).toBe(404);
    expect((await r2.post(`/api/orders/${id}/confirm-reception`)).status).toBe(404);
    const done = await r1.post(`/api/orders/${id}/confirm-reception`);
    expect(done.body.status).toBe('entregado');
    expect(done.body.receivedAt).toBeTruthy();
    expect((await r1.post(`/api/orders/${id}/confirm-reception`)).status).toBe(200); // idempotente
    expect(await inv(pid)).toMatchObject({ onHand: 15, reserved: 0 });
  });

  it('cancelar antes de la entrega libera la reserva', async () => {
    const pid = await createProduct(admin, 'CANC-1');
    await stockUp(almacen, pid, 10);
    const id = (await r1.post('/api/orders', { items: [{ productId: pid, quantity: 4 }] })).body.id;
    expect((await inv(pid)).reserved).toBe(4);
    expect((await r1.post(`/api/orders/${id}/cancel`, { reason: 'me equivoqué' })).body.status).toBe('cancelado');
    expect((await inv(pid)).reserved).toBe(0);
  });

  it('surtido parcial: cantidades modificadas quedan registradas y el sobrante se libera', async () => {
    const pid = await createProduct(admin, 'PART-1');
    await stockUp(almacen, pid, 6);
    const id = (await r1.post('/api/orders', { items: [{ productId: pid, quantity: 10 }] })).body.id;
    const detail = await almacen.get(`/api/orders/${id}`);
    expect(detail.body.items[0]).toMatchObject({ qtyRequested: 10, qtyReserved: 6, qtyUnreserved: 4 });
    const prep = await almacen.post(`/api/orders/${id}/accept`);
    expect(prep.body.items[0].qtyAuthorized).toBe(10);
    const partial = await almacen.post(`/api/orders/${id}/prepare`);
    expect(partial.body.status).toBe('parcialmente_surtido');
    const closed = await almacen.post(`/api/orders/${id}/prepare`, { closePartial: true });
    expect(closed.body.status).toBe('listo_entrega');
    await entrega.post(`/api/orders/${id}/dispatch`);
    const fin = await r1.get(`/api/orders/${id}`);
    expect(fin.body.items[0]).toMatchObject({ qtyRequested: 10, qtyDelivered: 6, qtyPending: 4 });
    expect(await inv(pid)).toMatchObject({ onHand: 0, reserved: 0 });
    expect(fin.body.events.map((e: any) => e.event)).toContain('despachado');
  });

  it('no se puede autorizar más de lo solicitado', async () => {
    const pid = await createProduct(admin, 'AUTH-1');
    await stockUp(almacen, pid, 10);
    const created = await r1.post('/api/orders', { items: [{ productId: pid, quantity: 2 }] });
    const itemId = (await almacen.get(`/api/orders/${created.body.id}`)).body.items[0].id;
    expect((await almacen.post(`/api/orders/${created.body.id}/accept`, { items: [{ itemId, authorizedQuantity: 5 }] })).status).toBe(400);
  });

  it('valida datos de entrada', async () => {
    expect((await r1.post('/api/orders', { items: [] })).status).toBe(400);
    expect((await r1.post('/api/orders', { items: [{ productId: 1, quantity: -2 }] })).status).toBe(400);
    expect((await r1.post('/api/orders', { items: [{ productId: 99999, quantity: 1 }] })).status).toBe(400);
  });
});

describe('compras, faltantes y trazabilidad', () => {
  it('crear una orden de compra no modifica existencias; la recepción parcial suma solo lo recibido', async () => {
    const pid = await createProduct(admin, 'BUY-1', 10);
    await stockUp(almacen, pid, 4); // por debajo del mínimo → faltante automático
    const open = await db.query<{ id: number; quantityShort: number }>(`SELECT id, quantity_short FROM shortages WHERE product_id = $1 AND status <> 'resuelto'`, [pid]);
    expect(open).toHaveLength(1);
    expect(open[0].quantityShort).toBe(6);

    const sug = (await compras.get('/api/purchases/suggestions')).body.find((s: any) => s.productId === pid);
    expect(sug.suggested).toBe(6);

    const po = await compras.post('/api/purchases', { supplierId: 1, items: [{ productId: pid, quantity: 20, unitCost: 12 }], shortageIds: [open[0].id] });
    expect(po.status).toBe(201);
    expect((await inv(pid)).onHand).toBe(4);
    // ya cubierto por la orden: la sugerencia desaparece
    const after = (await compras.get('/api/purchases/suggestions')).body.find((s: any) => s.productId === pid);
    expect(after.suggested).toBe(0);
    expect((await almacen.post(`/api/purchases/${po.body.id}/receive`, { items: [{ productId: pid, quantity: 5, unitCost: 12 }] })).status).toBe(409); // no autorizada

    expect((await compras.post(`/api/purchases/${po.body.id}/submit`)).status).toBe(204);
    expect((await compras.post(`/api/purchases/${po.body.id}/authorize`)).status).toBe(403);
    expect((await almacen.post(`/api/purchases/${po.body.id}/authorize`)).status).toBe(204);

    expect((await almacen.post(`/api/purchases/${po.body.id}/receive`, { items: [{ productId: pid, quantity: 3, unitCost: 12 }] })).status).toBe(201);
    expect((await inv(pid)).onHand).toBe(7);
    expect((await compras.get(`/api/purchases/${po.body.id}`)).body.status).toBe('parcialmente_recibida');
    const sh = (await compras.get(`/api/shortages/${open[0].id}`)).body;
    expect(sh.status).toBe('parcialmente_resuelto');

    // no se puede recibir más de lo pendiente
    expect((await almacen.post(`/api/purchases/${po.body.id}/receive`, { items: [{ productId: pid, quantity: 18, unitCost: 12 }] })).status).toBe(409);
    expect((await almacen.post(`/api/purchases/${po.body.id}/receive`, { items: [{ productId: pid, quantity: 17, unitCost: 12 }] })).status).toBe(201);
    expect((await compras.get(`/api/purchases/${po.body.id}`)).body.status).toBe('recibida');
    expect((await compras.get(`/api/shortages/${open[0].id}`)).body.status).toBe('resuelto');
    expect((await inv(pid)).onHand).toBe(24);
  });

  it('no duplica faltantes abiertos del mismo producto', async () => {
    const pid = await createProduct(admin, 'DUP-1', 5);
    const a = await compras.post('/api/shortages', { productId: pid, quantityRequired: 3 });
    const b = await compras.post('/api/shortages', { productId: pid, quantityRequired: 7 });
    expect(a.status).toBe(201); expect(b.status).toBe(200); expect(b.body.id).toBe(a.body.id);
    const n = await db.one<{ n: number }>(`SELECT count(*)::int AS n FROM shortages WHERE product_id = $1`, [pid]);
    expect(n!.n).toBe(1);
  });

  it('el historial de movimientos es inmutable y los productos con historial no se eliminan', async () => {
    const pid = await createProduct(admin, 'HIST-1');
    await stockUp(almacen, pid, 5);
    await expect(db.query(`UPDATE inventory_movements SET quantity = 99 WHERE product_id = $1`, [pid])).rejects.toThrow(/solo inserción/);
    await expect(db.query(`DELETE FROM audit_logs`)).rejects.toThrow(/solo inserción/);
    expect((await admin.delete(`/api/products/${pid}`)).status).toBe(409);
    const unused = await createProduct(admin, 'HIST-2');
    expect((await admin.delete(`/api/products/${unused}`)).status).toBe(204);
  });

  it('los ajustes dejan movimientos con existencia anterior y posterior; no permiten dejar la reserva al descubierto', async () => {
    const pid = await createProduct(admin, 'ADJ-1');
    await stockUp(almacen, pid, 10);
    await r1.post('/api/orders', { items: [{ productId: pid, quantity: 8 }] });
    expect((await almacen.post('/api/inventory/adjustments', { productId: pid, type: 'ajuste_negativo', quantity: 5, reason: 'Merma' })).status).toBe(409);
    expect((await almacen.post('/api/inventory/adjustments', { productId: pid, type: 'ajuste_negativo', quantity: 2, reason: 'Merma' })).status).toBe(201);
    expect((await almacen.post('/api/inventory/adjustments', { productId: pid, type: 'correccion_autorizada', direction: 'in', quantity: 1, reason: 'Corrección' })).status).toBe(400);
    const mv = await admin.get(`/api/movements?productId=${pid}`);
    const last = mv.body.data[0];
    expect(last).toMatchObject({ type: 'ajuste_negativo', onHandBefore: 10, onHandAfter: 8 });
  });

  it('conteo físico registra la diferencia', async () => {
    const pid = await createProduct(admin, 'CNT-1');
    await stockUp(almacen, pid, 10);
    const res = await almacen.post('/api/inventory/counts', { productId: pid, countedQuantity: 8, reason: 'Conteo mensual' });
    expect(res.body).toMatchObject({ systemQuantity: 10, difference: -2 });
    expect((await almacen.get(`/api/inventory/${pid}`)).body).toMatchObject({ onHand: 8, lastCountDifference: -2 });
  });

  it('el dashboard calcula indicadores desde la BD', async () => {
    const res = await admin.get('/api/dashboard');
    expect(res.status).toBe(200);
    expect(res.body.cards.totalProducts).toBeGreaterThan(5);
    expect(res.body.movementsByDay).toHaveLength(7);
  });

  it('notificaciones guardadas y marcables como leídas', async () => {
    const list = await almacen.get('/api/notifications');
    expect(list.body.data.some((n: any) => n.type === 'pedido_nuevo')).toBe(true);
    const unread = list.body.unreadCount;
    await almacen.post(`/api/notifications/${list.body.data[0].id}/read`);
    expect((await almacen.get('/api/notifications')).body.unreadCount).toBe(unread - 1);
    // un restaurante no ve las notificaciones del personal
    expect((await r1.get('/api/notifications')).body.data.every((n: any) => n.type !== 'pedido_nuevo')).toBe(true);
  });
});
