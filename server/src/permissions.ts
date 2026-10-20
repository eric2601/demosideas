export type RoleCode = 'admin' | 'almacen' | 'compras' | 'entrega' | 'restaurante';

export const PERMISSIONS = [
  'dashboard.read',
  'products.read', 'products.write',
  'inventory.read', 'inventory.adjust',
  'movements.read',
  'receipts.read', 'receipts.create',
  'suppliers.read', 'suppliers.write',
  'shortages.read', 'shortages.write',
  'purchases.read', 'purchases.write', 'purchases.authorize',
  'orders.read', 'orders.manage',
  'deliveries.read', 'deliveries.update',
  'restaurants.read', 'restaurants.write',
  'users.read', 'users.write',
  'settings.read', 'settings.write',
  'audit.read',
  'catalog.read', 'orders.create', 'orders.own', 'orders.confirm_reception',
] as const;
export type Permission = (typeof PERMISSIONS)[number];

const warehouse: Permission[] = [
  'dashboard.read', 'products.read', 'products.write', 'inventory.read', 'inventory.adjust',
  'movements.read', 'receipts.read', 'receipts.create', 'suppliers.read', 'shortages.read',
  'shortages.write', 'purchases.read', 'purchases.authorize', 'orders.read', 'orders.manage',
  'deliveries.read', 'deliveries.update', 'restaurants.read', 'settings.read',
];

export const ROLE_PERMISSIONS: Record<RoleCode, readonly Permission[]> = {
  admin: PERMISSIONS,
  almacen: warehouse,
  compras: [
    'dashboard.read', 'products.read', 'inventory.read', 'movements.read', 'receipts.read',
    'suppliers.read', 'suppliers.write', 'shortages.read', 'shortages.write',
    'purchases.read', 'purchases.write', 'settings.read',
  ],
  entrega: ['orders.read', 'deliveries.read', 'deliveries.update', 'settings.read'],
  restaurante: ['catalog.read', 'orders.create', 'orders.own', 'orders.confirm_reception'],
};

export const isStaffRole = (role: RoleCode) => role !== 'restaurante';
