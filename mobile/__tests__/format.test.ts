import {
  clampQuantity, formatDateTime, formatQuantity, formatQuantityWithUnit, isDecimalUnit, parseQuantityInput, sanitizeQuantityText,
} from '../src/lib/format';
import { normalizeServerUrl } from '../src/lib/serverUrl';

describe('cantidades', () => {
  it('formatea sin ceros sobrantes y con máximo 3 decimales', () => {
    expect(formatQuantity(12)).toBe('12');
    expect(formatQuantity(1.5)).toBe('1.5');
    expect(formatQuantity(0.1 + 0.2)).toBe('0.3');
    expect(formatQuantity(2.12345)).toBe('2.123');
    expect(formatQuantity(1234.5)).toBe('1,234.5');
    expect(formatQuantity(null)).toBe('0');
    expect(formatQuantityWithUnit(2.5, 'Kilogramo')).toBe('2.5 Kilogramo');
  });
  it('interpreta la entrada con coma o punto y recorta a 3 decimales', () => {
    expect(parseQuantityInput('2,5')).toBe(2.5);
    expect(parseQuantityInput('1.23456')).toBe(1.234);
    expect(parseQuantityInput('abc')).toBeNull();
    expect(parseQuantityInput('')).toBeNull();
    expect(parseQuantityInput('.')).toBeNull();
    expect(parseQuantityInput('3.9', false)).toBe(3);
  });
  it('limpia lo escrito', () => {
    expect(sanitizeQuantityText('1,2345')).toBe('1.234');
    expect(sanitizeQuantityText('1.2.3')).toBe('1.23');
    expect(sanitizeQuantityText('4.5', false)).toBe('45');
    expect(sanitizeQuantityText('a1b')).toBe('1');
  });
  it('limita el rango', () => {
    expect(clampQuantity(-3)).toBe(0);
    expect(clampQuantity(2_000_000)).toBe(1_000_000);
  });
  it('detecta unidades con decimales', () => {
    expect(isDecimalUnit('Kilogramo')).toBe(true);
    expect(isDecimalUnit('Litro')).toBe(true);
    expect(isDecimalUnit('Pieza')).toBe(false);
    expect(isDecimalUnit('Caja')).toBe(false);
  });
});

describe('fechas', () => {
  it('muestra la hora en la zona horaria indicada', () => {
    expect(formatDateTime('2026-10-20T20:05:00Z', 'America/Mexico_City')).toBe('20 oct 2026, 14:05');
    expect(formatDateTime('2026-01-01T03:30:00Z', 'UTC')).toBe('1 ene 2026, 03:30');
  });
  it('maneja valores vacíos o inválidos', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime('no-es-fecha')).toBe('—');
  });
});

describe('dirección del servidor', () => {
  it('normaliza', () => {
    expect(normalizeServerUrl('192.168.1.100:3000')).toEqual({ ok: true, url: 'http://192.168.1.100:3000' });
    expect(normalizeServerUrl(' http://192.168.1.100:3000/ ')).toEqual({ ok: true, url: 'http://192.168.1.100:3000' });
    expect(normalizeServerUrl('http://servidor.local:3000/api')).toEqual({ ok: true, url: 'http://servidor.local:3000' });
    expect(normalizeServerUrl('https://almacen.lan')).toEqual({ ok: true, url: 'https://almacen.lan' });
  });
  it('rechaza lo inválido', () => {
    expect(normalizeServerUrl('').ok).toBe(false);
    expect(normalizeServerUrl('http://').ok).toBe(false);
    expect(normalizeServerUrl('ftp://x').ok).toBe(false);
    expect(normalizeServerUrl('http://a b:3000').ok).toBe(false);
    expect(normalizeServerUrl('http://1.2.3.4:99999').ok).toBe(false);
    expect(normalizeServerUrl('http://1.2.3.4:3000/foo').ok).toBe(false);
  });
});
