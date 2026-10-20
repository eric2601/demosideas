import { spawnSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { config } from '../config';

/** Respaldo con pg_dump (formato custom, comprimido). Conserva los últimos BACKUP_KEEP archivos. */
const keep = Number(process.env.BACKUP_KEEP ?? 14);
fs.mkdirSync(config.backupDir, { recursive: true });
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const file = path.join(config.backupDir, `almacen-${stamp}.dump`);

const res = spawnSync(process.env.PG_DUMP_BIN ?? 'pg_dump', ['--format=custom', '--no-owner', `--file=${file}`, config.databaseUrl], { stdio: 'inherit' });
if (res.status !== 0) { console.error('El respaldo falló. ¿Está instalado pg_dump y accesible la base de datos?'); process.exit(1); }
console.log(`Respaldo creado: ${file}`);

const old = fs.readdirSync(config.backupDir).filter((f) => f.endsWith('.dump')).sort().reverse().slice(keep);
for (const f of old) { fs.unlinkSync(path.join(config.backupDir, f)); console.log(`Respaldo antiguo eliminado: ${f}`); }
