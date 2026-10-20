// Tipos del contrato de la API (docs/API.md). Fechas ISO-8601 en UTC.

export type OrderStatus =
  | 'nuevo'
  | 'pendiente_revision'
  | 'en_preparacion'
  | 'parcialmente_surtido'
  | 'listo_entrega'
  | 'en_entrega'
  | 'entregado'
  | 'cancelado';

export type StockStatus = 'ok' | 'low' | 'out';

export interface Restaurant {
  id: number;
  name: string;
  code: string;
}

export interface User {
  id: number;
  username: string;
  fullName: string;
  role: string;
  roleName: string;
  mustChangePassword: boolean;
  restaurant: Restaurant | null;
  permissions: string[];
}

export interface LoginResponse {
  token: string;
  user: User;
}

export interface ServerConfig {
  companyName: string;
  timezone: string;
  showPrices: boolean;
  notificationSound?: boolean;
  serverTime: string;
}

export interface HealthResponse {
  status: string;
  time: string;
}

export interface CatalogProduct {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  categoryId: number;
  categoryName: string;
  unitName: string;
  /** Solo viene si el almacén habilitó mostrar precios */
  price?: number | null;
  /** Cantidad disponible confirmada por el servidor */
  available: number;
  stockStatus: StockStatus;
}

export interface Paginated<T> {
  data: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface OrderSummary {
  id: number;
  folio: string;
  status: OrderStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  deliveredAt: string | null;
  receivedAt: string | null;
  restaurantId: number;
  restaurantName: string;
  requestedByName: string;
  itemCount: number;
  qtyRequested: number;
  qtyDelivered: number;
}

export interface OrderItem {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  unitName: string;
  unitPrice?: number | null;
  qtyRequested: number;
  qtyAuthorized: number | null;
  qtyPrepared: number;
  qtyDelivered: number;
  qtyPending: number;
}

export interface OrderEvent {
  id: number;
  event: string;
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus | null;
  note: string | null;
  createdAt: string;
  userName: string | null;
}

export interface OrderDetail {
  id: number;
  folio: string;
  status: OrderStatus;
  notes: string | null;
  warehouseNotes?: string | null;
  cancelReason: string | null;
  createdAt: string;
  updatedAt: string;
  deliveredAt: string | null;
  receivedAt: string | null;
  receivedByName: string | null;
  restaurantId: number;
  restaurantName: string;
  requestedByName: string;
  items: OrderItem[];
  events: OrderEvent[];
  delivery: {
    status: string | null;
    dispatchedAt: string | null;
    deliveredAt: string | null;
    receivedAt: string | null;
    assignedToName: string | null;
  } | null;
}

export interface CreateOrderBody {
  items: { productId: number; quantity: number }[];
  notes?: string;
}

export interface AppNotification {
  id: number;
  type: string;
  title: string;
  body: string | null;
  payload: Record<string, unknown>;
  createdAt: string;
  read: boolean;
}

export interface NotificationsResponse {
  data: AppNotification[];
  unreadCount: number;
}

// Eventos Socket.IO
export interface OrderUpdatedEvent {
  orderId: number;
  folio: string;
  status: OrderStatus;
  previousStatus: OrderStatus;
}
