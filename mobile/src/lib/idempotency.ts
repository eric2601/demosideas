// Clave de idempotencia para POST /orders.
// Regla: se genera UNA vez por intento de pedido y se reutiliza en cada reintento
// mientras el contenido del pedido no cambie y no haya habido éxito.
// Si el usuario modifica el carrito tras un fallo, el contenido ya es otro pedido
// y se genera una clave nueva (así nunca se devuelve un pedido distinto al pedido enviado).

export interface OrderPayloadLike {
  items: { productId: number; quantity: number }[];
  notes?: string;
}

export interface StoredAttempt {
  key: string;
  fingerprint: string;
}

/** Huella estable del contenido del pedido (independiente del orden de las partidas). */
export function fingerprintOrder(payload: OrderPayloadLike): string {
  const items = [...payload.items]
    .map((i) => `${i.productId}:${Number(i.quantity)}`)
    .sort()
    .join('|');
  return `${items}#${(payload.notes ?? '').trim()}`;
}

export class IdempotencyAttempt {
  private current: StoredAttempt | null;

  constructor(
    private readonly generate: () => string,
    initial: StoredAttempt | null = null,
  ) {
    this.current = initial;
  }

  /** Devuelve la clave del intento en curso, creándola si no existe o si el pedido cambió. */
  keyFor(payload: OrderPayloadLike): string {
    const fingerprint = fingerprintOrder(payload);
    if (!this.current || this.current.fingerprint !== fingerprint) {
      this.current = { key: this.generate(), fingerprint };
    }
    return this.current.key;
  }

  /** Llamar tras un envío exitoso: el siguiente pedido usará una clave nueva. */
  clear(): void {
    this.current = null;
  }

  snapshot(): StoredAttempt | null {
    return this.current ? { ...this.current } : null;
  }
}
