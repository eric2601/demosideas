import { useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query';
import { App } from 'antd';
import { api } from './client';
import type {
  AppConfig, Category, DeliveryStaff, Product, Restaurant, Supplier, Unit,
} from '../types';

/** Configuración pública (zona horaria, mostrar precios, sonido). */
export const useAppConfig = () =>
  useQuery({ queryKey: ['config'], queryFn: () => api.get<AppConfig>('/config'), staleTime: 60_000 });

export const useProducts = (enabled = true) =>
  useQuery({ queryKey: ['products', 'all'], queryFn: () => api.get<Product[]>('/products'), enabled });
export const useCategories = (enabled = true) =>
  useQuery({ queryKey: ['categories'], queryFn: () => api.get<Category[]>('/categories'), enabled });
export const useUnits = (enabled = true) =>
  useQuery({ queryKey: ['units'], queryFn: () => api.get<Unit[]>('/units'), enabled });
export const useSuppliers = (enabled = true) =>
  useQuery({ queryKey: ['suppliers'], queryFn: () => api.get<Supplier[]>('/suppliers'), enabled });
export const useRestaurants = (enabled = true) =>
  useQuery({ queryKey: ['restaurants'], queryFn: () => api.get<Restaurant[]>('/restaurants'), enabled });
export const useDeliveryStaff = (enabled = true) =>
  useQuery({ queryKey: ['delivery-staff'], queryFn: () => api.get<DeliveryStaff[]>('/delivery-staff'), enabled });

interface ActionOptions<T, V> {
  /** Prefijos de queryKey a invalidar al terminar. */
  invalidate?: QueryKey[];
  success?: string | ((data: T, vars: V) => string);
  onSuccess?: (data: T, vars: V) => void;
}

/** Mutación con invalidación y aviso de éxito; los errores los muestra el MutationCache global. */
export function useAction<T = unknown, V = void>(fn: (vars: V) => Promise<T>, opts: ActionOptions<T, V> = {}) {
  const qc = useQueryClient();
  const { message } = App.useApp();
  return useMutation({
    mutationFn: fn,
    onSuccess: (data, vars) => {
      for (const key of opts.invalidate ?? []) qc.invalidateQueries({ queryKey: key });
      const text = typeof opts.success === 'function' ? opts.success(data, vars) : opts.success;
      if (text) message.success(text);
      opts.onSuccess?.(data, vars);
    },
  });
}

/** Claves que dependen de pedidos/inventario: se invalidan juntas. */
export const STOCK_KEYS: QueryKey[] = [
  ['orders'], ['order'], ['dashboard'], ['inventory'], ['products'], ['shortages'], ['purchases'], ['suggestions'],
  ['deliveries'], ['movements'], ['receipts'], ['notifications'],
];
