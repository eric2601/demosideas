import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Form, Input, Modal, Popconfirm, Space, Table, Tag, Transfer, Typography } from 'antd';
import { EditOutlined, PlusOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useAction, useProducts, useRestaurants } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { passwordRules } from '../components/ChangePasswordModal';
import { PageHeader } from '../components/common';
import { fmtDateTime } from '../utils/format';
import type { Restaurant } from '../types';

function RestaurantForm({ editing, onClose }: { editing: Partial<Restaurant> | null; onClose: () => void }) {
  const [form] = Form.useForm();
  const [withUser, setWithUser] = useState(false);
  useEffect(() => { setWithUser(false); }, [editing]);
  const isEdit = !!editing?.id;
  const save = useAction(
    (v: any) => {
      const base = { code: v.code, name: v.name, address: v.address || undefined, managerName: v.managerName, phone: v.phone || undefined };
      return isEdit
        ? api.put(`/restaurants/${editing!.id}`, { ...base, active: editing!.active })
        : api.post('/restaurants', { ...base, user: withUser ? { username: v.username, fullName: v.userFullName, password: v.password } : undefined });
    },
    { invalidate: [['restaurants'], ['users']], success: isEdit ? 'Restaurante actualizado' : 'Restaurante creado', onSuccess: onClose },
  );
  return (
    <Modal open={!!editing} title={isEdit ? 'Editar restaurante' : 'Nuevo restaurante'} okText="Guardar" cancelText="Cancelar" onCancel={onClose}
      onOk={() => form.submit()} confirmLoading={save.isPending} destroyOnHidden width={560}>
      <Form form={form} layout="vertical" initialValues={editing ?? {}} onFinish={(v) => save.mutate(v)}>
        <Space align="start" style={{ width: '100%' }} wrap>
          <Form.Item name="code" label="Código" rules={[{ required: true, whitespace: true, message: 'Indica el código' }]}><Input maxLength={20} style={{ width: 130 }} /></Form.Item>
          <Form.Item name="name" label="Nombre" rules={[{ required: true, whitespace: true, message: 'Indica el nombre' }]}><Input maxLength={120} style={{ width: 340 }} /></Form.Item>
        </Space>
        <Form.Item name="managerName" label="Responsable" rules={[{ required: true, whitespace: true, message: 'Indica el responsable' }]}><Input maxLength={120} /></Form.Item>
        <Form.Item name="phone" label="Teléfono"><Input maxLength={30} /></Form.Item>
        <Form.Item name="address" label="Dirección"><Input.TextArea rows={2} maxLength={250} /></Form.Item>
        {!isEdit && (
          <>
            <Checkbox checked={withUser} onChange={(e) => setWithUser(e.target.checked)} style={{ marginBottom: 12 }}>Crear usuario de acceso para la app Android</Checkbox>
            {withUser && (
              <>
                <Form.Item name="username" label="Usuario" rules={[{ required: true, min: 3, message: 'Mínimo 3 caracteres' }, { pattern: /^[a-zA-Z0-9._-]+$/, message: 'Solo letras, números, punto, guion y guion bajo' }]}><Input maxLength={40} autoComplete="off" /></Form.Item>
                <Form.Item name="userFullName" label="Nombre del usuario" rules={[{ required: true, whitespace: true, message: 'Indica el nombre' }]}><Input maxLength={120} /></Form.Item>
                <Form.Item name="password" label="Contraseña temporal" rules={passwordRules}><Input.Password autoComplete="new-password" /></Form.Item>
              </>
            )}
          </>
        )}
      </Form>
    </Modal>
  );
}

