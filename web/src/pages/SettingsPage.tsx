import { useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, Select, Space, Switch, Table, Tabs, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useAction } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/common';
import { useRealtime } from '../realtime/RealtimeProvider';
import { fmtDateTime, getTimezone } from '../utils/format';
import type { AuditRow, Paged, Settings } from '../types';

function timezones(): string[] {
  try {
    const list = (Intl as any).supportedValuesOf?.('timeZone') as string[] | undefined;
    if (list?.length) return list;
  } catch { /* ignorar */ }
  return ['America/Mexico_City', 'America/Monterrey', 'America/Tijuana', 'America/Cancun', 'America/Bogota', 'America/Lima', 'America/Santiago', 'America/Argentina/Buenos_Aires', 'Europe/Madrid', 'UTC'];
}

function GeneralTab() {
  const { can } = useAuth();
  const { localSound, setLocalSound, testSound } = useRealtime();
  const [form] = Form.useForm<Settings>();
  const write = can('settings.write');
  const { data, isLoading } = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/settings') });
  useEffect(() => { if (data) form.setFieldsValue(data); }, [data, form]);
  const save = useAction((v: Settings) => api.put<Settings>('/settings', v), {
    invalidate: [['settings'], ['config']], success: 'Configuración guardada',
  });
  return (
    <Space direction="vertical" size={16} style={{ width: '100%', maxWidth: 640 }}>
      <Card title="Empresa" loading={isLoading}>
        {!write && <Alert type="info" showIcon style={{ marginBottom: 16 }} message="Solo un administrador puede modificar la configuración." />}
        <Form form={form} layout="vertical" disabled={!write} onFinish={(v) => save.mutate(v)}>
          <Form.Item name="company_name" label="Nombre de la empresa" rules={[{ required: true, whitespace: true, message: 'Indica el nombre' }]}><Input maxLength={100} /></Form.Item>
          <Form.Item name="timezone" label="Zona horaria" extra={`Las fechas se muestran en esta zona (actual: ${getTimezone()}).`} rules={[{ required: true }]}>
            <Select showSearch options={timezones().map((z) => ({ value: z, label: z }))} />
          </Form.Item>
          <Form.Item name="show_prices" label="Mostrar precios a los restaurantes" valuePropName="checked"><Switch /></Form.Item>
          <Form.Item name="notification_sound" label="Sonido de notificaciones (general)" valuePropName="checked"
            extra="Habilita el aviso sonoro para pedidos nuevos en este panel."><Switch /></Form.Item>
          {write && <Button type="primary" htmlType="submit" loading={save.isPending}>Guardar configuración</Button>}
        </Form>
      </Card>
      <Card title="Preferencias de este navegador">
        <Space>
          <Switch checked={localSound} onChange={(on) => { setLocalSound(on); if (on) testSound(); }} aria-label="Sonido local" />
          <Typography.Text>Reproducir sonido al llegar un pedido nuevo (solo en este equipo)</Typography.Text>
          <Button size="small" onClick={testSound}>Probar</Button>
        </Space>
      </Card>
    </Space>
  );
}

function AuditTab() {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [q, setQ] = useState('');
  const { data, isLoading } = useQuery({ queryKey: ['audit', page, pageSize], queryFn: () => api.get<Paged<AuditRow>>('/audit', { page, pageSize }) });
  const rows = (data?.data ?? []).filter((r) => !q || `${r.action} ${r.username ?? ''} ${r.entity ?? ''}`.toLowerCase().includes(q.toLowerCase()));
  return (
    <>
      <Input.Search allowClear placeholder="Filtrar esta página por acción o usuario" value={q} onChange={(e) => setQ(e.target.value)} style={{ width: 320, marginBottom: 12 }} />
      <Table
        size="small" rowKey="id" loading={isLoading} dataSource={rows} scroll={{ x: 900 }}
        pagination={{ current: page, pageSize, total: data?.total, showSizeChanger: true, onChange: (p, s) => { setPage(p); setPageSize(s); }, showTotal: (t) => `${t} registros` }}
        columns={[
          { title: 'Fecha', dataIndex: 'createdAt', render: fmtDateTime, width: 150 },
          { title: 'Usuario', dataIndex: 'username', render: (v) => v ?? 'Sistema' },
          { title: 'Acción', dataIndex: 'action', render: (v) => <b>{v}</b> },
          { title: 'Entidad', key: 'e', render: (_, r) => (r.entity ? `${r.entity} #${r.entityId ?? ''}` : '—') },
          { title: 'Detalle', dataIndex: 'detail', render: (v) => (v ? <Typography.Text code style={{ fontSize: 12 }}>{JSON.stringify(v)}</Typography.Text> : '—') },
          { title: 'IP', dataIndex: 'ip', render: (v) => v ?? '—' },
        ]}
      />
    </>
  );
}

export default function SettingsPage() {
  const { can } = useAuth();
  return (
    <>
      <PageHeader title="Configuración" subtitle="Parámetros generales del sistema" />
      <Tabs items={[
        { key: 'g', label: 'General', children: <GeneralTab /> },
        ...(can('audit.read') ? [{ key: 'a', label: 'Auditoría', children: <AuditTab /> }] : []),
      ]} />
    </>
  );
}
