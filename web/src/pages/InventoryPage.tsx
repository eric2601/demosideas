import { useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Radio, Select, Space, Table, Tooltip, Typography } from 'antd';
import { EditOutlined, ExperimentOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { STOCK_KEYS, useAction } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { PageHeader, StatusTag, useDebounced } from '../components/common';
import { fmtDateTime, fmtQty, fmtSigned } from '../utils/format';
import { STOCK_STATUS } from '../utils/status';
import type { InventoryRow } from '../types';

const ADJ_TYPES = [
  { value: 'ajuste_positivo', label: 'Ajuste positivo (suma)' },
  { value: 'ajuste_negativo', label: 'Ajuste negativo (resta)' },
  { value: 'devolucion', label: 'Devolución (suma)' },
  { value: 'correccion_autorizada', label: 'Corrección autorizada (solo administrador)' },
];

function AdjustModal({ row, onClose }: { row: InventoryRow | null; onClose: () => void }) {
  const { user } = useAuth();
  const [form] = Form.useForm();
  const type = Form.useWatch('type', form);
  const adjust = useAction((v: Record<string, unknown>) => api.post('/inventory/adjustments', { ...v, productId: row!.productId }), {
    invalidate: STOCK_KEYS, success: 'Ajuste registrado', onSuccess: onClose,
  });
  return (
    <Modal
      open={!!row} onCancel={onClose} title={`Ajustar inventario: ${row?.name ?? ''}`} okText="Registrar ajuste" cancelText="Cancelar"
      onOk={() => form.submit()} confirmLoading={adjust.isPending} destroyOnHidden afterClose={() => form.resetFields()}
    >
      <Typography.Paragraph type="secondary">
        Existencia física actual: <b>{fmtQty(row?.onHand)} {row?.unitName}</b>. Los ajustes generan un movimiento nuevo; nunca editan el historial.
      </Typography.Paragraph>
      <Form form={form} layout="vertical" initialValues={{ type: 'ajuste_positivo', direction: 'in' }} onFinish={(v) => adjust.mutate(v)}>
        <Form.Item name="type" label="Tipo de ajuste" rules={[{ required: true }]}>
          <Select options={ADJ_TYPES.map((o) => ({ ...o, disabled: o.value === 'correccion_autorizada' && user?.role !== 'admin' }))} />
        </Form.Item>
        {type === 'correccion_autorizada' && (
          <Form.Item name="direction" label="Sentido de la corrección" rules={[{ required: true }]}>
            <Radio.Group optionType="button" options={[{ value: 'in', label: 'Suma' }, { value: 'out', label: 'Resta' }]} />
          </Form.Item>
        )}
        <Form.Item name="quantity" label="Cantidad" rules={[{ required: true, message: 'Indica la cantidad' }, { type: 'number', min: 0.001, message: 'Debe ser mayor a 0' }]}>
          <InputNumber style={{ width: '100%' }} min={0} step={1} suffix={row?.unitName} />
        </Form.Item>
        <Form.Item name="reason" label="Motivo (obligatorio)" rules={[{ required: true, whitespace: true, min: 3, message: 'Indica el motivo (mínimo 3 caracteres)' }]}>
          <Input maxLength={300} placeholder="Ej. merma, error de captura, producto dañado" />
        </Form.Item>
        <Form.Item name="notes" label="Notas"><Input.TextArea rows={2} maxLength={500} /></Form.Item>
      </Form>
    </Modal>
  );
}

function CountModal({ row, onClose }: { row: InventoryRow | null; onClose: () => void }) {
  const [form] = Form.useForm();
  const [result, setResult] = useState<{ systemQuantity: number; countedQuantity: number; difference: number } | null>(null);
  const count = useAction(
    (v: { countedQuantity: number; reason: string; notes?: string }) =>
      api.post<{ systemQuantity: number; countedQuantity: number; difference: number }>('/inventory/counts', {
        productId: row!.productId, countedQuantity: v.countedQuantity, notes: [v.reason, v.notes].filter(Boolean).join(' · '),
      }),
    { invalidate: STOCK_KEYS, onSuccess: (r) => setResult(r) },
  );
  const close = () => { setResult(null); onClose(); };
  return (
    <Modal
      open={!!row} onCancel={close} title={`Conteo físico: ${row?.name ?? ''}`} destroyOnHidden afterClose={() => form.resetFields()}
      okText="Registrar conteo" cancelText={result ? 'Cerrar' : 'Cancelar'} okButtonProps={{ style: result ? { display: 'none' } : undefined }}
      onOk={() => form.submit()} confirmLoading={count.isPending}
    >
      {result ? (
        <Alert
          type={result.difference === 0 ? 'success' : 'warning'} showIcon
          message={result.difference === 0 ? 'El conteo coincide con el sistema' : `Diferencia de ${fmtSigned(result.difference)} ${row?.unitName}`}
          description={`Sistema: ${fmtQty(result.systemQuantity)} · Contado: ${fmtQty(result.countedQuantity)}. ${result.difference !== 0 ? 'Se generó el ajuste correspondiente.' : ''}`}
        />
      ) : (
        <>
          <Typography.Paragraph type="secondary">
            Existencia del sistema: <b>{fmtQty(row?.onHand)} {row?.unitName}</b>. Si el conteo difiere, se genera un ajuste automático y se guarda la diferencia.
          </Typography.Paragraph>
          <Form form={form} layout="vertical" onFinish={(v) => count.mutate(v)}>
            <Form.Item name="countedQuantity" label="Cantidad contada" rules={[{ required: true, message: 'Indica la cantidad contada' }]}>
              <InputNumber style={{ width: '100%' }} min={0} step={1} suffix={row?.unitName} />
            </Form.Item>
            <Form.Item name="reason" label="Motivo del conteo (obligatorio)" rules={[{ required: true, whitespace: true, min: 3, message: 'Indica el motivo (mínimo 3 caracteres)' }]}>
              <Input maxLength={200} placeholder="Ej. inventario mensual, revisión por diferencia" />
            </Form.Item>
            <Form.Item name="notes" label="Notas"><Input.TextArea rows={2} maxLength={250} /></Form.Item>
          </Form>
        </>
      )}
    </Modal>
  );
}

export default function InventoryPage() {
  const { can } = useAuth();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const dq = useDebounced(q);
  const [adjusting, setAdjusting] = useState<InventoryRow | null>(null);
  const [counting, setCounting] = useState<InventoryRow | null>(null);
  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['inventory', dq, status],
    queryFn: () => api.get<InventoryRow[]>('/inventory', { q: dq, status }),
  });
  const canAdjust = can('inventory.adjust');

  return (
    <>
      <PageHeader title="Inventario" subtitle="Existencia física, reservada y disponible por producto" onRefresh={() => void refetch()} loading={isFetching} />
      <Space wrap style={{ marginBottom: 12 }}>
        <Input allowClear prefix={<SearchOutlined />} placeholder="Buscar por nombre o SKU" value={q} onChange={(e) => setQ(e.target.value)} style={{ width: 280 }} />
        <Select
          allowClear placeholder="Todos los estados" value={status} onChange={setStatus} style={{ width: 190 }}
          options={Object.entries(STOCK_STATUS).map(([value, v]) => ({ value, label: v.label }))}
        />
      </Space>
      <Table
        size="middle" rowKey="productId" loading={isLoading} dataSource={data} scroll={{ x: 1400 }}
        pagination={{ pageSize: 25, showSizeChanger: true, showTotal: (t) => `${t} productos` }}
        rowClassName={(r) => (r.stockStatus === 'out' ? 'row-out' : '')}
        columns={[
          { title: 'Producto', dataIndex: 'name', sorter: (a, b) => a.name.localeCompare(b.name), fixed: 'left', width: 250,
            render: (v, r) => <span><b>{v}</b><br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.sku} · {r.categoryName}</Typography.Text></span> },
          { title: 'Existencia física', dataIndex: 'onHand', align: 'right', width: 170, sorter: (a, b) => a.onHand - b.onHand, render: (v, r) => <span style={{ whiteSpace: 'nowrap' }}>{fmtQty(v)} {r.unitName}</span> },
          { title: 'Reservada', dataIndex: 'reserved', align: 'right', width: 110, render: fmtQty },
          { title: 'Disponible', dataIndex: 'available', align: 'right', width: 130, sorter: (a, b) => a.available - b.available, render: (v) => <b>{fmtQty(v)}</b> },
          { title: 'Mínimo', dataIndex: 'minStock', align: 'right', width: 100, render: fmtQty },
          { title: 'Dif. último conteo', dataIndex: 'lastCountDifference', align: 'right', width: 150,
            render: (v: number | null, r) => v === null ? '—' : (
              <Tooltip title={`Contado ${fmtDateTime(r.lastCountedAt)}`}>
                <Typography.Text type={v === 0 ? undefined : v < 0 ? 'danger' : 'warning'}>{fmtSigned(v)}</Typography.Text>
              </Tooltip>) },
          { title: 'Último movimiento', dataIndex: 'lastMovementAt', width: 160, render: fmtDateTime },
          { title: 'Estado', dataIndex: 'stockStatus', width: 120, render: (v) => <StatusTag map={STOCK_STATUS} value={v} />,
            filters: Object.entries(STOCK_STATUS).map(([value, s]) => ({ value, text: s.label })), onFilter: (v, r) => r.stockStatus === v },
          ...(canAdjust ? [{
            title: 'Acciones', key: 'actions', fixed: 'right' as const, width: 200,
            render: (_: unknown, r: InventoryRow) => (
              <Space>
                <Button size="small" icon={<EditOutlined />} onClick={() => setAdjusting(r)}>Ajustar</Button>
                <Button size="small" icon={<ExperimentOutlined />} onClick={() => setCounting(r)}>Conteo</Button>
              </Space>
            ),
          }] : []),
        ]}
      />
      <AdjustModal row={adjusting} onClose={() => setAdjusting(null)} />
      <CountModal row={counting} onClose={() => setCounting(null)} />
    </>
  );
}
