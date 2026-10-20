import { useEffect, useState, type ReactNode } from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Result, Select, Skeleton, Space, Table, Tag, Timeline, Typography } from 'antd';
import {
  ArrowLeftOutlined, CarOutlined, CheckCircleOutlined, CloseCircleOutlined, EditOutlined, EyeOutlined,
  InboxOutlined, ReloadOutlined, ToolOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { STOCK_KEYS, useAction, useAppConfig, useDeliveryStaff } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { KeyValue, StatusTag } from '../components/common';
import { fmtDateTime, fmtMoney, fmtQty } from '../utils/format';
import { ORDER_EVENT_LABEL, ORDER_STATUS } from '../utils/status';
import type { OrderDetail, OrderEvent, OrderItem } from '../types';

const target = (i: OrderItem) => i.qtyAuthorized ?? i.qtyRequested;

// ───── Modales de acción ─────
function AcceptModal({ order, open, onClose }: { order: OrderDetail; open: boolean; onClose: () => void }) {
  const [qtys, setQtys] = useState<Record<number, number>>({});
  const [note, setNote] = useState('');
  const isNew = ['nuevo', 'pendiente_revision'].includes(order.status);
  useEffect(() => {
    if (open) { setQtys(Object.fromEntries(order.items.map((i) => [i.id, target(i)]))); setNote(''); }
  }, [open, order]);
  const accept = useAction(
    () => api.post(`/orders/${order.id}/accept`, { items: order.items.map((i) => ({ itemId: i.id, authorizedQuantity: qtys[i.id] ?? target(i) })), note: note || undefined }),
    { invalidate: STOCK_KEYS, success: isNew ? 'Pedido aceptado y reservado' : 'Cantidades actualizadas', onSuccess: onClose },
  );
  const changed = order.items.some((i) => (qtys[i.id] ?? target(i)) !== i.qtyRequested);
  return (
    <Modal
      open={open} onCancel={onClose} width={820} title={isNew ? `Aceptar pedido ${order.folio}` : `Editar cantidades autorizadas ${order.folio}`}
      okText={isNew ? 'Aceptar pedido' : 'Guardar cantidades'} cancelText="Cancelar" onOk={() => accept.mutate()} confirmLoading={accept.isPending} destroyOnHidden
    >
      <Typography.Paragraph type="secondary">
        Ajusta la cantidad autorizada de cada partida (no puede exceder lo solicitado). Se reservará la existencia disponible.
      </Typography.Paragraph>
      <Table
        size="small" rowKey="id" pagination={false} dataSource={order.items} scroll={{ x: 560 }}
        columns={[
          { title: 'Producto', dataIndex: 'productName' },
          { title: 'Solicitada', dataIndex: 'qtyRequested', align: 'right', render: fmtQty },
          { title: 'Disponible', dataIndex: 'availableNow', align: 'right', render: fmtQty },
          { title: 'Autorizar', key: 'q', align: 'right', width: 170,
            render: (_, i) => (
              <InputNumber
                min={i.qtyPrepared} max={i.qtyRequested} value={qtys[i.id]} suffix={i.unitName} style={{ width: 150 }}
                onChange={(v) => setQtys((s) => ({ ...s, [i.id]: v ?? 0 }))}
              />) },
        ]}
      />
      {changed && <Alert style={{ marginTop: 12 }} type="info" showIcon message="Se notificará al restaurante el cambio de cantidades." />}
      <Input.TextArea style={{ marginTop: 12 }} rows={2} maxLength={300} placeholder="Nota (opcional)" value={note} onChange={(e) => setNote(e.target.value)} />
    </Modal>
  );
}

function PrepareModal({ order, open, onClose }: { order: OrderDetail; open: boolean; onClose: () => void }) {
  const [qtys, setQtys] = useState<Record<number, number>>({});
  const [note, setNote] = useState('');
  useEffect(() => {
    if (open) {
      setQtys(Object.fromEntries(order.items.map((i) => [i.id, Math.min(i.qtyReserved, target(i))])));
      setNote('');
    }
  }, [open, order]);
  const prepare = useAction(
    (closePartial: boolean) => api.post(`/orders/${order.id}/prepare`, {
      items: order.items.map((i) => ({ itemId: i.id, preparedQuantity: qtys[i.id] ?? 0 })), closePartial, note: note || undefined,
    }),
    { invalidate: STOCK_KEYS, success: (_d, closePartial) => (closePartial ? 'Pedido cerrado como surtido parcial' : 'Preparación guardada'), onSuccess: onClose },
  );
  const complete = order.items.every((i) => (qtys[i.id] ?? 0) === target(i));
  return (
    <Modal
      open={open} onCancel={onClose} width={860} title={`Preparar pedido ${order.folio}`} destroyOnHidden
      footer={(
        <Space wrap>
          <Button onClick={onClose}>Cancelar</Button>
          <Popconfirm
            title="¿Cerrar como surtido parcial?" description="Lo no preparado dejará de ser demanda pendiente y el pedido pasará a listo para entrega."
            okText="Cerrar parcial" cancelText="Volver" onConfirm={() => prepare.mutate(true)} disabled={complete}
          >
            <Button disabled={complete} loading={prepare.isPending}>Cerrar como surtido parcial</Button>
          </Popconfirm>
          <Button type="primary" loading={prepare.isPending} onClick={() => prepare.mutate(false)}>
            {complete ? 'Marcar listo para entrega' : 'Guardar preparación'}
          </Button>
        </Space>
      )}
    >
      <Typography.Paragraph type="secondary">
        Indica lo realmente preparado de cada partida (máximo lo reservado). Si todo queda completo el pedido pasa a “Listo para entrega”; si no, queda “Parcialmente surtido”.
      </Typography.Paragraph>
      <Table
        size="small" rowKey="id" pagination={false} dataSource={order.items} scroll={{ x: 600 }}
        columns={[
          { title: 'Producto', dataIndex: 'productName' },
          { title: 'Autorizada', key: 'a', align: 'right', render: (_, i) => fmtQty(target(i)) },
          { title: 'Reservada', dataIndex: 'qtyReserved', align: 'right', render: fmtQty },
          { title: 'Preparada', key: 'p', align: 'right', width: 170,
            render: (_, i) => (
              <InputNumber
                min={0} max={Math.min(i.qtyReserved, target(i))} value={qtys[i.id]} suffix={i.unitName} style={{ width: 150 }}
                onChange={(v) => setQtys((s) => ({ ...s, [i.id]: v ?? 0 }))}
              />) },
        ]}
      />
      <Input.TextArea style={{ marginTop: 12 }} rows={2} maxLength={300} placeholder="Nota (opcional)" value={note} onChange={(e) => setNote(e.target.value)} />
    </Modal>
  );
}

function DispatchModal({ order, open, onClose }: { order: OrderDetail; open: boolean; onClose: () => void }) {
  const { user } = useAuth();
  const { data: staff = [], isLoading } = useDeliveryStaff(open);
  const [assigned, setAssigned] = useState<number | undefined>();
  useEffect(() => { if (open) setAssigned(user?.role === 'entrega' ? user.id : undefined); }, [open, user]);
  const dispatch = useAction(
    () => api.post(`/orders/${order.id}/dispatch`, assigned ? { assignedTo: assigned } : {}),
    { invalidate: STOCK_KEYS, success: 'Pedido despachado; la mercancía salió del inventario', onSuccess: onClose },
  );
  return (
    <Modal
      open={open} onCancel={onClose} title={`Despachar pedido ${order.folio}`} okText="Confirmar despacho" cancelText="Cancelar"
      onOk={() => dispatch.mutate()} confirmLoading={dispatch.isPending} destroyOnHidden
    >
      <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="Al despachar, la mercancía preparada sale definitivamente del inventario." />
      <Typography.Text>Repartidor responsable</Typography.Text>
      <Select
        allowClear style={{ width: '100%', marginTop: 6 }} placeholder="Selecciona un repartidor (opcional)" loading={isLoading}
        value={assigned} onChange={setAssigned} options={staff.map((s) => ({ value: s.id, label: s.fullName }))}
      />
    </Modal>
  );
}

function CancelModal({ order, open, onClose }: { order: OrderDetail; open: boolean; onClose: () => void }) {
  const [form] = Form.useForm();
  const cancel = useAction((reason: string) => api.post(`/orders/${order.id}/cancel`, { reason }), {
    invalidate: STOCK_KEYS, success: 'Pedido cancelado', onSuccess: onClose,
  });
  return (
    <Modal
      open={open} onCancel={onClose} title={`Cancelar pedido ${order.folio}`} okText="Cancelar pedido" cancelText="Volver" okButtonProps={{ danger: true }}
      onOk={() => form.submit()} confirmLoading={cancel.isPending} destroyOnHidden afterClose={() => form.resetFields()}
    >
      <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="Se liberarán las reservas y se avisará al restaurante." />
      <Form form={form} layout="vertical" onFinish={(v) => cancel.mutate(v.reason)}>
        <Form.Item name="reason" label="Motivo de la cancelación" rules={[{ required: true, whitespace: true, min: 3, message: 'Indica el motivo (mínimo 3 caracteres)' }]}>
          <Input.TextArea rows={3} maxLength={300} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

// ───── Historial ─────
function describeChanges(e: OrderEvent): ReactNode {
  const c = e.changes as any;
  if (!c) return null;
  const lines: string[] = [];
  if (Array.isArray(c)) {
    for (const r of c) {
      if (r.product !== undefined && r.prepared !== undefined) lines.push(`${r.product}: preparado ${fmtQty(r.prepared)} de ${fmtQty(r.authorized)}`);
    }
  } else {
    for (const q of c.quantities ?? []) lines.push(`${q.product}: autorizado ${fmtQty(q.from)} → ${fmtQty(q.to)}`);
    for (const r of c.reserved ?? []) lines.push(`Reservado +${fmtQty(r.reservedAdded)} (producto ${r.productId})`);
  }
  return lines.length ? <ul style={{ margin: '4px 0 0', paddingInlineStart: 18 }}>{lines.map((l, i) => <li key={i}>{l}</li>)}</ul> : null;
}

export default function OrderDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { can } = useAuth();
  const { data: config } = useAppConfig();
  const [modal, setModal] = useState<'accept' | 'prepare' | 'dispatch' | 'cancel' | null>(null);
  const { data: order, isLoading, error } = useQuery({
    queryKey: ['order', id],
    queryFn: () => api.get<OrderDetail>(`/orders/${id}`),
    retry: false,
  });

  const review = useAction(() => api.post(`/orders/${id}/review`), { invalidate: STOCK_KEYS, success: 'Pedido marcado en revisión' });
  const reserve = useAction(() => api.post(`/orders/${id}/reserve-pending`), { invalidate: STOCK_KEYS, success: 'Se reintentó la reserva con la existencia disponible' });
  const deliver = useAction(() => api.post(`/orders/${id}/deliver`), { invalidate: STOCK_KEYS, success: 'Pedido marcado como entregado' });

  if (isLoading) return <Skeleton active paragraph={{ rows: 10 }} />;
  if (error || !order) {
    return (
      <Result status={error instanceof ApiError && error.status === 404 ? '404' : 'error'} title="No se pudo cargar el pedido"
        subTitle={error instanceof Error ? error.message : undefined} extra={<Button onClick={() => navigate('/pedidos')}>Volver a pedidos</Button>} />
    );
  }

  const manage = can('orders.manage');
  const logistics = can('orders.manage', 'deliveries.update');
  const s = order.status;
  const canEditQty = manage && ['nuevo', 'pendiente_revision', 'en_preparacion', 'parcialmente_surtido'].includes(s);
  const isNew = s === 'nuevo' || s === 'pendiente_revision';
  const canPrepare = manage && ['en_preparacion', 'parcialmente_surtido'].includes(s);
  const needsReserve = order.items.some((i) => i.qtyUnreserved > 0);
  const canReserve = manage && !['en_entrega', 'entregado', 'cancelado'].includes(s) && needsReserve;
  const canCancel = manage && ['nuevo', 'pendiente_revision', 'en_preparacion', 'parcialmente_surtido', 'listo_entrega'].includes(s);
  const showPrices = !!config?.showPrices;

  return (
    <>
      <Space style={{ marginBottom: 12 }} wrap>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/pedidos')}>Pedidos</Button>
        <Typography.Title level={3} style={{ margin: 0 }}>{order.folio}</Typography.Title>
        <StatusTag map={ORDER_STATUS} value={s} />
      </Space>

      <Card size="small" style={{ marginBottom: 16 }}>
        <KeyValue rows={[
          ['Restaurante', <b key="r">{order.restaurantName}</b>],
          ['Dirección', order.restaurantAddress],
          ['Solicitó', order.requestedByName],
          ['Creado', fmtDateTime(order.createdAt)],
          ['Responsable', order.assignedToName],
          ['Repartidor / entrega', order.delivery ? `${order.delivery.assignedToName ?? 'Sin asignar'} · ${order.delivery.status === 'entregada' ? 'entregada' : 'en ruta'}` : '—'],
          ['Entregado', fmtDateTime(order.deliveredAt)],
          ['Recepción confirmada', order.receivedAt ? `${fmtDateTime(order.receivedAt)} (${order.receivedByName ?? ''})` : '—'],
          ['Notas del restaurante', order.notes],
        ]} />
        {order.cancelReason && <Alert style={{ marginTop: 12 }} type="error" showIcon message={`Motivo de cancelación: ${order.cancelReason}`} />}
      </Card>

      <Card size="small" style={{ marginBottom: 16 }} title="Acciones">
        <Space wrap>
          {manage && s === 'nuevo' && (
            <Button icon={<EyeOutlined />} loading={review.isPending} onClick={() => review.mutate()}>Marcar en revisión</Button>
          )}
          {canEditQty && (
            <Button type={isNew ? 'primary' : 'default'} icon={isNew ? <CheckCircleOutlined /> : <EditOutlined />} onClick={() => setModal('accept')}>
              {isNew ? 'Aceptar pedido' : 'Editar cantidades autorizadas'}
            </Button>
          )}
          {canReserve && (
            <Popconfirm title="¿Reintentar la reserva?" description="Se reservará la existencia disponible que falte." okText="Reintentar" cancelText="No" onConfirm={() => reserve.mutate()}>
              <Button icon={<ReloadOutlined />} loading={reserve.isPending}>Reintentar reserva</Button>
            </Popconfirm>
          )}
          {canPrepare && <Button type="primary" icon={<ToolOutlined />} onClick={() => setModal('prepare')}>Preparar</Button>}
          {logistics && s === 'listo_entrega' && <Button type="primary" icon={<CarOutlined />} onClick={() => setModal('dispatch')}>Despachar</Button>}
          {logistics && s === 'en_entrega' && (
            <Popconfirm title="¿Marcar el pedido como entregado?" okText="Entregado" cancelText="No" onConfirm={() => deliver.mutate()}>
              <Button type="primary" icon={<InboxOutlined />} loading={deliver.isPending}>Marcar entregado</Button>
            </Popconfirm>
          )}
          {canCancel && <Button danger icon={<CloseCircleOutlined />} onClick={() => setModal('cancel')}>Cancelar pedido</Button>}
          {!manage && !logistics && <Typography.Text type="secondary">Sin acciones disponibles para tu rol.</Typography.Text>}
          {(manage || logistics) && ['entregado', 'cancelado'].includes(s) && <Typography.Text type="secondary">El pedido está cerrado.</Typography.Text>}
        </Space>
      </Card>

      <Card size="small" title="Partidas" style={{ marginBottom: 16 }}>
        <Table
          size="small" rowKey="id" pagination={false} dataSource={order.items} scroll={{ x: 900 }}
          columns={[
            { title: 'Producto', dataIndex: 'productName', render: (v, r) => <span><b>{v}</b><br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.sku}</Typography.Text></span> },
            { title: 'Unidad', dataIndex: 'unitName' },
            { title: 'Solicitada', dataIndex: 'qtyRequested', align: 'right', render: fmtQty },
            { title: 'Autorizada', dataIndex: 'qtyAuthorized', align: 'right', render: (v) => fmtQty(v) },
            { title: 'Reservada', dataIndex: 'qtyReserved', align: 'right',
              render: (v, r) => (r.qtyUnreserved > 0 && !['entregado', 'cancelado', 'en_entrega'].includes(s)
                ? <Tag color="orange" style={{ marginInlineEnd: 0 }}>{fmtQty(v)} (faltan {fmtQty(r.qtyUnreserved)})</Tag> : fmtQty(v)) },
            { title: 'Preparada', dataIndex: 'qtyPrepared', align: 'right', render: fmtQty },
            { title: 'Entregada', dataIndex: 'qtyDelivered', align: 'right', render: fmtQty },
            { title: 'Pendiente', dataIndex: 'qtyPending', align: 'right', render: (v) => (v > 0 ? <Typography.Text type="danger">{fmtQty(v)}</Typography.Text> : fmtQty(v)) },
            { title: 'Disponible ahora', dataIndex: 'availableNow', align: 'right', render: fmtQty },
            ...(showPrices ? [{ title: 'Precio', dataIndex: 'unitPrice', align: 'right' as const, render: (v: number | null) => fmtMoney(v) }] : []),
          ]}
        />
      </Card>

      <Card size="small" title="Historial de eventos">
        <Timeline
          items={order.events.map((e) => ({
            color: e.toStatus ? (ORDER_STATUS[e.toStatus]?.color === 'default' ? 'gray' : ORDER_STATUS[e.toStatus]?.color) : 'gray',
            children: (
              <div>
                <Space wrap size={6}>
                  <b>{ORDER_EVENT_LABEL[e.event] ?? e.event}</b>
                  {e.toStatus && <StatusTag map={ORDER_STATUS} value={e.toStatus} />}
                </Space>
                <div><Typography.Text type="secondary">{fmtDateTime(e.createdAt)} · {e.userName ?? 'Sistema'}</Typography.Text></div>
                {e.note && <div>{e.note}</div>}
                {describeChanges(e)}
              </div>
            ),
          }))}
        />
      </Card>

      <AcceptModal order={order} open={modal === 'accept'} onClose={() => setModal(null)} />
      <PrepareModal order={order} open={modal === 'prepare'} onClose={() => setModal(null)} />
      <DispatchModal order={order} open={modal === 'dispatch'} onClose={() => setModal(null)} />
      <CancelModal order={order} open={modal === 'cancel'} onClose={() => setModal(null)} />
    </>
  );
}
