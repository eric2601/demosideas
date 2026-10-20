import http from 'http';
import os from 'os';
import { config } from './config';
import { createApp } from './app';
import { initRealtime } from './realtime';
import { pool } from './db';
import { runMigrations } from './scripts/migrate';

async function main() {
  await runMigrations();
  const server = http.createServer(createApp());
  initRealtime(server);
  server.listen(config.port, config.host, () => {
    console.log(`Servidor listo en el puerto ${config.port}`);
    for (const list of Object.values(os.networkInterfaces())) {
      for (const n of list ?? []) if (n.family === 'IPv4' && !n.internal) console.log(`  → http://${n.address}:${config.port}`);
    }
  });
  const shutdown = () => server.close(() => pool.end().then(() => process.exit(0)));
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

main().catch((err) => { console.error(err); process.exit(1); });
