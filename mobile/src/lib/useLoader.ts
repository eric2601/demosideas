import { useCallback, useEffect, useRef, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import { ApiError } from '../api/client';
import { useConnection } from '../context/ConnectionContext';

export function errorText(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  return 'Ocurrió un problema inesperado. Intenta de nuevo.';
}

/**
 * Carga datos por REST: al entrar a la pantalla, al reconectar o cuando llega un evento
 * del servidor (dataVersion), y con pull-to-refresh. Conserva los datos previos si falla un refresco.
 */
export function useLoader<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const { dataVersion } = useConnection();
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const seq = useRef(0);
  const mounted = useRef(true);
  useEffect(() => () => { mounted.current = false; }, []);

  const run = useCallback(async (pull = false) => {
    const mine = ++seq.current;
    if (pull) setRefreshing(true);
    try {
      const r = await fnRef.current();
      if (!mounted.current || mine !== seq.current) return;
      setData(r);
      setError(null);
    } catch (e) {
      if (!mounted.current || mine !== seq.current) return;
      setError(errorText(e));
    } finally {
      if (mounted.current && mine === seq.current) { setLoading(false); setRefreshing(false); }
    }
  }, []);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const key = JSON.stringify(deps);
  useFocusEffect(useCallback(() => { run(); }, [run, dataVersion, key]));

  return { data, loading, refreshing, error, reload: () => run(false), pull: () => run(true), setData };
}
