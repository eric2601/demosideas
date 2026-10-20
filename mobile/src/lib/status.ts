import type { OrderStatus } from '../types';

export interface StatusInfo {
  label: string;
  /** color de texto/borde */
  color: string;
  /** color de fondo suave */
  bg: string;
}

export const STATUS_INFO: Record<OrderStatus, StatusInfo> = {
  nuevo: { label: 'Nuevo', color: '#1565C0', bg: '#E3F0FC' },
  pendiente_revision: { label: 'En revisión', color: '#8A5A00', bg: '#FFF1CC' },
  en_preparacion: { label: 'En preparación', color: '#3949AB', bg: '#E8EAF8' },
  parcialmente_surtido: { label: 'Surtido parcial', color: '#B45309', bg: '#FFE8D1' },
  listo_entrega: { label: 'Listo para entregar', color: '#00695C', bg: '#DDF3EF' },
  en_entrega: { label: 'En camino', color: '#6A1B9A', bg: '#F1E3F7' },
  entregado: { label: 'Entregado', color: '#2E7D32', bg: '#E1F3E2' },
  cancelado: { label: 'Cancelado', color: '#B3261E', bg: '#FCE4E2' },
};

export function statusInfo(status: string): StatusInfo {
  return STATUS_INFO[status as OrderStatus] ?? { label: status, color: '#444', bg: '#EEE' };
}

export const ALL_STATUSES = Object.keys(STATUS_INFO) as OrderStatus[];

/** Mensaje amable para avisos en pantalla cuando cambia el estado de un pedido. */
export function statusMessage(folio: string, status: string): string {
  switch (status) {
    case 'nuevo': return `Recibimos tu pedido ${folio}`;
    case 'pendiente_revision': return `El almacén está revisando tu pedido ${folio}`;
    case 'en_preparacion': return `Tu pedido ${folio} está en preparación`;
    case 'parcialmente_surtido': return `Tu pedido ${folio} se está surtiendo parcialmente`;
    case 'listo_entrega': return `Tu pedido ${folio} está listo para entregar`;
    case 'en_entrega': return `Tu pedido ${folio} va en camino`;
    case 'entregado': return `Tu pedido ${folio} fue entregado. Recuerda confirmar la recepción`;
    case 'cancelado': return `Tu pedido ${folio} fue cancelado`;
    default: return `Tu pedido ${folio} cambió de estado`;
  }
}

export const CANCELABLE: OrderStatus[] = ['nuevo', 'pendiente_revision'];

export const canCancel = (status: OrderStatus) => CANCELABLE.includes(status);

/** Se puede confirmar la recepción: en entrega, o entregado sin confirmar. */
export function canConfirmReception(status: OrderStatus, receivedAt: string | null | undefined): boolean {
  return status === 'en_entrega' || (status === 'entregado' && !receivedAt);
}

// Grupos para las tarjetas de la pantalla principal
export const COUNTER_GROUPS = {
  pendientes: ['nuevo', 'pendiente_revision'],
  enPreparacion: ['en_preparacion', 'parcialmente_surtido'],
  listos: ['listo_entrega', 'en_entrega'],
} as const satisfies Record<string, readonly OrderStatus[]>;

export interface OrderCounters {
  pendientes: number;
  enPreparacion: number;
  listos: number;
}

/** Suma conteos por estado en los tres grupos de la pantalla principal. */
export function computeCounters(byStatus: Partial<Record<string, number>>): OrderCounters {
  const sum = (list: readonly OrderStatus[]) => list.reduce((acc, s) => acc + (byStatus[s] ?? 0), 0);
  return {
    pendientes: sum(COUNTER_GROUPS.pendientes),
    enPreparacion: sum(COUNTER_GROUPS.enPreparacion),
    listos: sum(COUNTER_GROUPS.listos),
  };
}

/** Cuenta pedidos por estado a partir de una lista. */
export function countByStatus(orders: { status: string }[]): Record<string, number> {
  const out: Record<string, number> = {};
  for (const o of orders) out[o.status] = (out[o.status] ?? 0) + 1;
  return out;
}

export type FilterKey = 'todos' | 'pendientes' | 'enPreparacion' | 'listos' | 'entregados' | 'cancelados';

export const FILTERS: { key: FilterKey; label: string; statuses: OrderStatus[] }[] = [
  { key: 'todos', label: 'Todos', statuses: [] },
  { key: 'pendientes', label: 'Pendientes', statuses: [...COUNTER_GROUPS.pendientes] },
  { key: 'enPreparacion', label: 'En preparación', statuses: [...COUNTER_GROUPS.enPreparacion] },
  { key: 'listos', label: 'Listos', statuses: [...COUNTER_GROUPS.listos] },
  { key: 'entregados', label: 'Entregados', statuses: ['entregado'] },
  { key: 'cancelados', label: 'Cancelados', statuses: ['cancelado'] },
];
