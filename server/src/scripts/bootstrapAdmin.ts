import bcrypt from 'bcryptjs';
import crypto from 'crypto';
import { config } from '../config';
import { db, pool } from '../db';
import { runMigrations } from './migrate';

/** Crea el primer administrador. Uso: ADMIN_USERNAME=admin ADMIN_PASSWORD=... npm run bootstrap-admin */
async function main() {
  await runMigrations();
  const username = process.env.ADMIN_USERNAME ?? 'admin';
  const generated = !process.env.ADMIN_PASSWORD;
  const password = process.env.ADMIN_PASSWORD ?? crypto.randomBytes(9).toString('base64url') + '7';
  const exists = await db.one(`SELECT 1 FROM users WHERE lower(username) = lower($1)`, [username]);
  if (exists) { console.log(`El usuario "${username}" ya existe; no se hicieron cambios.`); return; }
  await db.query(
    `INSERT INTO users (username, full_name, password_hash, role_id, must_change_password)
     VALUES ($1,'Administrador',$2,(SELECT id FROM roles WHERE code='admin'),true)`,
    [username, await bcrypt.hash(password, config.bcryptRounds)]);
  console.log(`Administrador "${username}" creado.`);
  if (generated) console.log(`Contraseña temporal (mostrada una sola vez): ${password}`);
}
main().then(() => pool.end()).catch((e) => { console.error(e); process.exit(1); });
