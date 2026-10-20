import { useState } from 'react';
import { Input, Select, Space, Table, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useRestaurants } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { PageHeader, StatusTag, useDebounced } from '../components/common';
import { fmtDateTime, fmtQty } from '../utils/format';
import { ORDER_STATUS, options } from '../utils/status';
import type { OrderSummary, Paged } from '../types';

export default function OrdersPage() {
  const navigate = useNavigate();
  const { can } = useAuth();
  const [status, setStatus] = useState<string | undefined>();
  const [restaurantId, setRestaurantId] = useState<number | undefined>();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const dq = useDebounced(q);
  const { data: restaurants = [] } = useRestaurants(can('restaurants.read'));
  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['orders', status, restaurantId, dq, page, pageSize],
    queryFn: () => api.get<Paged<OrderSummary>>('/orders', { status, restaurantId, q: dq, page, pageSize }),
  });
  const reset = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(1); };

  return (
    <>
      <PageHeader title="Pedidos" subtitle="Pedidos de los restaurantes" onRefresh={() => void refetch()} loading={isFetching} />
      <Space wrap style={{ marginBottom: 12 }}>
        <Input allowClear prefix={<SearchOutlined />} placeholder="Buscar por folio o restaurante" value={q} onChange={(e) => reset(setQ)(e.target.value)} style={{ width: 280 }} />
        <Select allowClear placeholder="Todos los estados" value={status} onChange={reset(setStatus)} style={{ width: 220 }} options={options(ORDER_STATUS)} />
        {can('restaurants.read') && (
          <Select
            allowClear showSearch optionFilterProp="label" placeholder="Todos los restaurantes" value={restaurantId} onChange={reset(setRestaurantId)}
            style={{ width: 240 }} options={restaurants.map((r) => ({ value: r.id, label: r.name }))}
          />
        )}
      </Space>
      <Table
        size="middle" rowKey="id" loading={isLoading} dataSource={data?.data} scroll={{ x: 960 }}
        pagination={{ current: page, pageSize, total: data?.total, showSizeChanger: true, onChange: (p, s) => { setPage(p); setPageSize(s); }, showTotal: (t) => `${t} pedidos` }}
        onRow={(r) => ({ onClick: () => navigate(`/pedidos/${r.id}`), className: 'row-click' })}
        columns={[
          { title: 'Folio', dataIndex: 'folio', render: (v) => <b>{v}</b> },
          { title: 'Restaurante', dataIndex: 'restaurantName' },
          { title: 'Estado', dataIndex: 'status', render: (v) => <StatusTag map={ORDER_STATUS} value={v} /> },
          { title: 'Productos', dataIndex: 'itemCount', align: 'right' },
          { title: 'Solicitado', dataIndex: 'qtyRequested', align: 'right', render: fmtQty },
          { title: 'Entregado', dataIndex: 'qtyDelivered', align: 'right', render: fmtQty },
          { title: 'Creado', dataIndex: 'createdAt', render: fmtDateTime },
          { title: 'Responsable', dataIndex: 'assignedToName', render: (v) => v ?? <Typography.Text type="secondary">Sin asignar</Typography.Text> },
        ]}
      />
    </>
  );
}
