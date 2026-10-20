import { Pool, PoolClient, types } from 'pg';
import { config } from './config';

// NUMERIC y BIGINT llegan como números (precisión de 3 decimales suficiente para el dominio)
types.setTypeParser(1700, (v) => parseFloat(v));
types.setTypeParser(20, (v) => parseInt(v, 10));

const camel = (s: string) => s.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase());
const camelizeRow = <T>(row: Record<string, unknown>): T =>
  Object.fromEntries(Object.entries(row).map(([k, v]) => [camel(k), v])) as T;

export const pool = new Pool({ connectionString: config.databaseUrl, max: 10 });

export type Row = Record<string, any>;

export interface Queryable {
  query<T = Row>(sql: string, params?: unknown[]): Promise<T[]>;
  one<T = Row>(sql: string, params?: unknown[]): Promise<T | null>;
}

/** Conexión transaccional. Los eventos en tiempo real se emiten solo tras el COMMIT. */
export class Tx implements Queryable {
  private afterCommitHooks: Array<() => void | Promise<void>> = [];
  constructor(private readonly client: PoolClient) {}

  async query<T = Row>(sql: string, params: unknown[] = []): Promise<T[]> {
    const res = await this.client.query(sql, params);
    return res.rows.map((r) => camelizeRow<T>(r));
  }
  async one<T = Row>(sql: string, params: unknown[] = []): Promise<T | null> {
    return (await this.query<T>(sql, params))[0] ?? null;
  }
  afterCommit(fn: () => void | Promise<void>) {
    this.afterCommitHooks.push(fn);
  }
  async runAfterCommit() {
    for (const fn of this.afterCommitHooks) {
      try { await fn(); } catch (err) { console.error('afterCommit falló', err); }
    }
  }
}

export const db: Queryable = {
  async query<T = Row>(sql: string, params: unknown[] = []) {
    const res = await pool.query(sql, params);
    return res.rows.map((r) => camelizeRow<T>(r));
  },
  async one<T = Row>(sql: string, params: unknown[] = []) {
    return (await this.query<T>(sql, params))[0] ?? null;
  },
};

export async function withTx<T>(fn: (tx: Tx) => Promise<T>): Promise<T> {
  const client = await pool.connect();
  const tx = new Tx(client);
  try {
    await client.query('BEGIN');
    const result = await fn(tx);
    await client.query('COMMIT');
    client.release();
    await tx.runAfterCommit();
    return result;
  } catch (err) {
    try { await client.query('ROLLBACK'); } catch { /* conexión ya rota */ }
    client.release();
    throw err;
  }
}
