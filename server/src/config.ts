import dotenv from 'dotenv';
import path from 'path';

dotenv.config({ path: path.resolve(__dirname, '../.env') });

const isProduction = process.env.NODE_ENV === 'production';

function required(name: string, devFallback?: string): string {
  const value = process.env[name] ?? (isProduction ? undefined : devFallback);
  if (!value) throw new Error(`Falta la variable de entorno ${name}`);
  return value;
}

export const config = {
  isProduction,
  isTest: process.env.NODE_ENV === 'test',
  host: process.env.HOST ?? '0.0.0.0',
  port: Number(process.env.PORT ?? 3000),
  databaseUrl: required('DATABASE_URL', 'postgres://almacen:almacen_dev@localhost:5432/almacen'),
  jwtSecret: required('JWT_SECRET', 'solo-para-desarrollo-cambiar-en-produccion'),
  jwtExpiresIn: process.env.JWT_EXPIRES_IN ?? '12h',
  bcryptRounds: Number(process.env.BCRYPT_ROUNDS ?? 12),
  corsOrigins: (process.env.CORS_ORIGINS ?? '').split(',').map((s) => s.trim()).filter(Boolean),
  webDistDir: process.env.WEB_DIST_DIR ?? path.resolve(__dirname, '../../web/dist'),
  backupDir: process.env.BACKUP_DIR ?? path.resolve(__dirname, '../../backups'),
  loginMaxAttempts: Number(process.env.LOGIN_MAX_ATTEMPTS ?? 10),
};

if (isProduction && config.jwtSecret.length < 32) {
  throw new Error('JWT_SECRET debe tener al menos 32 caracteres en producción');
}
