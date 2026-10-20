import type { Request, Response, NextFunction, RequestHandler } from 'express';
import { z, ZodTypeAny } from 'zod';
import { badRequest } from './errors';

export const asyncHandler =
  (fn: (req: Request, res: Response, next: NextFunction) => Promise<unknown>): RequestHandler =>
  (req, res, next) => { fn(req, res, next).catch(next); };

export function parse<S extends ZodTypeAny>(schema: S, data: unknown): z.infer<S> {
  const result = schema.safeParse(data);
  if (!result.success) {
    throw badRequest('Datos inválidos', result.error.issues.map((i) => ({ path: i.path.join('.'), message: i.message })));
  }
  return result.data;
}

export const idParam = (req: Request, name = 'id'): number => {
  const n = Number(req.params[name]);
  if (!Number.isInteger(n) || n <= 0) throw badRequest(`Identificador inválido: ${name}`);
  return n;
};

export const qty = z.number().positive().max(1_000_000).multipleOf(0.001);
export const money = z.number().min(0).max(100_000_000);
export const pageParams = (q: Request['query']) => {
  const page = Math.max(1, Number(q.page) || 1);
  const pageSize = Math.min(200, Math.max(1, Number(q.pageSize) || 25));
  return { page, pageSize, offset: (page - 1) * pageSize };
};
