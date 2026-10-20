import type { MovementType, OrderStatus, Priority, PurchaseStatus, ShortageStatus, StockStatus } from '../types';

export interface StatusInfo { label: string; color: string }

export const ORDER_STATUS: Record<OrderStatus, StatusInfo> = {
  nuevo: { label: 'Nuevo', color: 'blue' },
  pendiente_revision: { label: 'Pendiente de revisión', color: 'gold' },
  en_preparacion: { label: 'En preparación', color: 'cyan' },
  parcialmente_surtido: { label: 'Parcialmente surtido', color: 'orange' },
  listo_entrega: { label: 'Listo para entrega', color: 'lime' },
  en_entrega: { label: 'En entrega', color: 'geekblue' },
  entregado: { label: 'Entregado', color: 'green' },
  cancelado: { label: 'Cancelado', color: 'red' },
};

export const STOCK_STATUS: Record<StockStatus, StatusInfo> = {
  ok: { label: 'Disponible', color: 'green' },
  low: { label: 'Bajo stock', color: 'orange' },
  out: { label: 'Agotado', color: 'red' },
};

export const SHORTAGE_STATUS: Record<ShortageStatus, StatusInfo> = {
  pendiente: { label: 'Pendiente', color: 'red' },
  en_revision: { label: 'En revisión', color: 'gold' },
  en_proceso_compra: { label: 'En proceso de compra', color: 'purple' },
  parcialmente_resuelto: { label: 'Parcialmente resuelto', color: 'cyan' },
  resuelto: { label: 'Resuelto', color: 'green' },
};

export const PRIORITY: Record<Priority, StatusInfo> = {
  alta: { label: 'Alta', color: 'red' },
  media: { label: 'Media', color: 'orange' },
  baja: { label: 'Baja', color: 'default' },
};

export const PURCHASE_STATUS: Record<PurchaseStatus, StatusInfo> = {
  borrador: { label: 'Borrador', color: 'default' },
  pendiente_autorizacion: { label: 'Pendiente de autorización', color: 'gold' },
  autorizada: { label: 'Autorizada', color: 'blue' },
  parcialmente_recibida: { label: 'Parcialmente recibida', color: 'cyan' },
  recibida: { label: 'Recibida', color: 'green' },
  cancelada: { label: 'Cancelada', color: 'red' },
};

export const MOVEMENT_TYPE: Record<MovementType, StatusInfo> = {
  entrada_compra: { label: 'Entrada de compra', color: 'green' },
  salida_pedido: { label: 'Salida por pedido', color: 'volcano' },
  reserva: { label: 'Reserva', color: 'blue' },
  liberacion_reserva: { label: 'Liberación de reserva', color: 'geekblue' },
  ajuste_positivo: { label: 'Ajuste positivo', color: 'lime' },
  ajuste_negativo: { label: 'Ajuste negativo', color: 'orange' },
  devolucion: { label: 'Devolución', color: 'cyan' },
  correccion_autorizada: { label: 'Corrección autorizada', color: 'purple' },
};

export const DELIVERY_STATUS: Record<string, StatusInfo> = {
  en_ruta: { label: 'En ruta', color: 'geekblue' },
  entregada: { label: 'Entregada', color: 'green' },
};

export const ORDER_EVENT_LABEL: Record<string, string> = {
  creado: 'Pedido creado',
  cambio_estado: 'Cambio de estado',
  cantidades_modificadas: 'Cantidades modificadas',
  reserva_adicional: 'Reserva adicional',
  preparado: 'Preparación registrada',
  preparacion_actualizada: 'Preparación actualizada',
  despachado: 'Despachado',
  entregado: 'Entregado',
  recepcion_confirmada: 'Recepción confirmada',
  cancelado: 'Cancelado',
};

export const options = <T extends string>(map: Record<T, StatusInfo>) =>
  (Object.keys(map) as T[]).map((k) => ({ value: k, label: map[k].label }));

export const ROLE_LABEL: Record<string, string> = {
  admin: 'Administrador', almacen: 'Almacén', compras: 'Compras', entrega: 'Entrega', restaurante: 'Restaurante',
};
