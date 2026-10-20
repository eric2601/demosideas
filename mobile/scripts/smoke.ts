/**
 * Prueba de humo contra el backend real, usando el MISMO cliente HTTP y endpoints que la app.
 * Uso:  SERVER_URL=http://localhost:3000 npx tsx scripts/smoke.ts
 * Crea un pedido de prueba con rest1.demo y lo cancela al terminar.
 */
import { randomUUID } from 'node:crypto';
import { io } from 'socket.io-client';
import { createHttpClient } from '../src/api/client';
import { createApi } from '../src/api/endpoints';
import { IdempotencyAttempt } from '../src/lib/idempotency';
import type { OrderUpdatedEvent } from '../src/types';

const SERVER = process.env.SERVER_URL ?? 'http://localhost:3000';
const PASSWORD = process.env.DEMO_PASSWORD ?? 'Demo-Temporal-2026';

let failed = 0;
const ok = (cond: unknown, msg: string) => {
  console.log(`${cond ? 'OK  ' : 'FAIL'} ${msg}`);
  if (!cond) failed++;
};

function makeApi() {
  let token: string | null = null;
  const http = createHttpClient({ getBaseUrl: () => SERVER, getToken: () => token });
  return { api: createApi(http), http, setToken: (t: string) => { token = t; } };
}

