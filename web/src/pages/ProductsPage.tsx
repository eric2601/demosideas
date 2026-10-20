import { useState } from 'react';
import { Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useAction, useCategories, useUnits } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { PageHeader, StatusTag, useDebounced } from '../components/common';
import { fmtMoney, fmtQty } from '../utils/format';
import { STOCK_STATUS } from '../utils/status';
import type { Category, Product, Unit } from '../types';

const PRODUCT_KEYS = [['products'], ['inventory'], ['dashboard']];

function ProductForm({ editing, onClose }: { editing: Partial<Product> | null; onClose: () => void }) {
  const [form] = Form.useForm();
  const { data: categories = [] } = useCategories();
  const { data: units = [] } = useUnits();
  const isEdit = !!editing?.id;
  const save = useAction(
    (v: any) => {
      const body = { ...v, description: v.description || undefined, salePrice: v.salePrice ?? null, costUnit: v.costUnit ?? 0, active: v.active };
      return isEdit ? api.put(`/products/${editing!.id}`, body) : api.post('/products', body);
    },
    { invalidate: PRODUCT_KEYS, success: isEdit ? 'Producto actualizado' : 'Producto creado', onSuccess: onClose },
  );
  return (
    <Modal open={!!editing} title={isEdit ? 'Editar producto' : 'Nuevo producto'} okText="Guardar" cancelText="Cancelar" onCancel={onClose}
      onOk={() => form.submit()} confirmLoading={save.isPending} destroyOnHidden width={620}>
      <Form form={form} layout="vertical" initialValues={{ active: true, minStock: 0, costUnit: 0, ...editing }} onFinish={(v) => save.mutate(v)}>
        <Space align="start" wrap style={{ width: '100%' }}>
          <Form.Item name="sku" label="SKU" rules={[{ required: true, whitespace: true, message: 'Indica el SKU' }]}><Input maxLength={40} style={{ width: 160 }} /></Form.Item>
          <Form.Item name="name" label="Nombre" rules={[{ required: true, whitespace: true, message: 'Indica el nombre' }]}><Input maxLength={120} style={{ width: 380 }} /></Form.Item>
        </Space>
        <Form.Item name="description" label="Descripción"><Input.TextArea rows={2} maxLength={500} /></Form.Item>
        <Space align="start" wrap>
          <Form.Item name="categoryId" label="Categoría" rules={[{ required: true, message: 'Elige la categoría' }]}>
            <Select style={{ width: 250 }} options={categories.filter((c) => c.active || c.id === editing?.categoryId).map((c) => ({ value: c.id, label: c.name }))} />
          </Form.Item>
          <Form.Item name="unitId" label="Unidad" rules={[{ required: true, message: 'Elige la unidad' }]}>
            <Select style={{ width: 250 }} options={units.filter((u) => u.active || u.id === editing?.unitId).map((u) => ({ value: u.id, label: `${u.name} (${u.code})` }))} />
          </Form.Item>
        </Space>
        <Space align="start" wrap>
          <Form.Item name="minStock" label="Stock mínimo" rules={[{ required: true, message: 'Indica el mínimo' }]}><InputNumber min={0} style={{ width: 160 }} /></Form.Item>
          <Form.Item name="costUnit" label="Costo unitario"><InputNumber min={0} prefix="$" style={{ width: 160 }} /></Form.Item>
          <Form.Item name="salePrice" label="Precio de venta (opcional)"><InputNumber min={0} prefix="$" style={{ width: 180 }} /></Form.Item>
        </Space>
        <Form.Item name="active" label="Estado"><Select style={{ width: 160 }} options={[{ value: true, label: 'Activo' }, { value: false, label: 'Inactivo' }]} /></Form.Item>
      </Form>
    </Modal>
  );
}

