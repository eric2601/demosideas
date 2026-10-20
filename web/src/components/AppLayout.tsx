import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Avatar, Badge, Button, Drawer, Dropdown, Grid, Layout, Menu, Space, Typography } from 'antd';
import {
  AppstoreOutlined, BarChartOutlined, CarOutlined, DashboardOutlined, DatabaseOutlined, HistoryOutlined,
  InboxOutlined, LogoutOutlined, MenuOutlined, SettingOutlined, ShopOutlined, ShoppingCartOutlined,
  TeamOutlined, UnorderedListOutlined, UserOutlined, WarningOutlined, KeyOutlined,
} from '@ant-design/icons';
import { useAuth } from '../auth/AuthContext';
import { useAppConfig } from '../api/hooks';
import { useRealtime, type ConnectionState } from '../realtime/RealtimeProvider';
import { NotificationBell } from './NotificationBell';
import { ChangePasswordModal } from './ChangePasswordModal';
import { ROLE_LABEL } from '../utils/status';
import type { Permission } from '../types';

export interface NavItem { path: string; label: string; icon: ReactNode; perms: Permission[] }

export const NAV: NavItem[] = [
  { path: '/dashboard', label: 'Dashboard', icon: <DashboardOutlined />, perms: ['dashboard.read'] },
  { path: '/inventario', label: 'Inventario', icon: <DatabaseOutlined />, perms: ['inventory.read'] },
  { path: '/entradas', label: 'Entradas de mercancía', icon: <InboxOutlined />, perms: ['receipts.read'] },
  { path: '/pedidos', label: 'Pedidos', icon: <UnorderedListOutlined />, perms: ['orders.read'] },
  { path: '/faltantes', label: 'Faltantes', icon: <WarningOutlined />, perms: ['shortages.read'] },
  { path: '/compras', label: 'Compras', icon: <ShoppingCartOutlined />, perms: ['purchases.read'] },
  { path: '/entregas', label: 'Entregas', icon: <CarOutlined />, perms: ['deliveries.read'] },
  { path: '/movimientos', label: 'Historial de movimientos', icon: <HistoryOutlined />, perms: ['movements.read'] },
  { path: '/restaurantes', label: 'Restaurantes', icon: <ShopOutlined />, perms: ['restaurants.read'] },
  { path: '/productos', label: 'Catálogo de productos', icon: <AppstoreOutlined />, perms: ['products.read'] },
  { path: '/usuarios', label: 'Usuarios', icon: <TeamOutlined />, perms: ['users.read'] },
  { path: '/configuracion', label: 'Configuración', icon: <SettingOutlined />, perms: ['settings.read'] },
];

const CONN: Record<ConnectionState, { status: 'success' | 'warning' | 'error'; text: string }> = {
  connected: { status: 'success', text: 'En vivo' },
  connecting: { status: 'warning', text: 'Reconectando…' },
  disconnected: { status: 'error', text: 'Sin conexión' },
};

export function useNavItems() {
  const { can } = useAuth();
  return useMemo(() => NAV.filter((n) => can(...n.perms)), [can]);
}

