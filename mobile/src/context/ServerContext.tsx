import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, session } from '../api/session';
import { storage } from '../lib/storage';
import type { ServerConfig } from '../types';

interface ServerState {
  /** false mientras se lee la URL guardada */
  ready: boolean;
  baseUrl: string | null;
  serverConfig: ServerConfig | null;
  timezone: string | undefined;
  setBaseUrl: (url: string) => Promise<void>;
  refreshConfig: () => Promise<void>;
}

const Ctx = createContext<ServerState | null>(null);

export function ServerProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [baseUrl, setUrl] = useState<string | null>(null);
  const [serverConfig, setServerConfig] = useState<ServerConfig | null>(null);

  useEffect(() => {
    storage.getServerUrl().then((u) => {
      session.setBaseUrl(u);
      setUrl(u);
      setReady(true);
    });
  }, []);

  const setBaseUrl = useCallback(async (url: string) => {
    session.setBaseUrl(url);
    setUrl(url);
    setServerConfig(null);
    await storage.setServerUrl(url);
  }, []);

  const refreshConfig = useCallback(async () => {
    if (!session.getBaseUrl()) return;
    try {
      setServerConfig(await api.config());
    } catch {
      // se conserva la última configuración conocida
    }
  }, []);

  useEffect(() => {
    if (baseUrl) refreshConfig();
  }, [baseUrl, refreshConfig]);

  const value = useMemo(
    () => ({ ready, baseUrl, serverConfig, timezone: serverConfig?.timezone, setBaseUrl, refreshConfig }),
    [ready, baseUrl, serverConfig, setBaseUrl, refreshConfig],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useServer(): ServerState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useServer fuera de ServerProvider');
  return v;
}
