// Estado de sesión de bajo nivel compartido por el cliente HTTP de la app.
// Se mantiene fuera de React para que el cliente siempre lea el token y la URL vigentes.
import { createHttpClient } from './client';
import { createApi } from './endpoints';

let baseUrl: string | null = null;
let token: string | null = null;
let unauthorizedHandler: (() => void) | null = null;

export const session = {
  setBaseUrl: (u: string | null) => { baseUrl = u; },
  getBaseUrl: () => baseUrl,
  setToken: (t: string | null) => { token = t; },
  getToken: () => token,
  setUnauthorizedHandler: (fn: (() => void) | null) => { unauthorizedHandler = fn; },
};

export const api = createApi(
  createHttpClient({
    getBaseUrl: () => baseUrl,
    getToken: () => token,
    onUnauthorized: () => unauthorizedHandler?.(),
  }),
);
