import { Form, Input, Modal, type ModalProps } from 'antd';

/** Modal que pide un motivo obligatorio (mín. 3 caracteres) antes de ejecutar una acción. */
export function ReasonModal({ open, title, okText, loading, onCancel, onSubmit, label = 'Motivo' }: {
  open: boolean; title: string; okText: string; loading?: boolean; label?: string;
  onCancel: () => void; onSubmit: (reason: string) => void; okButtonProps?: ModalProps['okButtonProps'];
}) {
  const [form] = Form.useForm();
  return (
    <Modal
      open={open} title={title} okText={okText} cancelText="Volver" okButtonProps={{ danger: true }}
      onCancel={onCancel} onOk={() => form.submit()} confirmLoading={loading} destroyOnClose afterClose={() => form.resetFields()}
    >
      <Form form={form} layout="vertical" onFinish={(v) => onSubmit(v.reason)}>
        <Form.Item name="reason" label={label} rules={[{ required: true, whitespace: true, min: 3, message: 'Indica el motivo (mínimo 3 caracteres)' }]}>
          <Input.TextArea rows={3} maxLength={300} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