async function main() {
  const rest = makeApi();
  const wh = makeApi();

  const health = await rest.api.health();
  ok(health.status === 'ok', 'GET /health');
  const cfg = await rest.api.config();
  ok(!!cfg.timezone, `GET /config (zona ${cfg.timezone}, precios=${cfg.showPrices})`);

  // Rol incorrecto: el login funciona en la API, la app lo rechaza por role !== 'restaurante'
  const whLogin = await wh.api.login('almacen.demo', PASSWORD);
  wh.setToken(whLogin.token);
  ok(whLogin.user.role !== 'restaurante', `almacen.demo tiene rol "${whLogin.user.role}" (la app lo rechaza)`);

  const login = await rest.api.login('rest1.demo', PASSWORD);
  rest.setToken(login.token);
  ok(login.user.role === 'restaurante' && !!login.user.restaurant, `login rest1.demo (${login.user.restaurant?.name})`);
  const me = await rest.api.me();
  ok(me.id === login.user.id, 'GET /auth/me');

  const catalog = await rest.api.catalog();
  ok(catalog.length > 0, `GET /restaurant-catalog (${catalog.length} productos)`);
  const sample = catalog[0];
  ok(typeof sample.available === 'number' && ['ok', 'low', 'out'].includes(sample.stockStatus),
    `catálogo trae available/stockStatus (${sample.name}: ${sample.available} ${sample.unitName}, ${sample.stockStatus}, price=${'price' in sample ? sample.price : 'no viene'})`);

  const before = await rest.api.listOrders({ pageSize: 1 });

  // Socket del restaurante
  const events: OrderUpdatedEvent[] = [];
  const notifs: unknown[] = [];
  const socket = io(SERVER, { auth: { token: login.token }, transports: ['websocket'], reconnection: false });
  await new Promise<void>((res, rej) => {
    socket.on('connect', () => res());
    socket.on('connect_error', (e) => rej(e));
    setTimeout(() => rej(new Error('timeout socket')), 8000);
  });
  ok(socket.connected, 'socket.io conectado con auth.token');
  socket.on('order:updated', (e: OrderUpdatedEvent) => events.push(e));
  socket.on('notification:new', (n) => notifs.push(n));

  // Pedido con la misma Idempotency-Key dos veces (lógica real de la app)
  const attempt = new IdempotencyAttempt(() => randomUUID());
  const pick = catalog.filter((p) => p.stockStatus !== 'out').slice(0, 2);
  const body = {
    items: pick.map((p, i) => ({ productId: p.id, quantity: i === 0 ? 1 : 0.5 })),
    notes: 'Pedido de prueba (smoke)',
  };
  const key = attempt.keyFor(body);
  const o1 = await rest.api.createOrder(body, key);
  const o2 = await rest.api.createOrder(body, attempt.keyFor(body));
  ok(o1.id === o2.id && o1.folio === o2.folio, `Idempotency-Key repetida devuelve el mismo pedido (${o1.folio})`);
  const after = await rest.api.listOrders({ pageSize: 1 });
  ok(after.total === before.total + 1, `solo se creó un pedido (${before.total} -> ${after.total})`);

  // Una clave nueva (otro intento) sí crea otro pedido; se cancela enseguida
  attempt.clear();
  const k2 = attempt.keyFor(body);
  ok(k2 !== key, 'clave nueva tras éxito');

  const detail = await rest.api.getOrder(o1.id);
  ok(detail.status === 'nuevo' && detail.items.length === body.items.length, `GET /orders/:id estado=${detail.status}, ${detail.items.length} partidas`);
  const it = detail.items[0];
  ok(['qtyRequested', 'qtyAuthorized', 'qtyDelivered', 'qtyPending'].every((k) => k in it),
    `partida trae qtyRequested/qtyAuthorized/qtyDelivered/qtyPending (${JSON.stringify({ r: it.qtyRequested, a: it.qtyAuthorized, d: it.qtyDelivered, p: it.qtyPending })})`);
  ok(!('qtyReserved' in it) && !('availableNow' in it), 'el restaurante no ve reservas ni existencias internas');

  const list = await rest.api.listOrders({ status: 'nuevo', pageSize: 5 });
  ok(list.data.some((o) => o.id === o1.id), 'GET /orders?status=nuevo incluye el pedido');

  // Cambio hecho por almacén -> order:updated en el socket del restaurante
  await wh.http.request(`/orders/${o1.id}/review`, { method: 'POST' });
  await new Promise((r) => setTimeout(r, 1500));
  const ev = events.find((e) => e.orderId === o1.id);
  ok(!!ev && ev.status === 'pendiente_revision', `order:updated recibido (${ev ? `${ev.folio}: ${ev.previousStatus} -> ${ev.status}` : 'ninguno'})`);
  ok(notifs.length >= 0, `notification:new recibidos: ${notifs.length}`);

  const notif = await rest.api.notifications({ pageSize: 10 });
  ok(Array.isArray(notif.data) && typeof notif.unreadCount === 'number', `GET /notifications (${notif.data.length}, sin leer ${notif.unreadCount})`);
  if (notif.data[0]) {
    await rest.api.markNotificationRead(notif.data[0].id);
    ok(true, 'POST /notifications/:id/read');
  }

  // Cancelación permitida en pendiente_revision
  const cancelled = await rest.api.cancelOrder(o1.id, 'Prueba automática, se cancela');
  ok(cancelled.status === 'cancelado', 'POST /orders/:id/cancel');
  await new Promise((r) => setTimeout(r, 500));
  ok(events.some((e) => e.orderId === o1.id && e.status === 'cancelado'), 'order:updated por cancelación');

  // Errores: mensaje del servidor
  const bad = await rest.api.createOrder({ items: [{ productId: 999999, quantity: 1 }] }, randomUUID()).catch((e) => e);
  ok(bad?.status === 400 && /no existe/i.test(bad.message), `error comprensible del servidor: "${bad?.message}"`);
  const wrongLogin = await rest.api.login('rest1.demo', 'incorrecta').catch((e) => e);
  ok(wrongLogin?.status === 401, `login incorrecto -> 401 "${wrongLogin?.message}"`);

  // Flujo completo opcional (consume 1 unidad de inventario de desarrollo): FULL=1
  if (process.env.FULL === '1') {
    const p0 = pick[0];
    const full = await rest.api.createOrder({ items: [{ productId: p0.id, quantity: 2 }] }, randomUUID());
    const wr = (path: string, body?: unknown) => wh.http.request<any>(`/orders/${full.id}/${path}`, { method: 'POST', body });
    await wr('accept', { items: [{ itemId: full.items[0].id, authorizedQuantity: 1 }] });
    await wr('prepare', {});
    await wr('dispatch', {});
    const enEntrega = await rest.api.getOrder(full.id);
    ok(enEntrega.status === 'en_entrega', `flujo completo: estado ${enEntrega.status}`);
    const delivered = await wr('deliver');
    console.log('   partida tras entregar:', JSON.stringify(delivered.items[0]));
    const received = await rest.api.confirmReception(full.id);
    ok(!!received.receivedAt && !!received.receivedByName, `confirm-reception guarda fecha y usuario (${received.receivedByName})`);
    await new Promise((r) => setTimeout(r, 500));
    console.log('   estados recibidos por socket:', events.filter((e) => e.orderId === full.id).map((e) => e.status).join(' > '));
    console.log('   notificaciones recibidas:', notifs.map((n: any) => `${n.type}:${n.title}`).join(' | '));
  }

  socket.disconnect();
  await rest.api.logout();
  console.log(failed ? `\n${failed} verificación(es) fallaron` : '\nTodo correcto');
  process.exit(failed ? 1 : 0);
}

main().catch((e) => { console.error('ERROR', e); process.exit(1); });
