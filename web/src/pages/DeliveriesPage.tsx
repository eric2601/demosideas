import { useState } from 'react';
import { Alert, Button, Modal, Popconfirm, Select, Space, Table, Typography } from 'antd';
import { CarOutlined, InboxOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { STOCK_KEYS, useAction, useDeliveryStaff } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { PageHeader, StatusTag } from '../components/common';
import { fmtDateTime } from '../utils/format';
import { DELIVERY_STATUS, ORDER_STATUS } from '../utils/status';
import type { DeliveryRow } from '../types';

export default function DeliveriesPage() {
  const navigate = useNavigate();
  const { user, can } = useAuth();
  const [dispatching, setDispatching] = useState<DeliveryRow | null>(null);
  const [assigned, setAssigned] = useState<number | undefined>();
  const [filter, setFilter] = useState<string | undefined>();
  const { data, isLoading, refetch, isFetching } = useQuery({ queryKey: ['deliveries'], queryFn: () => api.get<DeliveryRow[]>('/deliveries') });
  const { data: staff = [] } = useDeliveryStaff(!!dispatching);
  const canAct = can('orders.manage', 'deliveries.update');

  const dispatch = useAction(() => api.post(`/orders/${dispatching!.orderId}/dispatch`, assigned ? { assignedTo: assigned } : {}), {
    invalidate: STOCK_KEYS, success: 'Pedido despachado', onSuccess: () => setDispatching(null),
  });
  const deliver = useAction((orderId: number) => api.post(`/orders/${orderId}/deliver`), { invalidate: STOCK_KEYS, success: 'Pedido marcado como entregado' });

  const rows = (data ?? []).filter((r) => !filter || r.orderStatus === filter);

  return (
    <>
      <PageHeader title="Entregas" subtitle="Pedidos listos, en camino y entregados" onRefresh={() => void refetch()} loading={isFetching} />
      <Space style={{ marginBottom: 12 }}>
        <Select allowClear placeholder="Todos" value={filter} onChange={setFilter} style={{ width: 220 }}
          options={(['listo_entrega', 'en_entrega', 'entregado'] as const).map((v) => ({ value: v, label: ORDER_STATUS[v].label }))} />
      </Space>
      <Table
        size="middle" rowKey="orderId" loading={isLoading} dataSource={rows} scroll={{ x: 980 }} pagination={{ pageSize: 25, showTotal: (t) => `${t} entregas` }}
        columns={[
          { title: 'Folio', dataIndex: 'folio', render: (v, r) => <a onClick={() => navigate(`/pedidos/${r.orderId}`)}><b>{v}</b></a> },
          { title: 'Restaurante', dataIndex: 'restaurantName', render: (v, r) => <span>{v}<br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.restaurantAddress ?? ''}</Typography.Text></span> },
          { title: 'Pedido', dataIndex: 'orderStatus', render: (v) => <StatusTag map={ORDER_STATUS} value={v} /> },
          { title: 'Entrega', dataIndex: 'status', render: (v) => (v ? <StatusTag map={DELIVERY_STATUS} value={v} /> : <Typography.Text type="secondary">Por despachar</Typography.Text>) },
          { title: 'Repartidor', dataIndex: 'assignedToName', render: (v) => v ?? '—' },
          { title: 'Despachado', dataIndex: 'dispatchedAt', render: fmtDateTime },
          { title: 'Entregado', dataIndex: 'deliveredAt', render: fmtDateTime },
          { title: 'Acciones', key: 'a', fixed: 'right', width: 190,
            render: (_, r) => !canAct ? null : r.orderStatus === 'listo_entrega' ? (
              <Button size="small" type="primary" icon={<CarOutlined />} onClick={() => { setAssigned(user?.role === 'entrega' ? user.id : undefined); setDispatching(r); }}>Despachar</Button>
            ) : r.orderStatus === 'en_entrega' ? (
              <Popconfirm title={`¿Marcar ${r.folio} como entregado?`} okText="Entregado" cancelText="No" onConfirm={() => deliver.mutate(r.orderId)}>
                <Button size="small" type="primary" icon={<InboxOutlined />} loading={deliver.isPending && deliver.variables === r.orderId}>Marcar entregado</Button>
              </Popconfirm>
            ) : null },
        ]}
      />
      <Modal open={!!dispatching} title={`Despachar ${dispatching?.folio ?? ''}`} okText="Confirmar despacho" cancelText="Cancelar"
        onCancel={() => setDispatching(null)} onOk={() => dispatch.mutate()} confirmLoading={dispatch.isPending} destroyOnHidden>
        <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="Al despachar, la mercancía preparada sale definitivamente del inventario." />
        <Typography.Text>Repartidor responsable</Typography.Text>
        <Select allowClear style={{ width: '100%', marginTop: 6 }} placeholder="Selecciona un repartidor (opcional)" value={assigned} onChange={setAssigned}
          options={staff.map((s) => ({ value: s.id, label: s.fullName }))} />
      </Modal>
    </>
  );
}
