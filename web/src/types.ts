// Tipos compartidos del contrato de la API (docs/API.md). Los campos llegan en camelCase.

export type RoleCode = 'admin' | 'almacen' | 'compras' | 'entrega' | 'restaurante';

export type Permission =
  | 'dashboard.read' | 'products.read' | 'products.write' | 'inventory.read' | 'inventory.adjust'
  | 'movements.read' | 'receipts.read' | 'receipts.create' | 'suppliers.read' | 'suppliers.write'
  | 'shortages.read' | 'shortages.write' | 'purchases.read' | 'purchases.write' | 'purchases.authorize'
  | 'orders.read' | 'orders.manage' | 'deliveries.read' | 'deliveries.update'
  | 'restaurants.read' | 'restaurants.write' | 'users.read' | 'users.write'
  | 'settings.read' | 'settings.write' | 'audit.read'
  | 'catalog.read' | 'orders.create' | 'orders.own' | 'orders.confirm_reception';

export interface SessionUser {
  id: number;
  username: string;
  fullName: string;
  role: RoleCode;
  roleName: string;
  mustChangePassword: boolean;
  restaurant: { id: number; name: string; code: string } | null;
  permissions: Permission[];
}

export interface LoginResponse { token: string; user: SessionUser }

export interface AppConfig {
  companyName: string;
  timezone: string;
  showPrices: boolean;
  notificationSound: boolean;
  serverTime: string;
}

export interface Paged<T> { data: T[]; total: number; page: number; pageSize: number }

// ───── Pedidos ─────
export type OrderStatus =
  | 'nuevo' | 'pendiente_revision' | 'en_preparacion' | 'parcialmente_surtido'
  | 'listo_entrega' | 'en_entrega' | 'entregado' | 'cancelado';

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
  assignedToName: string | null;
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
  unitPrice: number | null;
  qtyRequested: number;
  qtyAuthorized: number | null;
  qtyReserved: number;
  qtyPrepared: number;
  qtyDelivered: number;
  qtyUnreserved: number;
  qtyPending: number;
  availableNow: number;
}

export interface OrderEvent {
  id: number;
  event: string;
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus | null;
  note: string | null;
  changes: unknown;
  createdAt: string;
  userName: string | null;
}

export interface OrderDetail {
  id: number;
  folio: string;
  status: OrderStatus;
  notes: string | null;
  warehouseNotes: string | null;
  cancelReason: string | null;
  createdAt: string;
  updatedAt: string;
  deliveredAt: string | null;
  receivedAt: string | null;
  restaurantId: number;
  restaurantName: string;
  restaurantAddress: string | null;
  requestedByName: string;
  assignedToName: string | null;
  receivedByName: string | null;
  items: OrderItem[];
  events: OrderEvent[];
  delivery: { status: string; dispatchedAt: string | null; deliveredAt: string | null; receivedAt: string | null; assignedToName: string | null } | null;
}

export interface DeliveryRow {
  orderId: number;
  folio: string;
  orderStatus: OrderStatus;
  restaurantName: string;
  restaurantAddress: string | null;
  status: string | null;
  dispatchedAt: string | null;
  deliveredAt: string | null;
  receivedAt: string | null;
  assignedToName: string | null;
  assignedTo: number | null;
}

export interface DeliveryStaff { id: number; fullName: string }

// ───── Catálogo e inventario ─────
export type StockStatus = 'ok' | 'low' | 'out';

export interface Category { id: number; name: string; description: string | null; active: boolean }
export interface Unit { id: number; code: string; name: string; active: boolean }

export interface Product {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  categoryId: number;
  categoryName: string;
  unitId: number;
  unitName: string;
  unitCode: string;
  minStock: number;
  costUnit: number;
  salePrice: number | null;
  active: boolean;
  onHand: number;
  reserved: number;
  available: number;
  stockStatus: StockStatus;
}

export interface InventoryRow {
  productId: number;
  sku: string;
  name: string;
  categoryName: string;
  unitName: string;
  active: boolean;
  onHand: number;
  reserved: number;
  available: number;
  minStock: number;
  lastCountQty: number | null;
  lastCountDifference: number | null;
  lastCountedAt: string | null;
  lastMovementAt: string | null;
  stockStatus: StockStatus;
}

export type MovementType =
  | 'entrada_compra' | 'salida_pedido' | 'reserva' | 'liberacion_reserva'
  | 'ajuste_positivo' | 'ajuste_negativo' | 'devolucion' | 'correccion_autorizada';

export interface Movement {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  type: MovementType;
  quantity: number;
  deltaOnHand: number;
  deltaReserved: number;
  onHandBefore: number;
  onHandAfter: number;
  reservedBefore: number;
  reservedAfter: number;
  referenceType: string | null;
  referenceId: number | null;
  referenceFolio: string | null;
  reason: string | null;
  notes: string | null;
  createdAt: string;
  userName: string | null;
}

export interface Supplier {
  id: number;
  name: string;
  contact: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  active: boolean;
}

