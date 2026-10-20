import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import 'dayjs/locale/es';

dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.locale('es');

let tz = 'America/Mexico_City';
export function setTimezone(value: string | undefined) {
  if (!value) return;
  try { new Intl.DateTimeFormat('es', { timeZone: value }); tz = value; } catch { /* zona inválida */ }
}
export const getTimezone = () => tz;

const inTz = (iso: string) => dayjs(iso).tz(tz);

export const fmtDateTime = (iso?: string | null) => (iso ? inTz(iso).format('DD/MM/YYYY HH:mm') : '—');
export const fmtDate = (iso?: string | null) => (iso ? inTz(iso).format('DD/MM/YYYY') : '—');
export const fmtTime = (iso?: string | null) => (iso ? inTz(iso).format('HH:mm:ss') : '—');
export const fmtDay = (day: string) => dayjs(day).format('ddd DD/MM');
/** Fecha YYYY-MM-DD (sin hora): se muestra tal cual, sin conversión de zona. */
export const fmtPlainDate = (d?: string | null) => (d ? dayjs(d.slice(0, 10)).format('DD/MM/YYYY') : '—');

/** Inicio del día (en la zona del negocio) como ISO UTC; `offsetDays=1` da el inicio del día siguiente. */
export function dayBoundaryISO(date: dayjs.Dayjs, offsetDays = 0): string {
  return dayjs.tz(date.format('YYYY-MM-DD'), tz).add(offsetDays, 'day').startOf('day').toISOString();
}

const nf = new Intl.NumberFormat('es-MX', { maximumFractionDigits: 3 });
const cf = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });
export const fmtQty = (n?: number | null) => (n === null || n === undefined ? '—' : nf.format(n));
export const fmtMoney = (n?: number | null) => (n === null || n === undefined ? '—' : cf.format(n));
export const fmtSigned = (n: number) => (n > 0 ? `+${nf.format(n)}` : nf.format(n));
