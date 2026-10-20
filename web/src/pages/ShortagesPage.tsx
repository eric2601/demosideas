import { useMemo, useState } from 'react';
import { Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd';
import { PlusOutlined, ShoppingCartOutlined, SyncOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { STOCK_KEYS, useAction, useProducts } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { PageHeader, ProductSelect, StatusTag } from '../components/common';
import { PurchaseFormModal, type PurchaseInitial } from '../components/PurchaseFormModal';
import { fmtDateTime, fmtQty } from '../utils/format';
import { PRIORITY, PURCHASE_STATUS, SHORTAGE_STATUS, options } from '../utils/status';
import type { Priority, Shortage, ShortageStatus } from '../types';

function NewShortageModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form] = Form.useForm();
  const create = useAction((v: Record<string, unknown>) => api.post('/shortages', v), {
    invalidate: STOCK_KEYS, success: 'Faltante registrado', onSuccess: onClose,
  });
  return (
    <Modal
      open={open} onCancel={onClose} title="Alta manual de faltante" okText="Registrar" cancelText="Cancelar"
      onOk={() => form.submit()} confirmLoading={create.isPending} destroyOnClose afterClose={() => form.resetFields()}
    >
      <Form form={form} layout="vertical" initialValues={{ priority: 'media' }} onFinish={(v) => create.mutate(v)}>
        <Form.Item name="productId" label="Producto" rules={[{ required: true, message: 'Elige el producto' }]}><ProductSelect style={{ width: '100%' }} /></Form.Item>
        <Form.Item name="quantityRequired" label="Cantidad requerida" rules={[{ required: true, message: 'Indica la cantidad' }]}>
          <InputNumber min={0.001} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="priority" label="Prioridad"><Select options={options(PRIORITY)} /></Form.Item>
        <Form.Item name="notes" label="Notas"><Input.TextArea rows={2} maxLength={500} /></Form.Item>
      </Form>
      <Typography.Text type="secondary">Si el producto ya tiene un faltante abierto, se actualiza en lugar de duplicarse.</Typography.Text>
    </Modal>
  );
}