function ProductsTab() {
  const { can } = useAuth();
  const { data: categories = [] } = useCategories();
  const [q, setQ] = useState('');
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [active, setActive] = useState<string | undefined>();
  const [stock, setStock] = useState<string | undefined>();
  const [editing, setEditing] = useState<Partial<Product> | null>(null);
  const dq = useDebounced(q);
  const write = can('products.write');
  const { data, isLoading } = useQuery({
    queryKey: ['products', 'list', dq, categoryId, active, stock],
    queryFn: () => api.get<Product[]>('/products', { q: dq, categoryId, active, stock }),
  });
  const toggle = useAction((p: Product) => api.post(`/products/${p.id}/${p.active ? 'deactivate' : 'activate'}`), { invalidate: PRODUCT_KEYS, success: 'Estado actualizado' });
  const remove = useAction((p: Product) => api.del(`/products/${p.id}`), { invalidate: PRODUCT_KEYS, success: 'Producto eliminado' });
  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Input allowClear prefix={<SearchOutlined />} placeholder="Buscar por nombre o SKU" value={q} onChange={(e) => setQ(e.target.value)} style={{ width: 260 }} />
        <Select allowClear placeholder="Categoría" value={categoryId} onChange={setCategoryId} style={{ width: 200 }} options={categories.map((c) => ({ value: c.id, label: c.name }))} />
        <Select allowClear placeholder="Estado" value={active} onChange={setActive} style={{ width: 140 }} options={[{ value: 'true', label: 'Activos' }, { value: 'false', label: 'Inactivos' }]} />
        <Select allowClear placeholder="Existencia" value={stock} onChange={setStock} style={{ width: 160 }} options={Object.entries(STOCK_STATUS).map(([value, s]) => ({ value, label: s.label }))} />
        {write && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>Nuevo producto</Button>}
      </Space>
      <Table
        size="middle" rowKey="id" loading={isLoading} dataSource={data} scroll={{ x: 1100 }} pagination={{ pageSize: 25, showSizeChanger: true, showTotal: (t) => `${t} productos` }}
        columns={[
          { title: 'SKU', dataIndex: 'sku' },
          { title: 'Producto', dataIndex: 'name', sorter: (a, b) => a.name.localeCompare(b.name), render: (v, r) => <span><b>{v}</b>{r.description ? <><br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.description}</Typography.Text></> : null}</span> },
          { title: 'Categoría', dataIndex: 'categoryName' },
          { title: 'Unidad', dataIndex: 'unitName' },
          { title: 'Mínimo', dataIndex: 'minStock', align: 'right', render: fmtQty },
          { title: 'Disponible', dataIndex: 'available', align: 'right', render: fmtQty },
          { title: 'Costo', dataIndex: 'costUnit', align: 'right', render: fmtMoney },
          { title: 'Precio', dataIndex: 'salePrice', align: 'right', render: (v) => (v === null ? '—' : fmtMoney(v)) },
          { title: 'Existencia', dataIndex: 'stockStatus', render: (v) => <StatusTag map={STOCK_STATUS} value={v} /> },
          { title: 'Estado', dataIndex: 'active', render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? 'Activo' : 'Inactivo'}</Tag> },
          ...(write ? [{
            title: 'Acciones', key: 'a', fixed: 'right' as const, width: 270,
            render: (_: unknown, p: Product) => (
              <Space>
                <Button size="small" icon={<EditOutlined />} onClick={() => setEditing(p)}>Editar</Button>
                <Popconfirm title={p.active ? '¿Desactivar el producto?' : '¿Activar el producto?'} okText="Sí" cancelText="No" onConfirm={() => toggle.mutate(p)}>
                  <Button size="small">{p.active ? 'Desactivar' : 'Activar'}</Button>
                </Popconfirm>
                <Popconfirm title="¿Eliminar el producto?" description="Solo es posible si no tiene movimientos ni documentos; de lo contrario desactívalo." okText="Eliminar" cancelText="No" okButtonProps={{ danger: true }} onConfirm={() => remove.mutate(p)}>
                  <Button size="small" danger icon={<DeleteOutlined />} aria-label="Eliminar" />
                </Popconfirm>
              </Space>
            ),
          }] : []),
        ]}
      />
      <ProductForm editing={editing} onClose={() => setEditing(null)} />
    </>
  );
}

