import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import fs from 'fs';
import path from 'path';
import { config } from './config';
import { db } from './db';
import { authenticate } from './middleware/auth';
import { errorHandler, notFoundHandler } from './middleware/errorHandler';
import { asyncHandler } from './util';
import { authRouter } from './routes/auth';
import { catalogRouter } from './routes/catalog';
import { inventoryRouter } from './routes/inventory';
import { purchasingRouter } from './routes/purchasing';
import { ordersRouter } from './routes/orders';
import { adminRouter } from './routes/admin';
import { dashboardRouter } from './routes/dashboard';

export function createApp() {
  const app = express();
  app.set('trust proxy', false);
  // HTTP en LAN: sin HSTS ni upgrade-insecure-requests, o el navegador intentaría HTTPS
  app.use(helmet({ hsts: false, contentSecurityPolicy: {
    useDefaults: true,
    directives: { 'upgrade-insecure-requests': null, 'connect-src': ["'self'", 'ws:', 'wss:'], 'media-src': ["'self'", 'data:'] },
  } }));
  // CORS: solo orígenes configurados (la API móvil nativa no usa CORS; el panel web se sirve del mismo origen)
  app.use(cors({ origin: config.corsOrigins.length ? config.corsOrigins : false }));
  app.use(express.json({ limit: '256kb' }));

  app.get('/api/health', asyncHandler(async (_req, res) => {
    await db.query('SELECT 1');
    res.json({ status: 'ok', time: new Date().toISOString() });
  }));
  app.get('/api/config', asyncHandler(async (_req, res) => {
    const rows = await db.query<{ key: string; value: unknown }>(
      `SELECT key, value FROM settings WHERE key IN ('company_name','timezone','show_prices','notification_sound')`);
    const s = Object.fromEntries(rows.map((r) => [r.key, r.value]));
    res.json({ companyName: s.company_name, timezone: s.timezone, showPrices: s.show_prices, notificationSound: s.notification_sound, serverTime: new Date().toISOString() });
  }));

  app.use('/api/auth', authRouter);
  app.use('/api', authenticate, catalogRouter, inventoryRouter, purchasingRouter, ordersRouter, adminRouter, dashboardRouter);
  app.use('/api', notFoundHandler);

  if (fs.existsSync(path.join(config.webDistDir, 'index.html'))) {
    app.use(express.static(config.webDistDir));
    app.get('*', (_req, res) => res.sendFile(path.join(config.webDistDir, 'index.html')));
  }
  app.use(errorHandler);
  return app;
}