export default function ShortagesPage() {
  const { can } = useAuth();
  const [status, setStatus] = useState<string>('open');
  const [priority, setPriority] = useState<string | undefined>();
  const [q, setQ] = useState('');
  const [selected, setSelected] = useState<number[]>([]);
  const [newOpen, setNewOpen] = useState(false);
  const [poInitial, setPoInitial] = useState<PurchaseInitial | null>(null);
  const { data: products = [] } = useProducts(can('products.read'));
  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['shortages', status, priority],
    queryFn: () => api.get<Shortage[]>('/shortages', { status: status === 'all' ? undefined : status, priority }),
  });
  const patch = useAction((v: { id: number; status?: ShortageStatus; priority?: Priority }) => api.patch(`/shortages/${v.id}`, { status: v.status, priority: v.priority }), {
    invalidate: STOCK_KEYS, success: 'Faltante actualizado',
  });
  const refresh = useAction(() => api.post('/shortages/refresh'), { invalidate: STOCK_KEYS, success: 'Faltantes recalculados' });
  const write = can('shortages.write');

  const rows = useMemo(() => {
    const t = q.trim().toLowerCase();
    return (data ?? []).filter((s) => !t || s.productName.toLowerCase().includes(t) || s.sku.toLowerCase().includes(t) || s.folio.toLowerCase().includes(t));
  }, [data, q]);

  const createPO = () => {
    const chosen = rows.filter((r) => selected.includes(r.id) && r.status !== 'resuelto');
    setPoInitial({
      shortageIds: chosen.map((c) => c.id),
      items: chosen.map((c) => ({ productId: c.productId, quantity: c.quantityShort, unitCost: products.find((p) => p.id === c.productId)?.costUnit ?? 0 })),
    });
  };

  return (
    <>
      <PageHeader
        title="Faltantes" subtitle="Productos que no alcanzan para cubrir la demanda o el mínimo" onRefresh={() => void refetch()} loading={isFetching}
        extra={(
          <>
            {write && <Popconfirm title="¿Recalcular faltantes de todos los productos?" okText="Recalcular" cancelText="No" onConfirm={() => refresh.mutate()}>
              <Button icon={<SyncOutlined />} loading={refresh.isPending}>Recalcular</Button>
            </Popconfirm>}
            {write && <Button icon={<PlusOutlined />} onClick={() => setNewOpen(true)}>Alta manual</Button>}
            {can('purchases.write') && (
              <Button type="primary" icon={<ShoppingCartOutlined />} disabled={!selected.length} onClick={createPO}>
                Crear orden de compra{selected.length ? ` (${selected.length})` : ''}
              </Button>
            )}
          </>
        )}
      />
      <Space wrap style={{ marginBottom: 12 }}>
        <Input allowClear prefix={<SearchOutlined />} placeholder="Buscar producto o folio" value={q} onChange={(e) => setQ(e.target.value)} style={{ width: 260 }} />
        <Select value={status} onChange={(v) => { setStatus(v); setSelected([]); }} style={{ width: 210 }}
          options={[{ value: 'open', label: 'Abiertos' }, { value: 'all', label: 'Todos' }, ...options(SHORTAGE_STATUS)]} />
        <Select allowClear placeholder="Toda prioridad" value={priority} onChange={setPriority} style={{ width: 160 }} options={options(PRIORITY)} />
      </Space>
      <Table
        size="middle" rowKey="id" loading={isLoading} dataSource={rows} scroll={{ x: 1150 }}
        pagination={{ pageSize: 25, showSizeChanger: true, showTotal: (t) => `${t} faltantes` }}
        rowSelection={can('purchases.write') ? { selectedRowKeys: selected, onChange: (k) => setSelected(k as number[]), getCheckboxProps: (r) => ({ disabled: r.status === 'resuelto' }) } : undefined}
        columns={[
          { title: 'Folio', dataIndex: 'folio', render: (v) => <b>{v}</b> },
          { title: 'Producto', dataIndex: 'productName', render: (v, r) => <span>{v}<br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.sku}</Typography.Text></span> },
          { title: 'Requerido', dataIndex: 'quantityRequired', align: 'right', render: (v, r) => `${fmtQty(v)} ${r.unitName}` },
          { title: 'Faltante', dataIndex: 'quantityShort', align: 'right', render: (v) => <b>{fmtQty(v)}</b> },
          { title: 'Existencia actual', dataIndex: 'currentOnHand', align: 'right', render: fmtQty },
          { title: 'Disponible', dataIndex: 'currentAvailable', align: 'right', render: fmtQty },
          { title: 'Prioridad', dataIndex: 'priority',
            render: (v: Priority, r) => write && r.status !== 'resuelto'
              ? <Select size="small" value={v} style={{ width: 100 }} options={options(PRIORITY)} onChange={(p) => patch.mutate({ id: r.id, priority: p })} />
              : <StatusTag map={PRIORITY} value={v} /> },
          { title: 'Estado', dataIndex: 'status',
            render: (v: ShortageStatus, r) => write && v !== 'resuelto'
              ? <Select size="small" value={v} style={{ width: 190 }} options={options(SHORTAGE_STATUS)} onChange={(s) => {
                  if (s === 'resuelto') {
                    Modal.confirm({ title: '¿Marcar el faltante como resuelto?', okText: 'Resolver', cancelText: 'No', onOk: () => patch.mutateAsync({ id: r.id, status: s }) });
                  } else patch.mutate({ id: r.id, status: s });
                }} />
              : <StatusTag map={SHORTAGE_STATUS} value={v} /> },
          { title: 'Órdenes de compra', dataIndex: 'purchaseOrders',
            render: (v: Shortage['purchaseOrders']) => v.length ? <Space wrap size={4}>{v.map((p) => <Tag key={p.id} color={PURCHASE_STATUS[p.status]?.color}>{p.folio}</Tag>)}</Space> : '—' },
          { title: 'Detectado', dataIndex: 'detectedAt', render: fmtDateTime },
        ]}
      />
      <NewShortageModal open={newOpen} onClose={() => setNewOpen(false)} />
      {poInitial && (
        <PurchaseFormModal open onClose={() => setPoInitial(null)} initial={poInitial} onDone={() => setSelected([])} />
      )}
    </>
  );
}