function CatalogModal({ restaurant, onClose }: { restaurant: Restaurant | null; onClose: () => void }) {
  const { data: products = [] } = useProducts(!!restaurant);
  const { data: ids } = useQuery({
    queryKey: ['restaurant-products', restaurant?.id],
    queryFn: () => api.get<number[]>(`/restaurants/${restaurant!.id}/products`),
    enabled: !!restaurant,
  });
  const [keys, setKeys] = useState<string[]>([]);
  useEffect(() => { if (ids) setKeys(ids.map(String)); }, [ids]);
  const save = useAction(() => api.put(`/restaurants/${restaurant!.id}/products`, { productIds: keys.map(Number) }), {
    invalidate: [['restaurant-products']], success: 'Catálogo autorizado actualizado', onSuccess: onClose,
  });
  return (
    <Modal open={!!restaurant} title={`Catálogo autorizado · ${restaurant?.name ?? ''}`} okText="Guardar" cancelText="Cancelar" width={820}
      onCancel={onClose} onOk={() => save.mutate()} confirmLoading={save.isPending} destroyOnHidden>
      <Alert type="info" showIcon style={{ marginBottom: 12 }}
        message={keys.length ? `${keys.length} producto(s) autorizados: el restaurante solo podrá pedir estos.` : 'Sin restricción: el restaurante puede pedir todos los productos activos.'} />
      <Transfer
        listStyle={{ width: '100%', height: 360 }} showSearch
        dataSource={products.filter((p) => p.active || keys.includes(String(p.id))).map((p) => ({ key: String(p.id), title: `${p.sku} · ${p.name}` }))}
        targetKeys={keys} onChange={(k) => setKeys(k as string[])} render={(i) => i.title}
        titles={['Disponibles', 'Autorizados']} filterOption={(input, item) => item.title.toLowerCase().includes(input.toLowerCase())}
      />
    </Modal>
  );
}

export default function RestaurantsPage() {
  const { can } = useAuth();
  const { data, isLoading, refetch, isFetching } = useRestaurants();
  const [editing, setEditing] = useState<Partial<Restaurant> | null>(null);
  const [catalog, setCatalog] = useState<Restaurant | null>(null);
  const write = can('restaurants.write');
  const [q, setQ] = useState('');
  const toggle = useAction(
    (r: Restaurant) => api.put(`/restaurants/${r.id}`, { code: r.code, name: r.name, address: r.address ?? undefined, managerName: r.managerName, phone: r.phone ?? undefined, active: !r.active }),
    { invalidate: [['restaurants']], success: 'Estado actualizado' },
  );
  const rows = (data ?? []).filter((r) => !q || `${r.name} ${r.code} ${r.managerName}`.toLowerCase().includes(q.toLowerCase()));
  return (
    <>
      <PageHeader title="Restaurantes" subtitle="Clientes que realizan pedidos" onRefresh={() => void refetch()} loading={isFetching}
        extra={write && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>Nuevo restaurante</Button>} />
      <Input.Search allowClear placeholder="Buscar" value={q} onChange={(e) => setQ(e.target.value)} style={{ width: 280, marginBottom: 12 }} />
      <Table
        size="middle" rowKey="id" loading={isLoading} dataSource={rows} scroll={{ x: 1000 }} pagination={{ pageSize: 25, showTotal: (t) => `${t} restaurantes` }}
        columns={[
          { title: 'Código', dataIndex: 'code' },
          { title: 'Nombre', dataIndex: 'name', render: (v) => <b>{v}</b> },
          { title: 'Responsable', dataIndex: 'managerName' },
          { title: 'Teléfono', dataIndex: 'phone', render: (v) => v ?? '—' },
          { title: 'Dirección', dataIndex: 'address', render: (v) => v ?? '—' },
          { title: 'Usuarios', dataIndex: 'usernames', render: (v) => v ?? <Typography.Text type="secondary">Sin usuario</Typography.Text> },
          { title: 'Alta', dataIndex: 'createdAt', render: fmtDateTime },
          { title: 'Estado', dataIndex: 'active', render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? 'Activo' : 'Inactivo'}</Tag> },
          ...(write ? [{
            title: 'Acciones', key: 'a', fixed: 'right' as const, width: 330,
            render: (_: unknown, r: Restaurant) => (
              <Space>
                <Button size="small" icon={<EditOutlined />} onClick={() => setEditing(r)}>Editar</Button>
                <Button size="small" icon={<UnorderedListOutlined />} onClick={() => setCatalog(r)}>Catálogo</Button>
                <Popconfirm title={r.active ? '¿Desactivar el restaurante?' : '¿Activar el restaurante?'} description={r.active ? 'Sus usuarios ya no podrán iniciar sesión.' : undefined}
                  okText="Sí" cancelText="No" onConfirm={() => toggle.mutate(r)}>
                  <Button size="small" danger={r.active}>{r.active ? 'Desactivar' : 'Activar'}</Button>
                </Popconfirm>
              </Space>
            ),
          }] : []),
        ]}
      />
      <RestaurantForm editing={editing} onClose={() => setEditing(null)} />
      <CatalogModal restaurant={catalog} onClose={() => setCatalog(null)} />
    </>
  );
}
