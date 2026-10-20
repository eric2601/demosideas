import { DatePicker, Form, Input, Modal, Select, Typography } from 'antd';
import dayjs from 'dayjs';
import { api } from '../api/client';
import { STOCK_KEYS, useAction, useSuppliers } from '../api/hooks';
import { ItemsFormList, toApiItems, type LineItem } from './ItemsFormList';

export interface PurchaseInitial {
  supplierId?: number;
  items: LineItem[];
  shortageIds?: number[];
  expectedDate?: string | null;
  notes?: string | null;
}

/** Alta o edición (borrador) de una orden de compra. */
export function PurchaseFormModal({ open, onClose, initial, editId, onDone }: {
  open: boolean; onClose: () => void; initial: PurchaseInitial; editId?: number; onDone?: () => void;
}) {
  const [form] = Form.useForm();
  const { data: suppliers = [] } = useSuppliers();
  const save = useAction(
    (v: { supplierId: number; expectedDate?: dayjs.Dayjs; notes?: string; items: LineItem[] }) => {
      const body = {
        supplierId: v.supplierId, items: toApiItems(v.items), shortageIds: initial.shortageIds?.length ? initial.shortageIds : undefined,
        expectedDate: v.expectedDate ? v.expectedDate.format('YYYY-MM-DD') : undefined, notes: v.notes || undefined,
      };
      return editId ? api.put(`/purchases/${editId}`, body) : api.post('/purchases', body);
    },
    {
      invalidate: STOCK_KEYS,
      success: editId ? 'Orden de compra actualizada' : 'Orden de compra creada en borrador',
      onSuccess: () => { onClose(); onDone?.(); },
    },
  );
  return (
    <Modal
      open={open} onCancel={onClose} width={840} title={editId ? 'Editar orden de compra (borrador)' : 'Nueva orden de compra'}
      okText={editId ? 'Guardar cambios' : 'Crear orden'} cancelText="Cancelar" onOk={() => form.submit()}
      confirmLoading={save.isPending} destroyOnHidden maskClosable={false}
    >
      <Form
        form={form} layout="vertical" onFinish={(v) => save.mutate(v)}
        initialValues={{
          supplierId: initial.supplierId, notes: initial.notes ?? undefined,
          expectedDate: initial.expectedDate ? dayjs(initial.expectedDate.slice(0, 10)) : undefined,
          items: initial.items.length ? initial.items : [{}],
        }}
      >
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Form.Item name="supplierId" label="Proveedor" rules={[{ required: true, message: 'Elige el proveedor' }]} style={{ flex: '2 1 260px' }}>
            <Select showSearch optionFilterProp="label" placeholder="Proveedor" options={suppliers.filter((s) => s.active).map((s) => ({ value: s.id, label: s.name }))} />
          </Form.Item>
          <Form.Item name="expectedDate" label="Fecha esperada" style={{ flex: '1 1 180px' }}>
            <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
          </Form.Item>
        </div>
        {!!initial.shortageIds?.length && (
          <Typography.Paragraph type="secondary">Esta orden atenderá {initial.shortageIds.length} faltante(s) seleccionado(s).</Typography.Paragraph>
        )}
        <Typography.Text strong>Productos a comprar</Typography.Text>
        <div style={{ marginTop: 8 }}><ItemsFormList form={form} /></div>
        <Form.Item name="notes" label="Notas" style={{ marginTop: 12 }}><Input.TextArea rows={2} maxLength={500} /></Form.Item>
      </Form>
    </Modal>
  );
}
