import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { Button, Result, Spin } from 'antd';
import { useAuth } from './auth/AuthContext';
import { AppLayout, NAV, useNavItems } from './components/AppLayout';
import LoginPage from './pages/LoginPage';
import { RealtimeProvider } from './realtime/RealtimeProvider';

const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const InventoryPage = lazy(() => import('./pages/InventoryPage'));
const ReceiptsPage = lazy(() => import('./pages/ReceiptsPage'));
const OrdersPage = lazy(() => import('./pages/OrdersPage'));
const OrderDetailPage = lazy(() => import('./pages/OrderDetailPage'));
const ShortagesPage = lazy(() => import('./pages/ShortagesPage'));
const PurchasesPage = lazy(() => import('./pages/PurchasesPage'));
const DeliveriesPage = lazy(() => import('./pages/DeliveriesPage'));
const MovementsPage = lazy(() => import('./pages/MovementsPage'));
const RestaurantsPage = lazy(() => import('./pages/RestaurantsPage'));
const ProductsPage = lazy(() => import('./pages/ProductsPage'));
const UsersPage = lazy(() => import('./pages/UsersPage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));

const PAGES: Record<string, JSX.Element> = {
  '/dashboard': <DashboardPage />,
  '/inventario': <InventoryPage />,
  '/entradas': <ReceiptsPage />,
  '/pedidos': <OrdersPage />,
  '/faltantes': <ShortagesPage />,
  '/compras': <PurchasesPage />,
  '/entregas': <DeliveriesPage />,
  '/movimientos': <MovementsPage />,
  '/restaurantes': <RestaurantsPage />,
  '/productos': <ProductsPage />,
  '/usuarios': <UsersPage />,
  '/configuracion': <SettingsPage />,
};

const Loading = () => <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>;

function Protected() {
  const { user, loading, logout } = useAuth();
  const items = useNavItems();
  if (loading) return <Loading />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role === 'restaurante') {
    return (
      <Result
        status="info" title="Usa la app Android"
        subTitle="Los usuarios de restaurante hacen sus pedidos desde la app Android; el panel web es solo para el personal del almacén."
        extra={<Button type="primary" onClick={() => void logout()}>Cerrar sesión</Button>}
      />
    );
  }
  const first = items[0]?.path;
  return (
    <RealtimeProvider>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={first ? <Navigate to={first} replace /> : <Result status="403" title="Sin módulos disponibles" subTitle="Tu rol no tiene permisos asignados en el panel." />} />
          {NAV.filter((n) => items.some((i) => i.path === n.path)).map((n) => (
            <Route key={n.path} path={n.path} element={<Suspense fallback={<Loading />}>{PAGES[n.path]}</Suspense>} />
          ))}
          {items.some((i) => i.path === '/pedidos') && (
            <Route path="/pedidos/:id" element={<Suspense fallback={<Loading />}><OrderDetailPage /></Suspense>} />
          )}
          <Route path="*" element={<Result status="404" title="Página no encontrada" extra={first && <Button type="primary" href={first}>Ir al inicio</Button>} />} />
        </Route>
      </Routes>
    </RealtimeProvider>
  );
}

export default function App() {
  const { loading } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={loading ? <Loading /> : <LoginPage />} />
      <Route path="/*" element={<Protected />} />
    </Routes>
  );
}