export interface ReceiptSummary {
  id: number;
  folio: string;
  receivedAt: string;
  invoiceRef: string | null;
  totalAmount: number;
  notes: string | null;
  supplierName: string;
  userName: string;
  purchaseFolio: string | null;
  itemCount: number;
}

export interface ReceiptDetail extends Omit<ReceiptSummary, 'itemCount'> {
  items: { productId: number; sku: string; productName: string; quantity: number; unitCost: number; lineTotal: number }[];
}

// ───── Faltantes y compras ─────
export type ShortageStatus = 'pendiente' | 'en_revision' | 'en_proceso_compra' | 'parcialmente_resuelto' | 'resuelto';
export type Priority = 'baja' | 'media' | 'alta';

export interface Shortage {
  id: number;
  folio: string;
  productId: number;
  sku: string;
  productName: string;
  unitName: string;
  quantityRequired: number;
  onHandSnapshot: number;
  availableSnapshot: number;
  quantityShort: number;
  reason: string;
  priority: Priority;
  status: ShortageStatus;
  notes: string | null;
  detectedAt: string;
  resolvedAt: string | null;
  currentOnHand: number;
  currentAvailable: number;
  purchaseOrders: { id: number; folio: string; status: PurchaseStatus }[];
}

export type PurchaseStatus =
  | 'borrador' | 'pendiente_autorizacion' | 'autorizada' | 'parcialmente_recibida' | 'recibida' | 'cancelada';

export interface PurchaseSuggestion {
  productId: number;
  sku: string;
  name: string;
  unitName: string;
  onHand: number;
  reserved: number;
  available: number;
  minStock: number;
  estimatedUnitCost: number;
  orderShort: number;
  shortage: number;
  incoming: number;
  suggested: number;
  estimatedAmount: number;
  shortageId: number | null;
  shortageStatus: ShortageStatus | null;
  lastSupplier: string | null;
  lastSupplierId: number | null;
}

export interface PurchaseSummary {
  id: number;
  folio: string;
  status: PurchaseStatus;
  supplierId: number;
  supplierName: string;
  expectedDate: string | null;
  notes: string | null;
  createdAt: string;
  submittedAt: string | null;
  authorizedAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  createdByName: string;
  authorizedByName: string | null;
  totalAmount: number;
  qtyOrdered: number;
  qtyReceived: number;
}

export interface PurchaseItem {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  unitName: string;
  qtyOrdered: number;
  qtyReceived: number;
  qtyPending: number;
  unitCost: number;
  originalShortage: number | null;
  lineTotal: number;
}

export interface PurchaseDetail extends PurchaseSummary {
  items: PurchaseItem[];
  shortages: { id: number; folio: string; status: ShortageStatus; productName: string }[];
  receipts: { id: number; folio: string; receivedAt: string; invoiceRef: string | null; totalAmount: number }[];
}

// ───── Administración ─────
export interface Restaurant {
  id: number;
  code: string;
  name: string;
  address: string | null;
  managerName: string;
  phone: string | null;
  active: boolean;
  createdAt: string;
  usernames: string | null;
}

export interface UserRow {
  id: number;
  username: string;
  fullName: string;
  active: boolean;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  lockedUntil: string | null;
  role: RoleCode;
  roleName: string;
  restaurantId: number | null;
  restaurantName: string | null;
}

export interface Role { id: number; code: RoleCode; name: string }

export interface AppNotification {
  id: number;
  type: string;
  title: string;
  body: string | null;
  payload: Record<string, any>;
  createdAt: string;
  read: boolean;
}

export interface NotificationsResponse { data: AppNotification[]; unreadCount: number }

export interface Settings {
  company_name: string;
  timezone: string;
  show_prices: boolean;
  notification_sound: boolean;
}

export interface AuditRow {
  id: number;
  action: string;
  entity: string | null;
  entityId: number | null;
  detail: unknown;
  ip: string | null;
  createdAt: string;
  username: string | null;
}

export interface DashboardData {
  cards: {
    totalProducts: number;
    withStock: number;
    lowStock: number;
    outOfStock: number;
    ordersPending: number;
    ordersPreparing: number;
    ordersReady: number;
    ordersDelivered: number;
    openShortages: number;
    pendingPurchases: number;
    receiptsToday: number;
  };
  recentOrders: { id: number; folio: string; status: OrderStatus; createdAt: string; restaurantName: string; itemCount: number }[];
  reorder: { productId: number; sku: string; name: string; onHand: number; reserved: number; available: number; minStock: number; stockStatus: 'low' | 'out' }[];
  ordersByStatus: { status: OrderStatus; count: number }[];
  movementsByDay: { day: string; entries: number; exits: number }[];
}

// ───── Tiempo real ─────
export interface OrderNewEvent { orderId: number; folio: string; restaurantId: number; restaurantName: string; itemCount: number }
export interface OrderUpdatedEvent { orderId: number; folio: string; status: OrderStatus; previousStatus: OrderStatus }
export interface NotificationNewEvent {
  id: number; type: string; title: string; body: string | null; payload: Record<string, any>; createdAt: string; read?: boolean;
}
export interface InventoryChangedEvent { productId: number; onHand: number; reserved: number }
