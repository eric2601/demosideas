import { useState } from 'react';
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd';
import { EditOutlined, KeyOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useAction, useRestaurants } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { passwordRules } from '../components/ChangePasswordModal';
import { PageHeader } from '../components/common';
import { fmtDateTime } from '../utils/format';
import { ROLE_LABEL } from '../utils/status';
import type { Role, UserRow } from '../types';

function UserForm({ editing, onClose }: { editing: Partial<UserRow> | null; onClose: () => void }) {
  const [form] = Form.useForm();
  const { data: roles = [] } = useQuery({ queryKey: ['roles'], queryFn: () => api.get<Role[]>('/roles') });
  const { data: restaurants = [] } = useRestaurants(!!editing);
  const role = Form.useWatch('role', form);
  const isEdit = !!editing?.id;
  const save = useAction(
    (v: any) => {
      const body = { username: v.username, fullName: v.fullName, role: v.role, restaurantId: v.role === 'restaurante' ? v.restaurantId : null, active: v.active };
      return isEdit ? api.put(`/users/${editing!.id}`, body) : api.post('/users', { ...body, password: v.password, mustChangePassword: true });
    },
    { invalidate: [['users'], ['restaurants'], ['delivery-staff']], success: isEdit ? 'Usuario actualizado' : 'Usuario creado', onSuccess: onClose },
  );
  return (
    <Modal open={!!editing} title={isEdit ? 'Editar usuario' : 'Nuevo usuario'} okText="Guardar" cancelText="Cancelar" onCancel={onClose}
      onOk={() => form.submit()} confirmLoading={save.isPending} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={{ active: true, ...editing }} onFinish={(v) => save.mutate(v)}>
        <Form.Item name="username" label="Usuario" rules={[{ required: true, min: 3, message: 'Mínimo 3 caracteres' }, { pattern: /^[a-zA-Z0-9._-]+$/, message: 'Solo letras, números, punto, guion y guion bajo' }]}>
          <Input maxLength={40} autoComplete="off" />
        </Form.Item>
        <Form.Item name="fullName" label="Nombre completo" rules={[{ required: true, whitespace: true, message: 'Indica el nombre' }]}><Input maxLength={120} /></Form.Item>
        <Form.Item name="role" label="Rol" rules={[{ required: true, message: 'Elige el rol' }]}>
          <Select options={roles.map((r) => ({ value: r.code, label: ROLE_LABEL[r.code] ?? r.name }))} />
        </Form.Item>
        {role === 'restaurante' && (
          <Form.Item name="restaurantId" label="Restaurante" rules={[{ required: true, message: 'Elige el restaurante' }]}>
            <Select showSearch optionFilterProp="label" options={restaurants.map((r) => ({ value: r.id, label: r.name }))} />
          </Form.Item>
        )}
        {!isEdit && <Form.Item name="password" label="Contraseña temporal" rules={passwordRules} extra="El usuario deberá cambiarla en su primer inicio de sesión."><Input.Password autoComplete="new-password" /></Form.Item>}
        {isEdit && <Form.Item name="active" label="Estado"><Select options={[{ value: true, label: 'Activo' }, { value: false, label: 'Inactivo' }]} /></Form.Item>}
      </Form>
    </Modal>
  );
}

function ResetPasswordModal({ user, onClose }: { user: UserRow | null; onClose: () => void }) {
  const [form] = Form.useForm();
  const reset = useAction((password: string) => api.post(`/users/${user!.id}/reset-password`, { password }), {
    invalidate: [['users']], success: 'Contraseña restablecida; deberá cambiarla al ingresar', onSuccess: onClose,
  });
  return (
    <Modal open={!!user} title={`Restablecer contraseña · ${user?.username ?? ''}`} okText="Restablecer" cancelText="Cancelar" onCancel={onClose}
      onOk={() => form.submit()} confirmLoading={reset.isPending} destroyOnHidden afterClose={() => form.resetFields()}>
      <Form form={form} layout="vertical" onFinish={(v) => reset.mutate(v.password)}>
        <Form.Item name="password" label="Nueva contraseña temporal" rules={passwordRules}><Input.Password autoComplete="new-password" /></Form.Item>
      </Form>
    </Modal>
  );
}

