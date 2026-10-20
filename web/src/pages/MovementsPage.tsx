import { useState } from 'react';
import { DatePicker, Input, Select, Space, Table, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { PageHeader, ProductSelect, StatusTag, useDebounced } from '../components/common';
import { dayBoundaryISO, fmtDateTime, fmtQty, fmtSigned } from '../utils/format';
import { MOVEMENT_TYPE, options } from '../utils/status';
import type { Movement, Paged } from '../types';

export default function MovementsPage() {
  const navigate = useNavigate();
  const [productId, setProductId] = useState<number | undefined>();
  const [type, setType] = useState<string | undefined>();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [q, setQ] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const dq = useDebounced(q);
  const from = range?.[0] ? dayBoundaryISO(range[0]) : undefined;
  const to = range?.[1] ? dayBoundaryISO(range[1], 1) : undefined;
  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['movements', productId, type, from, to, dq, page, pageSize],
    queryFn: () => api.get<Paged<Movement>>('/movements', { productId, type, from, to, q: dq, page, pageSize }),
  });
  const reset = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(1); };

  return (
    <>
      <PageHeader title="Historial de movimientos" subtitle="Bitácora inmutable de existencias" onRefresh={() => void refetch()} loading={isFetching} />
      <Space wrap style={{ marginBottom: 12 }}>
        <ProductSelect allowClear onlyActive={false} value={productId} onChange={reset(setProductId)} placeholder="Todos los productos" style={{ width: 280 }} />
        <Select allowClear placeholder="Todos los tipos" value={type} onChange={reset(setType)} style={{ width: 230 }} options={options(MOVEMENT_TYPE)} />
        <DatePicker.RangePicker format="DD/MM/YYYY" value={range as any} onChange={(v) => reset(setRange)(v as any)} placeholder={['Desde', 'Hasta']} />
        <Input allowClear prefix={<SearchOutlined />} placeholder="Producto o documento" value={q} onChange={(e) => reset(setQ)(e.target.value)} style={{ width: 220 }} />
      </Space>
      <Table
        size="middle" rowKey="id" loading={isLoading} dataSource={data?.data} scroll={{ x: 1250 }}
        pagination={{ current: page, pageSize, total: data?.total, showSizeChanger: true, pageSizeOptions: [25, 50, 100], onChange: (p, s) => { setPage(p); setPageSize(s); }, showTotal: (t) => `${t} movimientos` }}
        columns={[
          { title: 'Fecha', dataIndex: 'createdAt', render: fmtDateTime, width: 140 },
          { title: 'Producto', dataIndex: 'productName', render: (v, r) => <span>{v}<br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.sku}</Typography.Text></span> },
          { title: 'Tipo', dataIndex: 'type', render: (v) => <StatusTag map={MOVEMENT_TYPE} value={v} /> },
          { title: 'Cantidad', dataIndex: 'quantity', align: 'right', render: fmtQty },
          { title: 'Δ Existencia', dataIndex: 'deltaOnHand', align: 'right', render: (v) => (v === 0 ? '—' : <Typography.Text type={v > 0 ? 'success' : 'danger'}>{fmtSigned(v)}</Typography.Text>) },
          { title: 'Existencia (antes → después)', key: 'oh', align: 'right', render: (_, r) => `${fmtQty(r.onHandBefore)} → ${fmtQty(r.onHandAfter)}` },
          { title: 'Reservada (antes → después)', key: 'rs', align: 'right', render: (_, r) => (r.deltaReserved === 0 ? '—' : `${fmtQty(r.reservedBefore)} → ${fmtQty(r.reservedAfter)}`) },
          { title: 'Documento', key: 'doc',
            render: (_, r) => !r.referenceFolio ? '—' : r.referenceType === 'pedido' && r.referenceId
              ? <a onClick={() => navigate(`/pedidos/${r.referenceId}`)}>{r.referenceFolio}</a>
              : <span>{r.referenceFolio}{r.referenceType ? <Typography.Text type="secondary"> ({r.referenceType})</Typography.Text> : null}</span> },
          { title: 'Motivo', dataIndex: 'reason', render: (v, r) => <span>{v ?? '—'}{r.notes ? <><br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.notes}</Typography.Text></> : null}</span> },
          { title: 'Usuario', dataIndex: 'userName', render: (v) => v ?? 'Sistema' },
        ]}
      />
    </>
  );
}