// ───── Categorías y unidades ─────
function SimpleCatalogTab<T extends { id: number; name: string; active: boolean }>({ kind }: { kind: 'categories' | 'units' }) {
  const { can } = useAuth();
  const catQ = useCategories(kind === 'categories');
  const unitQ = useUnits(kind === 'units');
  const data = (kind === 'categories' ? catQ.data : unitQ.data) as T[] | undefined;
  const loading = kind === 'categories' ? catQ.isLoading : unitQ.isLoading;
  const [editing, setEditing] = useState<Partial<Category & Unit> | null>(null);
  const [form] = Form.useForm();
  const write = kind === 'categories' ? can('products.write') : can('settings.write');
  const label = kind === 'categories' ? 'categoría' : 'unidad';
  const save = useAction(
    (v: Record<string, unknown>) => (editing?.id ? api.put(`/${kind}/${editing.id}`, v) : api.post(`/${kind}`, v)),
    { invalidate: [[kind]], success: 'Guardado', onSuccess: () => setEditing(null) },
  );
  return (
    <>
      {write && <Button type="primary" icon={<PlusOutlined />} style={{ marginBottom: 12 }} onClick={() => setEditing({ active: true })}>Nueva {label}</Button>}
      <Table
        size="middle" rowKey="id" loading={loading} dataSource={data} pagination={{ pageSize: 25 }}
        columns={[
          ...(kind === 'units' ? [{ title: 'Código', dataIndex: 'code' }] : []),
          { title: 'Nombre', dataIndex: 'name', render: (v: string) => <b>{v}</b> },
          ...(kind === 'categories' ? [{ title: 'Descripción', dataIndex: 'description', render: (v: string | null) => v ?? '—' }] : []),
          { title: 'Estado', dataIndex: 'active', render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Activa' : 'Inactiva'}</Tag> },
          ...(write ? [{ title: '', key: 'a', render: (_: unknown, r: T) => <Button size="small" icon={<EditOutlined />} onClick={() => setEditing(r as any)}>Editar</Button> }] : []),
        ]}
      />
      <Modal open={!!editing} title={editing?.id ? `Editar ${label}` : `Nueva ${label}`} okText="Guardar" cancelText="Cancelar" onCancel={() => setEditing(null)}
        onOk={() => form.submit()} confirmLoading={save.isPending} destroyOnHidden>
        <Form form={form} layout="vertical" initialValues={editing ?? {}} onFinish={(v) => save.mutate({ ...v, active: v.active ?? true })}>
          {kind === 'units' && <Form.Item name="code" label="Código" rules={[{ required: true, whitespace: true, message: 'Indica el código' }]}><Input maxLength={12} /></Form.Item>}
          <Form.Item name="name" label="Nombre" rules={[{ required: true, whitespace: true, message: 'Indica el nombre' }]}><Input maxLength={kind === 'units' ? 40 : 80} /></Form.Item>
          {kind === 'categories' && <Form.Item name="description" label="Descripción"><Input.TextArea rows={2} maxLength={300} /></Form.Item>}
          {editing?.id && <Form.Item name="active" label="Estado"><Select options={[{ value: true, label: 'Activa' }, { value: false, label: 'Inactiva' }]} /></Form.Item>}
        </Form>
      </Modal>
    </>
  );
}

export default function ProductsPage() {
  return (
    <>
      <PageHeader title="Catálogo de productos" subtitle="Productos, categorías y unidades de medida" />
      <Tabs items={[
        { key: 'p', label: 'Productos', children: <ProductsTab /> },
        { key: 'c', label: 'Categorías', children: <SimpleCatalogTab kind="categories" /> },
        { key: 'u', label: 'Unidades', children: <SimpleCatalogTab kind="units" /> },
      ]} />
    </>
  );
}
