import type { Request, Response, NextFunction } from 'express';
import { AppError } from '../errors';

export function notFoundHandler(_req: Request, res: Response) {
  res.status(404).json({ error: { code: 'NOT_FOUND', message: 'Ruta no encontrada' } });
}

export function errorHandler(err: any, _req: Request, res: Response, _next: NextFunction) {
  if (err instanceof AppError) {
    return res.status(err.status).json({ error: { code: err.code, message: err.message, details: err.details } });
  }
  if (err?.type === 'entity.parse.failed') {
    return res.status(400).json({ error: { code: 'BAD_REQUEST', message: 'JSON mal formado' } });
  }
  // Violaciones de integridad de PostgreSQL
  if (err?.code === '23505') {
    return res.status(409).json({ error: { code: 'DUPLICATE', message: 'Ya existe un registro con esos datos', details: err.detail } });
  }
  if (err?.code === '23503') {
    return res.status(409).json({ error: { code: 'REFERENCE_ERROR', message: 'La operación viola una relación con otros registros' } });
  }
  if (err?.code === '23514' || err?.code === '23000') {
    return res.status(409).json({ error: { code: 'CONSTRAINT', message: 'La operación viola una regla de integridad de datos' } });
  }
  console.error('Error no controlado:', err);
  res.status(500).json({ error: { code: 'INTERNAL', message: 'Error interno del servidor' } });
}
