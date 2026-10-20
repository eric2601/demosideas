// Cliente HTTP tipado, sin dependencias de React Native (se reutiliza en el script de humo).

export type ApiErrorKind = 'network' | 'timeout' | 'http' | 'parse';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly kind: ApiErrorKind,
    public readonly status: number = 0,
    public readonly code: string = 'ERROR',
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** Falla de red o tiempo agotado: el servidor pudo no haber recibido (o procesado) la petición. */
  get isConnectionProblem(): boolean {
    return this.kind === 'network' || this.kind === 'timeout';
  }
}

export interface ClientOptions {
  /** Devuelve la URL base del servidor (sin /api), p. ej. http://192.168.1.100:3000 */
  getBaseUrl: () => string | null;
  getToken: () => string | null;
  /** Se invoca ante un 401 en una petición autenticada. */
  onUnauthorized?: () => void;
  timeoutMs?: number;
  fetchImpl?: typeof fetch;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  headers?: Record<string, string>;
  /** false = petición pública (login, health, config): sin token y sin tratar 401 como sesión vencida */
  auth?: boolean;
  timeoutMs?: number;
  /** URL base explícita (para "Probar conexión" antes de guardarla) */
  baseUrl?: string;
}

export const DEFAULT_TIMEOUT_MS = 15_000;

export function buildQuery(query?: RequestOptions['query']): string {
  if (!query) return '';
  const parts = Object.entries(query)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  return parts.length ? `?${parts.join('&')}` : '';
}

export function createHttpClient(opts: ClientOptions) {
  const doFetch = opts.fetchImpl ?? ((...a: Parameters<typeof fetch>) => fetch(...a));

  async function request<T>(path: string, o: RequestOptions = {}): Promise<T> {
    const base = o.baseUrl ?? opts.getBaseUrl();
    if (!base) throw new ApiError('Primero escribe la dirección del servidor.', 'network', 0, 'NO_SERVER');
    const auth = o.auth !== false;
    const headers: Record<string, string> = { Accept: 'application/json', ...o.headers };
    if (o.body !== undefined) headers['Content-Type'] = 'application/json';
    const token = auth ? opts.getToken() : null;
    if (token) headers.Authorization = `Bearer ${token}`;

    const controller = new AbortController();
    const timeoutMs = o.timeoutMs ?? opts.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);

    let res: Response;
    try {
      res = await doFetch(`${base}/api${path}${buildQuery(o.query)}`, {
        method: o.method ?? 'GET',
        headers,
        body: o.body !== undefined ? JSON.stringify(o.body) : undefined,
        signal: controller.signal,
      });
    } catch (err) {
      clearTimeout(timer);
      if (timedOut) {
        throw new ApiError('El servidor tardó demasiado en responder. Revisa que estés en la misma red Wi-Fi.', 'timeout', 0, 'TIMEOUT');
      }
      throw new ApiError('No se pudo conectar con el servidor. Revisa tu Wi-Fi y la dirección del servidor.', 'network', 0, 'NETWORK', err);
    }

    let text = '';
    try {
      text = await res.text();
    } catch (err) {
      clearTimeout(timer);
      if (timedOut) throw new ApiError('El servidor tardó demasiado en responder.', 'timeout', 0, 'TIMEOUT');
      throw new ApiError('Se perdió la conexión mientras se recibía la respuesta.', 'network', 0, 'NETWORK', err);
    }
    clearTimeout(timer);

    let json: any = undefined;
    if (text) {
      try { json = JSON.parse(text); } catch { json = undefined; }
    }

    if (!res.ok) {
      const e = json?.error;
      const message = typeof e?.message === 'string' ? e.message : `El servidor respondió con un error (${res.status}).`;
      if (res.status === 401 && auth) opts.onUnauthorized?.();
      throw new ApiError(message, 'http', res.status, e?.code ?? `HTTP_${res.status}`, e?.details);
    }
    if (text && json === undefined) {
      throw new ApiError('La respuesta del servidor no es válida. ¿La dirección es la correcta?', 'parse', res.status, 'BAD_RESPONSE');
    }
    return json as T;
  }

  return { request };
}

export type HttpClient = ReturnType<typeof createHttpClient>;
