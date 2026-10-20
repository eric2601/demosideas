import { useEffect, useState, type ReactNode } from 'react';
import { Button, Empty, Flex, Select, Space, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useProducts } from '../api/hooks';
import type { StatusInfo } from '../utils/status';
import { fmtQty } from '../utils/format';

export function StatusTag({ map, value }: { map: Record<string, StatusInfo>; value: string | null | undefined }) {
  if (!value) return <>—</>;
  const info = map[value];
  return <Tag color={info?.color ?? 'default'} style={{ marginInlineEnd: 0 }}>{info?.label ?? value}</Tag>;
}

export function PageHeader({ title, subtitle, extra, onRefresh, loading }: {
  title: string; subtitle?: string; extra?: ReactNode; onRefresh?: () => void; loading?: boolean;
}) {
  return (
    <Flex justify="space-between" align="center" wrap gap={12} style={{ marginBottom: 16 }}>
      <div>
        <Typography.Title level={3} style={{ margin: 0 }}>{title}</Typography.Title>
        {subtitle && <Typography.Text type="secondary">{subtitle}</Typography.Text>}
      </div>
      <Space wrap>
        {extra}
        {onRefresh && <Button icon={<ReloadOutlined />} onClick={onRefresh} loading={loading}>Actualizar</Button>}
      </Space>
    </Flex>
  );
}

export function useDebounced<T>(value: T, ms = 350): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

export const EmptyText = ({ text = 'Sin resultados' }: { text?: string }) => <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={text} />;

/** Selector de producto con búsqueda por nombre o SKU. */
export function ProductSelect({ value, onChange, onlyActive = true, placeholder = 'Producto', style, allowClear, disabled }: {
  value?: number; onChange?: (id: number | undefined) => void; onlyActive?: boolean; placeholder?: string;
  style?: React.CSSProperties; allowClear?: boolean; disabled?: boolean;
}) {
  const { data = [], isLoading } = useProducts();
  const items = onlyActive ? data.filter((p) => p.active) : data;
  return (
    <Select
      showSearch allowClear={allowClear} disabled={disabled} loading={isLoading} value={value} placeholder={placeholder}
      style={{ minWidth: 220, ...style }} optionFilterProp="label"
      onChange={(v) => onChange?.(v as number | undefined)}
      options={items.map((p) => ({ value: p.id, label: `${p.sku} · ${p.name}` }))}
    />
  );
}

export const QtyUnit = ({ qty, unit }: { qty: number | null | undefined; unit?: string }) => (
  <span>{fmtQty(qty)}{unit ? <Typography.Text type="secondary"> {unit}</Typography.Text> : null}</span>
);

/** Tabla de pares clave/valor compacta para detalles. */
export function KeyValue({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: '10px 24px' }}>
      {rows.map(([k, v]) => (
        <div key={k}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{k}</Typography.Text>
          <div>{v ?? '—'}</div>
        </div>
      ))}
    </div>
  );
}
