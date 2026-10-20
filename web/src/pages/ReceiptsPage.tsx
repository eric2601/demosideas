import { useState } from 'react';
import { Button, Descriptions, Drawer, Form, Input, Modal, Select, Table, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { STOCK_KEYS, useAction, useSuppliers } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/common';
import { ItemsFormList, toApiItems, type LineItem } from '../components/ItemsFormList';
import { fmtDateTime, fmtMoney, fmtQty } from '../utils/format';
import type { Paged, ReceiptDetail, ReceiptSummary } from '../types';

function ReceiptForm({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form] = Form.useForm();
  const { data: suppliers = [] } = useSuppliers();
  const create = useAction(
    (v: { supplierId: number; invoiceRef?: string; notes?: string; items: LineItem[] }) =>
      api.post('/receipts', { supplierId: v.supplierId, invoiceRef: v.invoiceRef || undefined, notes: v.notes || undefined, items: toApiItems(v.items) }),
    { invalidate: STOCK_KEYS, success: 'Entrada registrada; existencias actualizadas', onSuccess: onClose },
  );
  return (
    <Modal
      open={open} onCancel={onClose} title="Nueva entrada de mercancía" width={820} okText="Registrar entrada" cancelText="Cancelar"
      onOk={() => form.submit()} confirmLoading={create.isPending} destroyOnHidden maskClosable={false} afterClose={() => form.resetFields()}
    >
      <Form form={form} layout="vertical" initialValues={{ items: [{}] }} onFinish={(v) => create.mutate(v)}>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Form.Item name="supplierId" label="Proveedor" rules={[{ required: true, message: 'Elige el proveedor' }]} style={{ flex: '2 1 260px' }}>
            <Select showSearch optionFilterProp="label" placeholder="Proveedor" options={suppliers.filter((s) => s.active).map((s) => ({ value: s.id, label: s.name }))} />
          </Form.Item>
          <Form.Item name="invoiceRef" label="Factura / remisión" style={{ flex: '1 1 180px' }}><Input maxLength={60} /></Form.Item>
        </div>
        <Typography.Text strong>Productos recibidos</Typography.Text>
        <div style={{ marginTop: 8 }}><ItemsFormList form={form} /></div>
        <Form.Item name="notes" label="Notas" style={{ marginTop: 12 }}><Input.TextArea rows={2} maxLength={500} /></Form.Item>
      </Form>
    </Modal>
  );
}

function ReceiptDrawer({ id, onClose }: { id: number | null; onClose: () => void }) {
  const { data, isLoading } = useQuery({ queryKey: ['receipts', 'detail', id], queryFn: () => api.get<ReceiptDetail>(`/receipts/${id}`), enabled: !!id });
  return (
    <Drawer open={!!id} onClose={onClose} width={680} title={data ? `Entrada ${data.folio}` : 'Entrada'} loading={isLoading}>
      {data && (
        <>
          <Descriptions column={{ xs: 1, sm: 2 }} size="small" bordered>
            <Descriptions.Item label="Fecha">{fmtDateTime(data.receivedAt)}</Descriptions.Item>
            <Descriptions.Item label="Proveedor">{data.supplierName}</Descriptions.Item>
            <Descriptions.Item label="Factura">{data.invoiceRef ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="Orden de compra">{data.purchaseFolio ?? 'Entrada directa'}</Descriptions.Item>
            <Descriptions.Item label="Recibió">{data.userName}</Descriptions.Item>
            <Descriptions.Item label="Importe total"><b>{fmtMoney(data.totalAmount)}</b></Descriptions.Item>
            {data.notes && <Descriptions.Item label="Notas" span={2}>{data.notes}</Descriptions.Item>}
          </Descriptions>
          <Table
            style={{ marginTop: 16 }} size="small" rowKey="productId" pagination={false} dataSource={data.items} scroll={{ x: 480 }}
            columns={[
              { title: 'Producto', dataIndex: 'productName', render: (v, r) => <span>{v}<br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.sku}</Typography.Text></span> },
              { title: 'Cantidad', dataIndex: 'quantity', align: 'right', render: fmtQty },
              { title: 'Costo unit.', dataIndex: 'unitCost', align: 'right', render: fmtMoney },
              { title: 'Importe', dataIndex: 'lineTotal', align: 'right', render: fmtMoney },
            ]}
          />
        </>
      )}
    </Drawer>
  );
}

export default function ReceiptsPage() {
  const { can } = useAuth();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [detail, setDetail] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['receipts', page, pageSize],
    queryFn: () => api.get<Paged<ReceiptSummary>>('/receipts', { page, pageSize }),
  });
  return (
    <>
      <PageHeader
        title="Entradas de mercancía" subtitle="Recepciones directas y contra órdenes de compra"
        onRefresh={() => void refetch()} loading={isFetching}
        extra={can('receipts.create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>Nueva entrada</Button>}
      />
      <Table
        size="middle" rowKey="id" loading={isLoading} dataSource={data?.data} scroll={{ x: 900 }}
        pagination={{ current: page, pageSize, total: data?.total, showSizeChanger: true, onChange: (p, s) => { setPage(p); setPageSize(s); }, showTotal: (t) => `${t} entradas` }}
        onRow={(r) => ({ onClick: () => setDetail(r.id), className: 'row-click' })}
        columns={[
          { title: 'Folio', dataIndex: 'folio', render: (v) => <b style={{ whiteSpace: 'nowrap' }}>{v}</b> },
          { title: 'Fecha', dataIndex: 'receivedAt', render: fmtDateTime },
          { title: 'Proveedor', dataIndex: 'supplierName' },
          { title: 'Factura', dataIndex: 'invoiceRef', render: (v) => v ?? '—' },
          { title: 'Orden de compra', dataIndex: 'purchaseFolio', render: (v) => v ?? 'Directa' },
          { title: 'Renglones', dataIndex: 'itemCount', align: 'right' },
          { title: 'Importe', dataIndex: 'totalAmount', align: 'right', render: fmtMoney },
          { title: 'Recibió', dataIndex: 'userName' },
        ]}
      />
      <ReceiptForm open={creating} onClose={() => setCreating(false)} />
      <ReceiptDrawer id={detail} onClose={() => setDetail(null)} />
    </>
  );
}
