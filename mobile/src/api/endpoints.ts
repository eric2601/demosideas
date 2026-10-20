import type { HttpClient } from './client';
import type {
  CatalogProduct, CreateOrderBody, HealthResponse, LoginResponse, NotificationsResponse,
  OrderDetail, OrderSummary, Paginated, ServerConfig, User,
} from '../types';

/** Funciones tipadas de la API REST. Recibe un cliente HTTP ya configurado. */
export function createApi(http: HttpClient) {
  const { request } = http;
  return {
    health: (baseUrl?: string, timeoutMs = 6000) =>
      request<HealthResponse>('/health', { auth: false, baseUrl, timeoutMs }),
    config: (baseUrl?: string) => request<ServerConfig>('/config', { auth: false, baseUrl }),

    login: (username: string, password: string) =>
      request<LoginResponse>('/auth/login', { method: 'POST', auth: false, body: { username, password } }),
    me: () => request<User>('/auth/me'),
    logout: () => request<void>('/auth/logout', { method: 'POST', timeoutMs: 5000 }),
    changePassword: (currentPassword: string, newPassword: string) =>
      request<void>('/auth/change-password', { method: 'POST', body: { currentPassword, newPassword } }),

    catalog: () => request<CatalogProduct[]>('/restaurant-catalog'),

    listOrders: (p: { status?: string; page?: number; pageSize?: number } = {}) =>
      request<Paginated<OrderSummary>>('/orders', { query: { status: p.status, page: p.page, pageSize: p.pageSize } }),
    getOrder: (id: number) => request<OrderDetail>(`/orders/${id}`),
    /** La clave se genera una vez por intento (ver lib/idempotency) y se reutiliza en cada reintento. */
    createOrder: (body: CreateOrderBody, idempotencyKey: string) =>
      request<OrderDetail>('/orders', { method: 'POST', body, headers: { 'Idempotency-Key': idempotencyKey } }),
    cancelOrder: (id: number, reason: string) =>
      request<OrderDetail>(`/orders/${id}/cancel`, { method: 'POST', body: { reason } }),
    confirmReception: (id: number) =>
      request<OrderDetail>(`/orders/${id}/confirm-reception`, { method: 'POST' }),

    notifications: (p: { unread?: boolean; page?: number; pageSize?: number } = {}) =>
      request<NotificationsResponse>('/notifications', {
        query: { unread: p.unread ? 'true' : undefined, page: p.page, pageSize: p.pageSize },
      }),
    markNotificationRead: (id: number) => request<void>(`/notifications/${id}/read`, { method: 'POST' }),
    markAllNotificationsRead: () => request<void>('/notifications/read-all', { method: 'POST' }),
  };
}

export type Api = ReturnType<typeof createApi>;
