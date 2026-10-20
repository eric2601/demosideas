import {
  canCancel, canConfirmReception, computeCounters, countByStatus, statusMessage, FILTERS,
} from '../src/lib/status';

describe('contadores por estado', () => {
  it('agrupa pendientes, en preparación y listos', () => {
    const c = computeCounters({
      nuevo: 2, pendiente_revision: 1, en_preparacion: 3, parcialmente_surtido: 2,
      listo_entrega: 4, en_entrega: 1, entregado: 50, cancelado: 7,
    });
    expect(c).toEqual({ pendientes: 3, enPreparacion: 5, listos: 5 });
  });
  it('estados ausentes cuentan 0', () => {
    expect(computeCounters({})).toEqual({ pendientes: 0, enPreparacion: 0, listos: 0 });
  });
  it('cuenta pedidos de una lista', () => {
    expect(countByStatus([{ status: 'nuevo' }, { status: 'nuevo' }, { status: 'entregado' }]))
      .toEqual({ nuevo: 2, entregado: 1 });
  });
});

describe('reglas de acciones', () => {
  it('solo se cancela en nuevo o pendiente_revision', () => {
    expect(canCancel('nuevo')).toBe(true);
    expect(canCancel('pendiente_revision')).toBe(true);
    expect(canCancel('en_preparacion')).toBe(false);
    expect(canCancel('en_entrega')).toBe(false);
  });
  it('confirmar recepción: en_entrega, o entregado sin receivedAt', () => {
    expect(canConfirmReception('en_entrega', null)).toBe(true);
    expect(canConfirmReception('entregado', null)).toBe(true);
    expect(canConfirmReception('entregado', '2026-01-01T00:00:00Z')).toBe(false);
    expect(canConfirmReception('listo_entrega', null)).toBe(false);
  });
  it('mensajes de aviso', () => {
    expect(statusMessage('PED-000001', 'listo_entrega')).toBe('Tu pedido PED-000001 está listo para entregar');
  });
  it('los filtros agrupan los estados esperados', () => {
    expect(FILTERS.find((f) => f.key === 'listos')!.statuses).toEqual(['listo_entrega', 'en_entrega']);
  });
});
