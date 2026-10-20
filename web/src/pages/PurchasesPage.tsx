import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import { CheckOutlined, EditOutlined, InboxOutlined, PlusOutlined, SendOutlined, ShoppingCartOutlined, StopOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { STOCK_KEYS, useAction, useSuppliers } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { PageHeader, StatusTag } from '../components/common';
import { PurchaseFormModal, type PurchaseInitial } from '../components/PurchaseFormModal';
import { ReasonModal } from '../components/ReasonModal';
import { fmtDateTime, fmtMoney, fmtPlainDate, fmtQty } from '../utils/format';
import { PURCHASE_STATUS, SHORTAGE_STATUS, options } from '../utils/status';
import type { Paged, PurchaseDetail, PurchaseSuggestion, PurchaseSummary, Supplier } from '../types';

// ───── Recepción (parcial o completa) ─────
function ReceiveModal({ po, open, onClose }: { po: PurchaseDetail; open: boolean; onClose: () => void }) {
  const pending = po.items.filter((i) => i.qtyPending > 0);
  const [qtys, setQtys] = useState<Record<number, number>>({});
  const [costs, setCosts] = useState<Record<number, number>>({});
  const [invoice, setInvoice] = useState('');
  useEffect(() => {
    if (open) {
      setQtys(Object.fromEntries(pending.map((i) => [i.productId, i.qtyPending])));
      setCosts(Object.fromEntries(pending.map((i) => [i.productId, i.unitCost])));
      setInvoice('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, po.id]);
  const lines = pending.filter((i) => (qtys[i.productId] ?? 0) > 0);
  const complete = pending.every((i) => (qtys[i.productId] ?? 0) === i.qtyPending);
  const total = lines.reduce((s, i) => s + (qtys[i.productId] ?? 0) * (costs[i.productId] ?? 0), 0);
  const receive = useAction(
    () => api.post(`/purchases/${po.id}/receive`, {
      invoiceRef: invoice || undefined,
      items: lines.map((i) => ({ productId: i.productId, quantity: qtys[i.productId], unitCost: costs[i.productId] ?? i.unitCost })),
    }),
    { invalidate: STOCK_KEYS, success: complete ? 'Recepción completa registrada' : 'Recepción parcial registrada', onSuccess: onClose },
  );
  return (
    <Modal
      open={open} onCancel={onClose} width={860} title={`Recibir mercancía · ${po.folio}`} destroyOnClose
      okText={complete ? 'Registrar recepción completa' : 'Registrar recepción parcial'} cancelText="Cancelar"
      okButtonProps={{ disabled: !lines.length }} onOk={() => receive.mutate()} confirmLoading={receive.isPending}
    >
      <Alert type="info" showIcon style={{ marginBottom: 12 }} message="Al registrar la recepción, las cantidades se suman a la existencia física." />
      <Table
        size="small" rowKey="id" pagination={false} dataSource={pending} scroll={{ x: 640 }}
        columns={[
          { title: 'Producto', dataIndex: 'productName' },
          { title: 'Por recibir', dataIndex: 'qtyPending', align: 'right', render: fmtQty },
          { title: 'Cantidad recibida', key: 'q', width: 170, render: (_, i) => (
            <InputNumber min={0} max={i.qtyPending} value={qtys[i.productId]} style={{ width: 150 }} addonAfter={i.unitName}
              onChange={(v) => setQtys((s) => ({ ...s, [i.productId]: v ?? 0 }))} />) },
          { title: 'Costo unit.', key: 'c', width: 130, render: (_, i) => (
            <InputNumber min={0} prefix="$" value={costs[i.productId]} style={{ width: 110 }}
              onChange={(v) => setCosts((s) => ({ ...s, [i.productId]: v ?? 0 }))} />) },
        ]}
      />
      <Space style={{ marginTop: 12, width: '100%', justifyContent: 'space-between' }} wrap>
        <Input placeholder="Factura / remisión" maxLength={60} value={invoice} onChange={(e) => setInvoice(e.target.value)} style={{ width: 260 }} />
        <Typography.Text strong>Importe recibido: {fmtMoney(total)}</Typography.Text>
      </Space>
    </Modal>
  );
}

// ───── Detalle de la orden ─────
function PurchaseDrawer({ id, onClose }: { id: number | null; onClose: () => void }) {
  const { can } = useAuth();
  const [receiving, setReceiving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const { data: po, isLoading } = useQuery({ queryKey: ['purchases', 'detail', id], queryFn: () => api.get<PurchaseDetail>(`/purchases/${id}`), enabled: !!id });
  const submit = useAction(() => api.post(`/purchases/${id}/submit`), { invalidate: STOCK_KEYS, success: 'Orden enviada a autorización' });
  const authorize = useAction(() => api.post(`/purchases/${id}/authorize`), { invalidate: STOCK_KEYS, success: 'Orden autorizada' });
  const cancel = useAction((reason: string) => api.post(`/purchases/${id}/cancel`, { reason }), {
    invalidate: STOCK_KEYS, success: 'Orden cancelada', onSuccess: () => setCancelling(false),
  });

  const initial: PurchaseInitial | null = po && {
    supplierId: po.supplierId, expectedDate: po.expectedDate, notes: po.notes,
    shortageIds: po.shortages.map((s) => s.id),
    items: po.items.map((i) => ({ productId: i.productId, quantity: i.qtyOrdered, unitCost: i.unitCost })),
  };
  const s = po?.status;

  return (
    <Drawer open={!!id} onClose={onClose} width={760} title={po ? <Space>Orden {po.folio} <StatusTag map={PURCHASE_STATUS} value={po.status} /></Space> : 'Orden de compra'} loading={isLoading}>
      {po && (
        <>
          <Space wrap style={{ marginBottom: 16 }}>
            {s === 'borrador' && can('purchases.write') && <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>Editar</Button>}
            {s === 'borrador' && can('purchases.write') && (
              <Popconfirm title="¿Enviar la orden a autorización?" okText="Enviar" cancelText="No" onConfirm={() => submit.mutate()}>
                <Button type="primary" icon={<SendOutlined />} loading={submit.isPending}>Enviar a autorización</Button>
              </Popconfirm>
            )}
            {s === 'pendiente_autorizacion' && can('purchases.authorize') && (
              <Popconfirm title="¿Autorizar esta orden de compra?" description={`Importe: ${fmtMoney(po.totalAmount)}`} okText="Autorizar" cancelText="No" onConfirm={() => authorize.mutate()}>
                <Button type="primary" icon={<CheckOutlined />} loading={authorize.isPending}>Autorizar</Button>
              </Popconfirm>
            )}
            {(s === 'autorizada' || s === 'parcialmente_recibida') && can('receipts.create') && (
              <Button type="primary" icon={<InboxOutlined />} onClick={() => setReceiving(true)}>Recibir mercancía</Button>
            )}
            {s && ['borrador', 'pendiente_autorizacion', 'autorizada'].includes(s) && can('purchases.write', 'purchases.authorize') && (
              <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>Cancelar orden</Button>
            )}
          </Space>
          <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }}>
            <Descriptions.Item label="Proveedor">{po.supplierName}</Descriptions.Item>
            <Descriptions.Item label="Importe"><b>{fmtMoney(po.totalAmount)}</b></Descriptions.Item>
            <Descriptions.Item label="Creada">{fmtDateTime(po.createdAt)} · {po.createdByName}</Descriptions.Item>
            <Descriptions.Item label="Fecha esperada">{fmtPlainDate(po.expectedDate)}</Descriptions.Item>
            <Descriptions.Item label="Autorizada">{po.authorizedAt ? `${fmtDateTime(po.authorizedAt)} · ${po.authorizedByName ?? ''}` : '—'}</Descriptions.Item>
            <Descriptions.Item label="Avance">{fmtQty(po.qtyReceived)} de {fmtQty(po.qtyOrdered)} recibidas</Descriptions.Item>
            {po.cancelReason && <Descriptions.Item label="Motivo de cancelación" span={2}>{po.cancelReason}</Descriptions.Item>}
            {po.notes && <Descriptions.Item label="Notas" span={2}>{po.notes}</Descriptions.Item>}
          </Descriptions>

          <Typography.Title level={5} style={{ marginTop: 20 }}>Solicitado vs recibido</Typography.Title>
          <Table
            size="small" rowKey="id" pagination={false} dataSource={po.items} scroll={{ x: 640 }}
            columns={[
              { title: 'Producto', dataIndex: 'productName', render: (v, r) => <span>{v}<br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.sku}</Typography.Text></span> },
              { title: 'Faltante original', dataIndex: 'originalShortage', align: 'right', render: (v) => fmtQty(v) },
              { title: 'Solicitado', dataIndex: 'qtyOrdered', align: 'right', render: fmtQty },
              { title: 'Recibido', dataIndex: 'qtyReceived', align: 'right', render: fmtQty },
              { title: 'Pendiente', dataIndex: 'qtyPending', align: 'right', render: (v) => (v > 0 ? <Typography.Text type="warning">{fmtQty(v)}</Typography.Text> : fmtQty(v)) },
              { title: 'Costo unit.', dataIndex: 'unitCost', align: 'right', render: fmtMoney },
              { title: 'Importe', dataIndex: 'lineTotal', align: 'right', render: fmtMoney },
            ]}
          />
          {po.receipts.length > 0 && (
            <>
              <Typography.Title level={5} style={{ marginTop: 20 }}>Recepciones</Typography.Title>
              <Table
                size="small" rowKey="id" pagination={false} dataSource={po.receipts}
                columns={[
                  { title: 'Folio', dataIndex: 'folio' }, { title: 'Fecha', dataIndex: 'receivedAt', render: fmtDateTime },
                  { title: 'Factura', dataIndex: 'invoiceRef', render: (v) => v ?? '—' }, { title: 'Importe', dataIndex: 'totalAmount', align: 'right', render: fmtMoney },
                ]}
              />
            </>
          )}
          {po.shortages.length > 0 && (
            <>
              <Typography.Title level={5} style={{ marginTop: 20 }}>Faltantes vinculados</Typography.Title>
              <Space wrap>{po.shortages.map((f) => <Tag key={f.id} color={SHORTAGE_STATUS[f.status]?.color}>{f.folio} · {f.productName}</Tag>)}</Space>
            </>
          )}
          <ReceiveModal po={po} open={receiving} onClose={() => setReceiving(false)} />
          {initial && editing && <PurchaseFormModal open onClose={() => setEditing(false)} initial={initial} editId={po.id} />}
          <ReasonModal open={cancelling} title={`Cancelar orden ${po.folio}`} okText="Cancelar orden" loading={cancel.isPending}
            onCancel={() => setCancelling(false)} onSubmit={(r) => cancel.mutate(r)} />
        </>
      )}
    </Drawer>
  );
}

// ───── Sugerencias ─────
function SuggestionsTab() {
  const { can } = useAuth();
  const [selected, setSelected] = useState<number[]>([]);
  const [initial, setInitial] = useState<PurchaseInitial | null>(null);
  const { data, isLoading } = useQuery({ queryKey: ['suggestions'], queryFn: () => api.get<PurchaseSuggestion[]>('/purchases/suggestions') });
  const chosen = useMemo(() => (data ?? []).filter((r) => selected.includes(r.productId)), [data, selected]);
  const create = () => {
    const suppliers = new Set(chosen.map((c) => c.lastSupplierId).filter(Boolean));
    setInitial({
      supplierId: suppliers.size === 1 ? ([...suppliers][0] as number) : undefined,
      shortageIds: chosen.map((c) => c.shortageId).filter((x): x is number => !!x),
      items: chosen.map((c) => ({ productId: c.productId, quantity: c.suggested, unitCost: c.estimatedUnitCost })),
    });
  };
  return (
    <>
      <Alert type="info" showIcon style={{ marginBottom: 12 }} message="Sugerido = faltante − cantidad por recibir en órdenes vigentes. Selecciona productos para armar una orden de compra." />
      {can('purchases.write') && (
        <Button type="primary" icon={<ShoppingCartOutlined />} disabled={!chosen.length} onClick={create} style={{ marginBottom: 12 }}>
          Crear orden de compra{chosen.length ? ` (${chosen.length})` : ''}
        </Button>
      )}
      <Table
        size="middle" rowKey="productId" loading={isLoading} dataSource={data} scroll={{ x: 1100 }}
        pagination={{ pageSize: 25, showTotal: (t) => `${t} productos` }}
        rowSelection={can('purchases.write') ? { selectedRowKeys: selected, onChange: (k) => setSelected(k as number[]), getCheckboxProps: (r) => ({ disabled: r.suggested <= 0 }) } : undefined}
        columns={[
          { title: 'Producto', dataIndex: 'name', render: (v, r) => <span><b>{v}</b><br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.sku}</Typography.Text></span> },
          { title: 'Disponible', dataIndex: 'available', align: 'right', render: (v, r) => `${fmtQty(v)} ${r.unitName}` },
          { title: 'Mínimo', dataIndex: 'minStock', align: 'right', render: fmtQty },
          { title: 'Faltante', dataIndex: 'shortage', align: 'right', render: fmtQty },
          { title: 'Por recibir', dataIndex: 'incoming', align: 'right', render: fmtQty },
          { title: 'Sugerido', dataIndex: 'suggested', align: 'right', render: (v) => <b>{fmtQty(v)}</b>, sorter: (a, b) => a.suggested - b.suggested },
          { title: 'Costo est.', dataIndex: 'estimatedUnitCost', align: 'right', render: fmtMoney },
          { title: 'Importe est.', dataIndex: 'estimatedAmount', align: 'right', render: fmtMoney },
          { title: 'Último proveedor', dataIndex: 'lastSupplier', render: (v) => v ?? '—' },
          { title: 'Faltante', dataIndex: 'shortageStatus', render: (v) => <StatusTag map={SHORTAGE_STATUS} value={v} /> },
        ]}
      />
      {initial && <PurchaseFormModal open onClose={() => setInitial(null)} initial={initial} onDone={() => setSelected([])} />}
    </>
  );
}

// ───── Órdenes ─────
function OrdersTab() {
  const { can } = useAuth();
  const [status, setStatus] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [detail, setDetail] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const { data, isLoading } = useQuery({
    queryKey: ['purchases', status, page, pageSize],
    queryFn: () => api.get<Paged<PurchaseSummary>>('/purchases', { status, page, pageSize }),
  });
  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select allowClear placeholder="Todos los estados" value={status} onChange={(v) => { setStatus(v); setPage(1); }} style={{ width: 240 }} options={options(PURCHASE_STATUS)} />
        {can('purchases.write') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>Nueva orden</Button>}
      </Space>
      <Table
        size="middle" rowKey="id" loading={isLoading} dataSource={data?.data} scroll={{ x: 1000 }}
        pagination={{ current: page, pageSize, total: data?.total, showSizeChanger: true, onChange: (p, s) => { setPage(p); setPageSize(s); }, showTotal: (t) => `${t} órdenes` }}
        onRow={(r) => ({ onClick: () => setDetail(r.id), className: 'row-click' })}
        columns={[
          { title: 'Folio', dataIndex: 'folio', render: (v) => <b>{v}</b> },
          { title: 'Proveedor', dataIndex: 'supplierName' },
          { title: 'Estado', dataIndex: 'status', render: (v) => <StatusTag map={PURCHASE_STATUS} value={v} /> },
          { title: 'Solicitado', dataIndex: 'qtyOrdered', align: 'right', render: fmtQty },
          { title: 'Recibido', dataIndex: 'qtyReceived', align: 'right', render: fmtQty },
          { title: 'Importe', dataIndex: 'totalAmount', align: 'right', render: fmtMoney },
          { title: 'Esperada', dataIndex: 'expectedDate', render: fmtPlainDate },
          { title: 'Creada', dataIndex: 'createdAt', render: fmtDateTime },
          { title: 'Creó', dataIndex: 'createdByName' },
        ]}
      />
      <PurchaseDrawer id={detail} onClose={() => setDetail(null)} />
      {creating && <PurchaseFormModal open onClose={() => setCreating(false)} initial={{ items: [] }} />}
    </>
  );
}

// ───── Proveedores ─────
function SuppliersTab() {
  const { can } = useAuth();
  const { data, isLoading } = useSuppliers();
  const [editing, setEditing] = useState<Partial<Supplier> | null>(null);
  const [form] = Form.useForm();
  const save = useAction(
    (v: Record<string, unknown>) => (editing?.id ? api.put(`/suppliers/${editing.id}`, v) : api.post('/suppliers', v)),
    { invalidate: [['suppliers']], success: 'Proveedor guardado', onSuccess: () => setEditing(null) },
  );
  const write = can('suppliers.write');
  return (
    <>
      {write && <Button type="primary" icon={<PlusOutlined />} style={{ marginBottom: 12 }} onClick={() => setEditing({ active: true })}>Nuevo proveedor</Button>}
      <Table
        size="middle" rowKey="id" loading={isLoading} dataSource={data} scroll={{ x: 760 }} pagination={{ pageSize: 25 }}
        columns={[
          { title: 'Proveedor', dataIndex: 'name', render: (v) => <b>{v}</b> },
          { title: 'Contacto', dataIndex: 'contact', render: (v) => v ?? '—' },
          { title: 'Teléfono', dataIndex: 'phone', render: (v) => v ?? '—' },
          { title: 'Correo', dataIndex: 'email', render: (v) => v ?? '—' },
          { title: 'Activo', dataIndex: 'active', render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? 'Activo' : 'Inactivo'}</Tag> },
          ...(write ? [{ title: '', key: 'a', render: (_: unknown, r: Supplier) => <Button size="small" icon={<EditOutlined />} onClick={() => setEditing(r)}>Editar</Button> }] : []),
        ]}
      />
      <Modal open={!!editing} title={editing?.id ? 'Editar proveedor' : 'Nuevo proveedor'} okText="Guardar" cancelText="Cancelar" onCancel={() => setEditing(null)}
        onOk={() => form.submit()} confirmLoading={save.isPending} destroyOnClose>
        <Form form={form} layout="vertical" initialValues={editing ?? {}} onFinish={(v) => save.mutate({ ...v, active: v.active ?? true })}>
          <Form.Item name="name" label="Nombre" rules={[{ required: true, whitespace: true, message: 'Indica el nombre' }]}><Input maxLength={120} /></Form.Item>
          <Form.Item name="contact" label="Contacto"><Input maxLength={120} /></Form.Item>
          <Form.Item name="phone" label="Teléfono"><Input maxLength={30} /></Form.Item>
          <Form.Item name="email" label="Correo" rules={[{ type: 'email', message: 'Correo inválido' }]}><Input maxLength={120} /></Form.Item>
          <Form.Item name="address" label="Dirección"><Input maxLength={250} /></Form.Item>
          {editing?.id && <Form.Item name="active" label="Estado"><Select options={[{ value: true, label: 'Activo' }, { value: false, label: 'Inactivo' }]} /></Form.Item>}
        </Form>
      </Modal>
    </>
  );
}

export default function PurchasesPage() {
  const { can } = useAuth();
  const [tab, setTab] = useState('orders');
  return (
    <>
      <PageHeader title="Compras" subtitle="Sugerencias, órdenes de compra y proveedores" />
      <Tabs
        activeKey={tab} onChange={setTab}
        items={[
          { key: 'orders', label: 'Órdenes de compra', children: <OrdersTab /> },
          { key: 'suggestions', label: 'Sugerencias de compra', children: <SuggestionsTab /> },
          ...(can('suppliers.read') ? [{ key: 'suppliers', label: 'Proveedores', children: <SuppliersTab /> }] : []),
        ]}
      />
    </>
  );
}
