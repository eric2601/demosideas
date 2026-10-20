import { Badge, Button, Empty, Flex, List, Popover, Space, Switch, Typography } from 'antd';
import { BellOutlined, CheckOutlined, SoundOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAction } from '../api/hooks';
import { useRealtime } from '../realtime/RealtimeProvider';
import { fmtDateTime } from '../utils/format';
import type { AppNotification, NotificationsResponse } from '../types';
import { useState } from 'react';

function linkFor(n: AppNotification): string | null {
  const p = n.payload ?? {};
  if (p.orderId) return `/pedidos/${p.orderId}`;
  if (n.type === 'compra_recibida') return '/entradas';
  if (n.type === 'producto_agotado' || n.type === 'stock_minimo') return '/inventario';
  return null;
}

export function NotificationBell() {
  const navigate = useNavigate();
  const { localSound, setLocalSound, testSound } = useRealtime();
  const [open, setOpen] = useState(false);
  const { data } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get<NotificationsResponse>('/notifications', { pageSize: 30 }),
    refetchInterval: 120_000,
  });
  const markRead = useAction((id: number) => api.post(`/notifications/${id}/read`), { invalidate: [['notifications']] });
  const markAll = useAction(() => api.post('/notifications/read-all'), { invalidate: [['notifications']], success: 'Notificaciones marcadas como leídas' });
  const unread = data?.unreadCount ?? 0;

  const content = (
    <div style={{ width: 360, maxWidth: '86vw' }}>
      <Flex justify="space-between" align="center" style={{ marginBottom: 8 }} wrap gap={8}>
        <Space size={6}>
          <SoundOutlined />
          <Switch
            size="small" checked={localSound} aria-label="Sonido de notificaciones"
            onChange={(on) => { setLocalSound(on); if (on) testSound(); }}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>Sonido</Typography.Text>
        </Space>
        <Button size="small" icon={<CheckOutlined />} disabled={!unread} loading={markAll.isPending} onClick={() => markAll.mutate()}>
          Marcar todas
        </Button>
      </Flex>
      {data?.data.length ? (
        <List
          size="small" style={{ maxHeight: 380, overflowY: 'auto' }}
          dataSource={data.data}
          renderItem={(n) => (
            <List.Item
              style={{ cursor: 'pointer', background: n.read ? undefined : 'rgba(22,119,255,0.08)', paddingInline: 8 }}
              onClick={() => {
                if (!n.read) markRead.mutate(n.id);
                const l = linkFor(n);
                if (l) { setOpen(false); navigate(l); }
              }}
            >
              <List.Item.Meta
                title={<Typography.Text strong={!n.read}>{n.title}</Typography.Text>}
                description={<>{n.body && <div>{n.body}</div>}<Typography.Text type="secondary" style={{ fontSize: 12 }}>{fmtDateTime(n.createdAt)}</Typography.Text></>}
              />
              {!n.read && <Badge status="processing" />}
            </List.Item>
          )}
        />
      ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Sin notificaciones" />}
    </div>
  );

  return (
    <Popover content={content} trigger="click" placement="bottomRight" open={open} onOpenChange={setOpen} title="Notificaciones">
      <Badge count={unread} size="small" overflowCount={99}>
        <Button type="text" shape="circle" aria-label="Notificaciones" icon={<BellOutlined style={{ fontSize: 20 }} />} />
      </Badge>
    </Popover>
  );
}
