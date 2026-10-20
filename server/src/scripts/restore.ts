import { spawnSync } from 'child_process';
import fs from 'fs';
import { config } from '../config';

/** Restaura un respaldo sobre la base configurada. Uso: npm run restore -- ruta/al/archivo.dump --yes */
const [file, confirm] = process.argv.slice(2);
if (!file || !fs.existsSync(file)) { console.error('Indica el archivo .dump a restaurar.'); process.exit(1); }
if (confirm !== '--yes') {
  console.error('ATENCIÓN: se reemplazarán los datos actuales. Detén el servidor y repite el comando agregando --yes.');
  process.exit(1);
}
const res = spawnSync(process.env.PG_RESTORE_BIN ?? 'pg_restore', ['--clean', '--if-exists', '--no-owner', '--single-transaction', `--dbname=${config.databaseUrl}`, file], { stdio: 'inherit' });
process.exit(res.status ?? 1);
