// Formateo de cantidades, fechas y precios (lógica pura, sin dependencias de React Native).

export const MAX_DECIMALS = 3;

const round3 = (n: number) => Math.round((n + Number.EPSILON) * 1000) / 1000;

/** 12 -> "12", 1.5 -> "1.5", 0.125 -> "0.125". Máximo 3 decimales, sin ceros sobrantes. */
export function formatQuantity(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '0';
  const n = round3(Number(value));
  let text = n.toFixed(MAX_DECIMALS);
  text = text.replace(/\.?0+$/, '');
  const [int, dec] = text.split('.');
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return dec ? `${grouped}.${dec}` : grouped;
}

export function formatQuantityWithUnit(value: number | null | undefined, unit: string): string {
  return `${formatQuantity(value)} ${unit}`.trim();
}

/** Unidades que admiten fracciones (kilos, litros, gramos...). */
export function isDecimalUnit(unitName: string | null | undefined): boolean {
  if (!unitName) return false;
  return /^(kg|kilo|kilogramo|l|lt|lts|litro|g|gr|gramo|ml|mililitro)/i.test(unitName.trim());
}

/**
 * Convierte lo escrito por la persona en cantidad.
 * Acepta coma o punto decimal. Devuelve null si no es válido.
 * Recorta a 3 decimales. Si la unidad no admite decimales, redondea hacia abajo al entero.
 */
export function parseQuantityInput(text: string, allowDecimals = true): number | null {
  const clean = text.trim().replace(',', '.');
  if (clean === '' || !/^\d*\.?\d*$/.test(clean) || clean === '.') return null;
  let n = Number(clean);
  if (!Number.isFinite(n)) return null;
  n = allowDecimals ? Math.floor(n * 1000 + 1e-9) / 1000 : Math.floor(n);
  return n;
}

/** Limita lo que se escribe en el campo: solo dígitos y un separador con hasta 3 decimales. */
export function sanitizeQuantityText(text: string, allowDecimals = true): string {
  let t = text.replace(/[^0-9.,]/g, '').replace(',', '.');
  if (!allowDecimals) return t.replace(/\./g, '');
  const dot = t.indexOf('.');
  if (dot >= 0) {
    t = t.slice(0, dot + 1) + t.slice(dot + 1).replace(/\./g, '').slice(0, MAX_DECIMALS);
  }
  return t;
}

export const MAX_QUANTITY = 1_000_000;

export function clampQuantity(n: number): number {
  return round3(Math.min(MAX_QUANTITY, Math.max(0, n)));
}

export function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) return '';
  return `$${Number(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

interface Parts { day: number; month: number; year: number; hour: number; minute: number }

function partsInZone(date: Date, timeZone?: string): Parts {
  if (timeZone) {
    try {
      const fmt = new Intl.DateTimeFormat('en-US', {
        timeZone, hourCycle: 'h23', year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: 'numeric',
      });
      const get = (type: string) => Number(fmt.formatToParts(date).find((p) => p.type === type)?.value);
      const parts = { day: get('day'), month: get('month'), year: get('year'), hour: get('hour') % 24, minute: get('minute') };
      if (Object.values(parts).every((v) => Number.isFinite(v))) return parts;
    } catch {
      // zona no soportada: se usa la hora del teléfono
    }
  }
  return { day: date.getDate(), month: date.getMonth() + 1, year: date.getFullYear(), hour: date.getHours(), minute: date.getMinutes() };
}

const pad = (n: number) => String(n).padStart(2, '0');

/** "20 oct 2026, 14:05" en la zona horaria indicada (la de GET /api/config). */
export function formatDateTime(iso: string | null | undefined, timeZone?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const p = partsInZone(d, timeZone);
  return `${p.day} ${MONTHS[p.month - 1]} ${p.year}, ${pad(p.hour)}:${pad(p.minute)}`;
}

export function formatTime(iso: string | null | undefined, timeZone?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const p = partsInZone(d, timeZone);
  return `${pad(p.hour)}:${pad(p.minute)}`;
}
