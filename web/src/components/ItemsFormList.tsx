import { Button, Form, InputNumber, Space, Typography, type FormInstance } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useProducts } from '../api/hooks';
import { ProductSelect } from './common';
import { fmtMoney } from '../utils/format';

export interface LineItem { productId?: number; quantity?: number; unitCost?: number }

/** Renglones de producto/cantidad/costo con importe por renglón y total. Debe usarse dentro de un <Form>. */
export function ItemsFormList({ form, name = 'items' }: { form: FormInstance; name?: string }) {
  const { data: products = [] } = useProducts();
  const items: LineItem[] = Form.useWatch(name, form) ?? [];
  const total = items.reduce((s, i) => s + (i?.quantity ?? 0) * (i?.unitCost ?? 0), 0);
  const chosen = new Set(items.map((i) => i?.productId));

  return (
    <>
      <Form.List name={name} rules={[{ validator: async (_, v) => { if (!v?.length) throw new Error('Agrega al menos un producto'); } }]}>
        {(fields, { add, remove }, { errors }) => (
          <>
            {fields.map((f) => {
              const line = items[f.name];
              const unit = products.find((p) => p.id === line?.productId)?.unitName;
              return (
                <div key={f.key} style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'flex-start', marginBottom: 8 }}>
                  <Form.Item name={[f.name, 'productId']} rules={[{ required: true, message: 'Elige el producto' }]} style={{ flex: '2 1 240px', marginBottom: 0 }}>
                    <ProductSelect style={{ width: '100%' }}
                      onChange={(id) => {
                        const p = products.find((x) => x.id === id);
                        const cur = form.getFieldValue(name) ?? [];
                        if (p && !cur[f.name]?.unitCost) {
                          cur[f.name] = { ...cur[f.name], unitCost: p.costUnit };
                          form.setFieldValue(name, [...cur]);
                        }
                      }} />
                  </Form.Item>
                  <Form.Item name={[f.name, 'quantity']} rules={[{ required: true, message: 'Cantidad' }, { type: 'number', min: 0.001, message: '> 0' }]} style={{ flex: '1 1 130px', marginBottom: 0 }}>
                    <InputNumber placeholder="Cantidad" min={0} style={{ width: '100%' }} suffix={unit} />
                  </Form.Item>
                  <Form.Item name={[f.name, 'unitCost']} rules={[{ required: true, message: 'Costo' }]} style={{ flex: '1 1 130px', marginBottom: 0 }}>
                    <InputNumber placeholder="Costo unit." min={0} prefix="$" style={{ width: '100%' }} />
                  </Form.Item>
                  <div style={{ flex: '0 0 100px', textAlign: 'right', lineHeight: '32px' }} className="num">{fmtMoney((line?.quantity ?? 0) * (line?.unitCost ?? 0))}</div>
                  <Button type="text" danger icon={<DeleteOutlined />} aria-label="Quitar renglón" onClick={() => remove(f.name)} />
                </div>
              );
            })}
            <Form.ErrorList errors={errors} />
            <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
              <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({})} disabled={chosen.size > 150}>Agregar producto</Button>
              <Typography.Text strong>Importe total: {fmtMoney(total)}</Typography.Text>
            </Space>
          </>
        )}
      </Form.List>
    </>
  );
}

/** Convierte los renglones del formulario al cuerpo esperado por la API. */
export const toApiItems = (items: LineItem[] = []) =>
  items.map((i) => ({ productId: i.productId!, quantity: i.quantity!, unitCost: i.unitCost ?? 0 }));
