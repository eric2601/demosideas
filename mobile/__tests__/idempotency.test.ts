import { IdempotencyAttempt, fingerprintOrder } from '../src/lib/idempotency';

const mk = () => {
  let n = 0;
  return new IdempotencyAttempt(() => `key-${++n}`);
};
const body = { items: [{ productId: 1, quantity: 2 }, { productId: 2, quantity: 0.5 }], notes: 'hola' };

describe('IdempotencyAttempt', () => {
  it('reutiliza la misma clave en reintentos mientras no haya éxito', () => {
    const a = mk();
    const k1 = a.keyFor(body);
    expect(a.keyFor(body)).toBe(k1);
    expect(a.keyFor({ ...body })).toBe(k1);
    expect(a.keyFor({ ...body, items: [...body.items].reverse() })).toBe(k1);
  });

  it('genera una clave nueva tras un éxito (clear)', () => {
    const a = mk();
    const k1 = a.keyFor(body);
    a.clear();
    expect(a.keyFor(body)).not.toBe(k1);
  });

  it('genera una clave nueva si el contenido del pedido cambia', () => {
    const a = mk();
    const k1 = a.keyFor(body);
    const k2 = a.keyFor({ ...body, items: [{ productId: 1, quantity: 3 }] });
    expect(k2).not.toBe(k1);
    expect(a.keyFor({ ...body, notes: 'otra' })).not.toBe(k2);
  });

  it('puede restaurarse desde un intento guardado (reinicio de la app)', () => {
    const a = mk();
    const k = a.keyFor(body);
    const restored = new IdempotencyAttempt(() => 'nueva', a.snapshot());
    expect(restored.keyFor(body)).toBe(k);
  });

  it('la huella ignora el orden y los espacios de las observaciones', () => {
    expect(fingerprintOrder({ items: [{ productId: 2, quantity: 1 }, { productId: 1, quantity: 1 }], notes: ' x ' }))
      .toBe(fingerprintOrder({ items: [{ productId: 1, quantity: 1 }, { productId: 2, quantity: 1 }], notes: 'x' }));
  });
});
