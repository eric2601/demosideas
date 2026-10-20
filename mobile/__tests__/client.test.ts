import { ApiError, buildQuery, createHttpClient } from '../src/api/client';

const resp = (status: number, body: unknown) =>
  ({ ok: status >= 200 && status < 300, status, text: async () => (body === undefined ? '' : JSON.stringify(body)) }) as Response;

describe('cliente HTTP', () => {
  it('envía token, cabeceras y cuerpo', async () => {
    const f = jest.fn().mockResolvedValue(resp(200, { ok: 1 }));
    const c = createHttpClient({ getBaseUrl: () => 'http://x:3000', getToken: () => 'tok', fetchImpl: f as any });
    await c.request('/orders', { method: 'POST', body: { a: 1 }, headers: { 'Idempotency-Key': 'k' }, query: { page: 2, status: undefined } });
    const [url, init] = f.mock.calls[0];
    expect(url).toBe('http://x:3000/api/orders?page=2');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(init.headers['Idempotency-Key']).toBe('k');
    expect(init.body).toBe('{"a":1}');
  });

  it('usa error.message del servidor y avisa 401 solo en peticiones autenticadas', async () => {
    const onUnauthorized = jest.fn();
    const f = jest.fn().mockResolvedValue(resp(401, { error: { code: 'UNAUTHORIZED', message: 'Usuario o contraseña incorrectos' } }));
    const c = createHttpClient({ getBaseUrl: () => 'http://x', getToken: () => 't', onUnauthorized, fetchImpl: f as any });
    await expect(c.request('/auth/login', { auth: false })).rejects.toMatchObject({ message: 'Usuario o contraseña incorrectos', status: 401 });
    expect(onUnauthorized).not.toHaveBeenCalled();
    await expect(c.request('/orders')).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('clasifica fallas de red', async () => {
    const f = jest.fn().mockRejectedValue(new TypeError('Network request failed'));
    const c = createHttpClient({ getBaseUrl: () => 'http://x', getToken: () => null, fetchImpl: f as any });
    const err: any = await c.request('/health').catch((e: unknown) => e);
    expect(err.kind).toBe('network');
    expect(err.isConnectionProblem).toBe(true);
  });

  it('aborta por tiempo de espera', async () => {
    const f = jest.fn((_u: string, init: any) => new Promise((_res, rej) => {
      init.signal.addEventListener('abort', () => rej(new Error('aborted')));
    }));
    const c = createHttpClient({ getBaseUrl: () => 'http://x', getToken: () => null, fetchImpl: f as any, timeoutMs: 20 });
    const err: any = await c.request('/health').catch((e: unknown) => e);
    expect(err.kind).toBe('timeout');
  });

  it('sin servidor configurado devuelve un error claro', async () => {
    const c = createHttpClient({ getBaseUrl: () => null, getToken: () => null });
    await expect(c.request('/health')).rejects.toMatchObject({ code: 'NO_SERVER' });
  });

  it('arma la query ignorando vacíos', () => {
    expect(buildQuery({ a: 1, b: '', c: undefined, d: 'x y' })).toBe('?a=1&d=x%20y');
    expect(buildQuery()).toBe('');
  });
});