export function AppLayout() {
  const { user, logout } = useAuth();
  const { data: config } = useAppConfig();
  const { connection } = useRealtime();
  const screens = Grid.useBreakpoint();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const items = useNavItems();
  const [collapsed, setCollapsed] = useState(false);
  const [drawer, setDrawer] = useState(false);
  const [pwdOpen, setPwdOpen] = useState(false);
  const [forcedShown, setForcedShown] = useState(false);

  const isMobile = !screens.md;
  const isTablet = !!screens.md && !screens.lg;
  useEffect(() => { setCollapsed(isTablet); }, [isTablet]);

  // Invitar a cambiar la contraseña temporal una sola vez por sesión de pantalla
  useEffect(() => {
    if (user?.mustChangePassword && !forcedShown) { setPwdOpen(true); setForcedShown(true); }
  }, [user?.mustChangePassword, forcedShown]);

  const selected = items.find((i) => pathname === i.path || pathname.startsWith(i.path + '/'))?.path;
  const menu = (
    <Menu
      mode="inline" theme={isMobile ? 'light' : 'dark'} selectedKeys={selected ? [selected] : []}
      items={items.map((i) => ({ key: i.path, icon: i.icon, label: i.label }))}
      onClick={({ key }) => { navigate(key); setDrawer(false); }}
    />
  );
  const brand = (
    <div style={{ height: 56, display: 'flex', alignItems: 'center', gap: 10, padding: '0 20px', color: isMobile ? undefined : '#fff', overflow: 'hidden', whiteSpace: 'nowrap' }}>
      <BarChartOutlined style={{ fontSize: 22 }} />
      {(!collapsed || isMobile) && <Typography.Text strong style={{ color: 'inherit', fontSize: 16 }}>{config?.companyName ?? 'Almacén'}</Typography.Text>}
    </div>
  );
  const conn = CONN[connection];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {!isMobile && (
        <Layout.Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} width={248} breakpoint="lg" style={{ position: 'sticky', top: 0, height: '100vh', overflow: 'auto' }}>
          {brand}
          {menu}
        </Layout.Sider>
      )}
      {isMobile && (
        <Drawer placement="left" open={drawer} onClose={() => setDrawer(false)} width={270} styles={{ body: { padding: 0 } }} title={config?.companyName ?? 'Almacén'}>
          {menu}
        </Drawer>
      )}
      <Layout>
        <Layout.Header style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '0 16px', position: 'sticky', top: 0, zIndex: 10, background: 'var(--hdr-bg, #fff)', borderBottom: '1px solid rgba(5,5,5,0.08)' }}>
          {isMobile && <Button type="text" aria-label="Menú" icon={<MenuOutlined />} onClick={() => setDrawer(true)} />}
          <div style={{ flex: 1 }} />
          <Badge status={conn.status} text={<span data-testid="conn-status" style={{ fontSize: 13 }}>{conn.text}</span>} />
          <NotificationBell />
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                { key: 'info', disabled: true, label: <span>{user?.fullName}<br /><Typography.Text type="secondary">{user ? ROLE_LABEL[user.role] ?? user.roleName : ''}</Typography.Text></span> },
                { type: 'divider' },
                { key: 'pwd', icon: <KeyOutlined />, label: 'Cambiar contraseña', onClick: () => setPwdOpen(true) },
                { key: 'out', icon: <LogoutOutlined />, label: 'Cerrar sesión', onClick: () => { void logout(); } },
              ],
            }}
          >
            <Space style={{ cursor: 'pointer' }} data-testid="user-menu">
              <Avatar size="small" icon={<UserOutlined />} />
              {!isMobile && <span>{user?.fullName}</span>}
            </Space>
          </Dropdown>
        </Layout.Header>
        <Layout.Content style={{ padding: isMobile ? 12 : 24, minWidth: 0 }}>
          {user?.mustChangePassword && (
            <Alert
              type="warning" showIcon style={{ marginBottom: 16 }}
              message="Estás usando una contraseña temporal"
              description="Cámbiala para proteger tu cuenta."
              action={<Button size="small" type="primary" onClick={() => setPwdOpen(true)}>Cambiar ahora</Button>}
            />
          )}
          {connection === 'disconnected' && (
            <Alert type="error" showIcon style={{ marginBottom: 16 }} message="Sin conexión en tiempo real"
              description="Los avisos de pedidos nuevos no llegarán hasta que se restablezca la conexión. Se reintenta automáticamente." />
          )}
          <Outlet />
        </Layout.Content>
      </Layout>
      <ChangePasswordModal open={pwdOpen} onClose={() => setPwdOpen(false)} forced={user?.mustChangePassword} />
    </Layout>
  );
}
