import { Queryable } from '../db';

export async function audit(
  q: Queryable,
  entry: { userId?: number | null; action: string; entity?: string; entityId?: string | number; detail?: unknown; ip?: string | null },
) {
  await q.query(
    `INSERT INTO audit_logs (user_id, action, entity, entity_id, detail, ip) VALUES ($1,$2,$3,$4,$5,$6)`,
    [entry.userId ?? null, entry.action, entry.entity ?? null, entry.entityId?.toString() ?? null,
     entry.detail ? JSON.stringify(entry.detail) : null, entry.ip ?? null],
  );
}
