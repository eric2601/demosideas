import { Client } from 'pg';
import { execSync } from 'child_process';

const url = process.env.TEST_DATABASE_URL ?? 'postgres://almacen:almacen_dev@localhost:5432/almacen_test';

// Recrea el esquema de la base de pruebas y aplica las migraciones
export default async function setup() {
  const c = new Client({ connectionString: url });
  await c.connect();
  await c.query('DROP SCHEMA public CASCADE; CREATE SCHEMA public;');
  await c.end();
  execSync('npx tsx src/scripts/migrate.ts', { env: { ...process.env, NODE_ENV: 'test', DATABASE_URL: url }, stdio: 'ignore' });
}
