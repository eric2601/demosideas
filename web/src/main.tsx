import React, { useState, type ReactNode } from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import esES from 'antd/locale/es_ES';
import dayjs from 'dayjs';
import 'dayjs/locale/es';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import { ApiError, errorText } from './api/client';
import { useAppConfig } from './api/hooks';
import { setTimezone } from './utils/format';
import './styles.css';

dayjs.locale('es');

/** Cliente de consultas con manejo centralizado de errores (muestra el mensaje del servidor). */
function Providers({ children }: { children: ReactNode }) {
  const { message } = AntApp.useApp();
  const [client] = useState(
    () => new QueryClient({
      queryCache: new QueryCache({
        onError: (err) => {
          // 401: el manejador de sesión ya regresa al login
          if (err instanceof ApiError && err.status === 401) return;
          message.error({ content: errorText(err), key: 'query-error' });
        },
      }),
      mutationCache: new MutationCache({
        onError: (err) => {
          if (err instanceof ApiError && err.status === 401) return;
          message.error(errorText(err));
        },
      }),
      defaultOptions: {
        queries: {
          retry: (n, err) => !(err instanceof ApiError && err.status >= 400 && err.status < 500) && n < 1,
          refetchOnWindowFocus: false,
          staleTime: 15_000,
        },
      },
    }),
  );
  return (
    <QueryClientProvider client={client}>
      <AuthProvider>
        <TimezoneSync />
        {children}
      </AuthProvider>
    </QueryClientProvider>
  );
}

/** Aplica la zona horaria del servidor a todo el formato de fechas. */
function TimezoneSync() {
  const { data } = useAppConfig();
  setTimezone(data?.timezone);
  return null;
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={esES} theme={{ token: { colorPrimary: '#1668dc', borderRadius: 6 } }}>
      <AntApp notification={{ top: 72, placement: "topRight", maxCount: 4 }} message={{ top: 72 }}>
        <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <Providers>
            <App />
          </Providers>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>,
);
