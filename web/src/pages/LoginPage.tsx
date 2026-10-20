import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { LockOutlined, UserOutlined, BarChartOutlined } from '@ant-design/icons';
import { useAuth, RestaurantRoleError } from '../auth/AuthContext';
import { useAppConfig } from '../api/hooks';
import { errorText } from '../api/client';

export default function LoginPage() {
  const { user, login, sessionExpired } = useAuth();
  const { data: config } = useAppConfig();
  const [error, setError] = useState<{ text: string; restaurant: boolean } | null>(null);
  const [loading, setLoading] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const onFinish = async (v: { username: string; password: string }) => {
    setLoading(true);
    setError(null);
    try {
      await login(v.username.trim(), v.password);
    } catch (e) {
      setError({ text: errorText(e), restaurant: e instanceof RestaurantRoleError });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 16, background: 'var(--login-bg, #f0f2f5)' }}>
      <Card style={{ width: 400, maxWidth: '100%' }}>
        <div style={{ textAlign: 'center', marginBottom: 20 }}>
          <BarChartOutlined style={{ fontSize: 40, color: '#1668dc' }} />
          <Typography.Title level={3} style={{ margin: '8px 0 0' }}>{config?.companyName ?? 'Panel de almacén'}</Typography.Title>
          <Typography.Text type="secondary">Panel de control de almacén</Typography.Text>
        </div>
        {sessionExpired && !error && <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="Tu sesión expiró. Inicia sesión de nuevo." />}
        {error && (
          <Alert
            type={error.restaurant ? 'info' : 'error'} showIcon style={{ marginBottom: 16 }}
            message={error.restaurant ? 'Usa la app Android' : 'No se pudo iniciar sesión'}
            description={error.text}
          />
        )}
        <Form layout="vertical" onFinish={onFinish} autoComplete="off">
          <Form.Item name="username" label="Usuario" rules={[{ required: true, message: 'Escribe tu usuario' }]}>
            <Input prefix={<UserOutlined />} autoFocus autoCapitalize="none" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="Contraseña" rules={[{ required: true, message: 'Escribe tu contraseña' }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>Iniciar sesión</Button>
        </Form>
      </Card>
    </div>
  );
}
