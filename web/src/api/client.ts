// Cliente HTTP tipado. Rutas relativas (/api) para funcionar en el mismo origen que el servidor.

const TOKEN_KEY = 'almacen.token';

let token: string | null = null;
try { token = sessionStorage.getItem(TOKEN_KEY); } catch { /* almacenamiento no disponible */ }

let onUnauthorized: (() => void) | null = null;

export const getToken = () => token;
export function setToken(value: string | null) {
  token = value;
  try {
    if (value) sessionStorage.setItem(TOKEN_KEY, value);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch { /* ignorar */ }
}
export const setUnauthorizedHandler = (fn: (() => void) | null) => { onUnauthorized = fn; };

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public details?: unknown) {
    super(message);
  }
}

type Query = Record<string, string | number | boolean | undefined | null>;

export function qs(params?: Query): string {
  if (!params) return '';
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : '';
}

async function request<T>(method: string, path: string, body?: unknown, params?: Query): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(`/api${path}${qs(params)}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'NETWORK', 'No se pudo conectar con el servidor. Revisa tu conexión.');
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let json: any = null;
  if (text) { try { json = JSON.parse(text); } catch { /* respuesta no JSON */ } }

  if (!res.ok) {
    const err = json?.error;
    if (res.status === 401 && !path.startsWith('/auth/login')) onUnauthorized?.();
    throw new ApiError(res.status, err?.code ?? 'ERROR', err?.message ?? `Error ${res.status}`, err?.details);
  }
  return json as T;
}

export const api = {
  get: <T>(path: string, params?: Query) => request<T>('GET', path, undefined, params),
  post: <T = void>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T = void>(path: string, body?: unknown) => request<T>('PUT', path, body ?? {}),
  patch: <T = void>(path: string, body?: unknown) => request<T>('PATCH', path, body ?? {}),
  del: <T = void>(path: string) => request<T>('DELETE', path),
};

/** Texto de error para mostrar: mensaje del servidor más el primer detalle de validación, si existe. */
export function errorText(err: unknown): string {
  if (err instanceof ApiError) {
    const d = err.details;
    if (Array.isArray(d) && d.length) {
      const first = d[0] as { path?: string; message?: string };
      if (first?.message) return `${err.message}: ${first.message}`;
    }
    return err.message;
  }
  return err instanceof Error ? err.message : 'Ocurrió un error inesperado';
}