export default function UsersPage() {
  const { user: me, can } = useAuth();
  const [editing, setEditing] = useState<Partial<UserRow> | null>(null);
  const [resetting, setResetting] = useState<UserRow | null>(null);
  const [q, setQ] = useState('');
  const [role, setRole] = useState<string | undefined>();
  const write = can('users.write');
  const { data, isLoading, refetch, isFetching } = useQuery({ queryKey: ['users'], queryFn: () => api.get<UserRow[]>('/users') });
  const toggle = useAction(
    (u: UserRow) => api.put(`/users/${u.id}`, { username: u.username, fullName: u.fullName, role: u.role, restaurantId: u.restaurantId, active: !u.active }),
    { invalidate: [['users']], success: 'Estado actualizado' },
  );
  const rows = (data ?? []).filter((u) => (!role || u.role === role) && (!q || `${u.username} ${u.fullName}`.toLowerCase().includes(q.toLowerCase())));
  return (
    <>
      <PageHeader title="Usuarios" subtitle="Cuentas de acceso y roles" onRefresh={() => void refetch()} loading={isFetching}
        extra={write && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ role: 'almacen' })}>Nuevo usuario</Button>} />
      <Space wrap style={{ marginBottom: 12 }}>
        <Input.Search allowClear placeholder="Buscar usuario o nombre" value={q} onChange={(e) => setQ(e.target.value)} style={{ width: 260 }} />
        <Select allowClear placeholder="Todos los roles" value={role} onChange={setRole} style={{ width: 180 }} options={Object.entries(ROLE_LABEL).map(([value, label]) => ({ value, label }))} />
      </Space>
      <Table
        size="middle" rowKey="id" loading={isLoading} dataSource={rows} scroll={{ x: 1000 }} pagination={{ pageSize: 25, showTotal: (t) => `${t} usuarios` }}
        columns={[
          { title: 'Usuario', dataIndex: 'username', render: (v) => <b>{v}</b> },
          { title: 'Nombre', dataIndex: 'fullName' },
          { title: 'Rol', dataIndex: 'role', render: (v) => ROLE_LABEL[v] ?? v },
          { title: 'Restaurante', dataIndex: 'restaurantName', render: (v) => v ?? '—' },
          { title: 'Último acceso', dataIndex: 'lastLoginAt', render: fmtDateTime },
          { title: 'Estado', key: 's', render: (_, u) => (
            <Space size={4} wrap>
              <Tag color={u.active ? 'green' : 'default'}>{u.active ? 'Activo' : 'Inactivo'}</Tag>
              {u.mustChangePassword && <Tag color="gold">Contraseña temporal</Tag>}
              {u.lockedUntil && new Date(u.lockedUntil) > new Date() && <Tag color="red">Bloqueado</Tag>}
            </Space>) },
          ...(write ? [{
            title: 'Acciones', key: 'a', fixed: 'right' as const, width: 340,
            render: (_: unknown, u: UserRow) => (
              <Space>
                <Button size="small" icon={<EditOutlined />} onClick={() => setEditing(u)}>Editar</Button>
                <Button size="small" icon={<KeyOutlined />} onClick={() => setResetting(u)}>Contraseña</Button>
                {u.id === me?.id ? <Typography.Text type="secondary">(tú)</Typography.Text> : (
                  <Popconfirm title={u.active ? '¿Desactivar al usuario?' : '¿Activar al usuario?'} okText="Sí" cancelText="No" onConfirm={() => toggle.mutate(u)}>
                    <Button size="small" danger={u.active}>{u.active ? 'Desactivar' : 'Activar'}</Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          }] : []),
        ]}
      />
      <UserForm editing={editing} onClose={() => setEditing(null)} />
      <ResetPasswordModal user={resetting} onClose={() => setResetting(null)} />
    </>
  );
}
