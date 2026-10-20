import { Alert, Form, Input, Modal } from 'antd';
import { api } from '../api/client';
import { useAction } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';

export const passwordRules = [
  { required: true, message: 'Escribe la contraseña' },
  { min: 8, message: 'Mínimo 8 caracteres' },
  { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: 'Debe contener letras y números' },
];

export function ChangePasswordModal({ open, onClose, forced }: { open: boolean; onClose: () => void; forced?: boolean }) {
  const [form] = Form.useForm();
  const { refreshUser } = useAuth();
  const change = useAction(
    (v: { currentPassword: string; newPassword: string }) => api.post('/auth/change-password', v),
    {
      success: 'Contraseña actualizada',
      onSuccess: () => { form.resetFields(); void refreshUser(); onClose(); },
    },
  );

  return (
    <Modal
      title="Cambiar contraseña" open={open} onCancel={onClose} okText="Guardar" cancelText={forced ? 'Más tarde' : 'Cancelar'}
      confirmLoading={change.isPending} onOk={() => form.submit()} destroyOnClose maskClosable={false}
    >
      {forced && <Alert type="warning" showIcon style={{ marginBottom: 16 }}
        message="Tu contraseña es temporal. Te recomendamos cambiarla ahora." />}
      <Form form={form} layout="vertical" onFinish={({ currentPassword, newPassword }) => change.mutate({ currentPassword, newPassword })}>
        <Form.Item name="currentPassword" label="Contraseña actual" rules={[{ required: true, message: 'Escribe tu contraseña actual' }]}>
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item name="newPassword" label="Nueva contraseña" rules={passwordRules}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirm" label="Confirmar nueva contraseña" dependencies={['newPassword']}
          rules={[{ required: true, message: 'Confirma la contraseña' }, ({ getFieldValue }) => ({
            validator: (_, v) => (!v || getFieldValue('newPassword') === v ? Promise.resolve() : Promise.reject(new Error('Las contraseñas no coinciden'))),
          })]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
