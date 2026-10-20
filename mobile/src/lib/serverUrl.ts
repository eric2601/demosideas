export type UrlResult = { ok: true; url: string } | { ok: false; error: string };

/**
 * Valida y normaliza la dirección del servidor.
 * "192.168.1.100:3000" -> "http://192.168.1.100:3000". Quita "/" finales y "/api".
 */
export function normalizeServerUrl(input: string): UrlResult {
  let text = (input ?? '').trim();
  if (!text) return { ok: false, error: 'Escribe la dirección del servidor, por ejemplo http://192.168.1.100:3000' };
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(text)) text = `http://${text}`;
  const m = /^(https?):\/\/(\[[0-9a-f:]+\]|[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*)(:(\d{1,5}))?(\/.*)?$/i.exec(text);
  if (!m) return { ok: false, error: 'La dirección no es válida. Ejemplo: http://192.168.1.100:3000' };
  const port = m[7] ? Number(m[7]) : null;
  if (port !== null && (port < 1 || port > 65535)) return { ok: false, error: 'El puerto debe estar entre 1 y 65535' };
  let path = (m[8] ?? '').replace(/\/+$/, '');
  path = path.replace(/\/api$/i, '');
  if (path && path !== '/') return { ok: false, error: 'Escribe solo la dirección y el puerto, sin rutas adicionales' };
  return { ok: true, url: `${m[1].toLowerCase()}://${m[2]}${port !== null ? `:${port}` : ''}` };
}
