import { Alert, Card, Col, Row, Space, Statistic, Table, Typography } from 'antd';
import {
  AppstoreOutlined, CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined, CarOutlined, InboxOutlined,
  ShoppingCartOutlined, WarningOutlined, ToolOutlined, FileDoneOutlined, ExclamationCircleOutlined, SendOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api/client';
import { PageHeader, StatusTag } from '../components/common';
import { useAuth } from '../auth/AuthContext';
import { fmtDateTime, fmtDay, fmtQty } from '../utils/format';
import { ORDER_STATUS, STOCK_STATUS } from '../utils/status';
import type { DashboardData, OrderStatus } from '../types';

// Colores hex de cada estado de pedido para la gráfica (alineados con las etiquetas)
const STATUS_HEX: Record<OrderStatus, string> = {
  nuevo: '#1677ff', pendiente_revision: '#d4b106', en_preparacion: '#13a8a8', parcialmente_surtido: '#fa8c16',
  listo_entrega: '#7cb305', en_entrega: '#2f54eb', entregado: '#389e0d', cancelado: '#cf1322',
};

export default function DashboardPage() {
  const navigate = useNavigate();
  const { can } = useAuth();
  const { data, isLoading, refetch, isFetching } = useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<DashboardData>('/dashboard') });
  const c = data?.cards;

  const stat = (title: string, value: number | undefined, icon: React.ReactNode, color: string, to?: string) => (
    <Col xs={12} md={8} xl={6} xxl={4} key={title}>
      <Card size="small" hoverable={!!to} onClick={to ? () => navigate(to) : undefined} loading={isLoading} styles={{ body: { padding: 14 } }}>
        <Statistic title={title} value={value ?? 0} prefix={icon} valueStyle={{ color, fontSize: 26 }} />
      </Card>
    </Col>
  );

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Resumen operativo del almacén" onRefresh={() => void refetch()} loading={isFetching} />

      <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}>
        {!!c?.outOfStock && (
          <Alert type="error" showIcon message={`${c.outOfStock} producto(s) agotado(s)`} action={can('inventory.read') ? <a onClick={() => navigate('/inventario')}>Ver inventario</a> : undefined} />
        )}
        {!!c?.lowStock && (
          <Alert type="warning" showIcon message={`${c.lowStock} producto(s) con bajo stock`} />
        )}
        {!!c?.ordersPending && (
          <Alert type="info" showIcon message={`${c.ordersPending} pedido(s) esperan revisión`} action={<a onClick={() => navigate('/pedidos')}>Ver pedidos</a>} />
        )}
        {!!c?.openShortages && (
          <Alert type="warning" showIcon message={`${c.openShortages} faltante(s) abierto(s)`} action={can('shortages.read') ? <a onClick={() => navigate('/faltantes')}>Ver faltantes</a> : undefined} />
        )}
      </Space>

      <Row gutter={[12, 12]}>
        {stat('Total de productos', c?.totalProducts, <AppstoreOutlined />, '#1677ff', '/inventario')}
        {stat('Con existencia', c?.withStock, <CheckCircleOutlined />, '#389e0d', '/inventario')}
        {stat('Bajo stock', c?.lowStock, <WarningOutlined />, '#d46b08', '/inventario')}
        {stat('Agotados', c?.outOfStock, <CloseCircleOutlined />, '#cf1322', '/inventario')}
        {stat('Pedidos pendientes', c?.ordersPending, <ClockCircleOutlined />, '#d4b106', '/pedidos')}
        {stat('En preparación', c?.ordersPreparing, <ToolOutlined />, '#13a8a8', '/pedidos')}
        {stat('Listos para entrega', c?.ordersReady, <SendOutlined />, '#7cb305', '/pedidos')}
        {stat('Pedidos entregados', c?.ordersDelivered, <CarOutlined />, '#389e0d', '/entregas')}
        {stat('Faltantes abiertos', c?.openShortages, <ExclamationCircleOutlined />, '#cf1322', '/faltantes')}
        {stat('Compras pendientes', c?.pendingPurchases, <ShoppingCartOutlined />, '#722ed1', '/compras')}
        {stat('Entradas del día', c?.receiptsToday, <InboxOutlined />, '#1677ff', '/entradas')}
        {stat('Pedidos en total', (data?.ordersByStatus ?? []).reduce((a, b) => a + b.count, 0), <FileDoneOutlined />, '#595959', '/pedidos')}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={10}>
          <Card title="Pedidos por estado" loading={isLoading} size="small">
            {data && data.ordersByStatus.length ? (
              <ResponsiveContainer width="100%" height={260}>
                <PieChart>
                  <Pie isAnimationActive={false} data={data.ordersByStatus.map((s) => ({ ...s, name: ORDER_STATUS[s.status]?.label ?? s.status }))} dataKey="count" nameKey="name" innerRadius={55} outerRadius={90} paddingAngle={2} label={(e) => e.value}>
                    {data.ordersByStatus.map((s) => <Cell key={s.status} fill={STATUS_HEX[s.status] ?? '#999'} />)}
                  </Pie>
                  <Tooltip />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            ) : <Typography.Text type="secondary">Aún no hay pedidos.</Typography.Text>}
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card title="Entradas y salidas de los últimos 7 días" loading={isLoading} size="small">
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={(data?.movementsByDay ?? []).map((d) => ({ ...d, label: fmtDay(d.day), entries: Number(d.entries), exits: Number(d.exits) }))}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="label" fontSize={12} />
                <YAxis fontSize={12} />
                <Tooltip formatter={(v: number) => fmtQty(v)} />
                <Legend />
                <Bar isAnimationActive={false} dataKey="entries" name="Entradas" fill="#389e0d" radius={[3, 3, 0, 0]} />
                <Bar isAnimationActive={false} dataKey="exits" name="Salidas" fill="#d46b08" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} xl={12}>
          <Card title="Pedidos recientes" size="small" extra={<a onClick={() => navigate('/pedidos')}>Ver todos</a>}>
            <Table
              size="small" rowKey="id" pagination={false} loading={isLoading} dataSource={data?.recentOrders} scroll={{ x: 520 }}
              onRow={(r) => ({ onClick: () => navigate(`/pedidos/${r.id}`), className: 'row-click' })}
              columns={[
                { title: "Folio", dataIndex: "folio", render: (v) => <b style={{ whiteSpace: "nowrap" }}>{v}</b> },
                { title: 'Restaurante', dataIndex: 'restaurantName' },
                { title: 'Productos', dataIndex: 'itemCount', align: 'right' },
                { title: 'Estado', dataIndex: 'status', render: (v) => <StatusTag map={ORDER_STATUS} value={v} /> },
                { title: 'Fecha', dataIndex: 'createdAt', render: fmtDateTime },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card title="Productos a reponer" size="small" extra={can('purchases.read') ? <a onClick={() => navigate('/compras')}>Ir a compras</a> : undefined}>
            <Table
              size="small" rowKey="productId" pagination={false} loading={isLoading} dataSource={data?.reorder} scroll={{ x: 520 }}
              columns={[
                { title: 'Producto', dataIndex: 'name', render: (v, r) => <span>{v}<br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.sku}</Typography.Text></span> },
                { title: 'Disponible', dataIndex: 'available', align: 'right', render: fmtQty },
                { title: 'Mínimo', dataIndex: 'minStock', align: 'right', render: fmtQty },
                { title: 'Estado', dataIndex: 'stockStatus', render: (v) => <StatusTag map={STOCK_STATUS} value={v} /> },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </>
  );
}
