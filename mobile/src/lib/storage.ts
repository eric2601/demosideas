import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';

// Todas las operaciones están protegidas: si el almacenamiento falla, la app sigue funcionando.

const KEYS = {
  serverUrl: 'server_url',
  token: 'auth_token',
  cart: 'cart_v1',
  attempt: 'order_attempt_v1',
};

export const storage = {
  async getServerUrl(): Promise<string | null> {
    try { return await AsyncStorage.getItem(KEYS.serverUrl); } catch { return null; }
  },
  async setServerUrl(url: string): Promise<void> {
    try { await AsyncStorage.setItem(KEYS.serverUrl, url); } catch { /* sin persistencia */ }
  },
  async getToken(): Promise<string | null> {
    try { return await SecureStore.getItemAsync(KEYS.token); } catch { return null; }
  },
  async setToken(token: string | null): Promise<void> {
    try {
      if (token) await SecureStore.setItemAsync(KEYS.token, token);
      else await SecureStore.deleteItemAsync(KEYS.token);
    } catch { /* sin persistencia */ }
  },
  async getJson<T>(key: 'cart' | 'attempt'): Promise<T | null> {
    try {
      const raw = await AsyncStorage.getItem(KEYS[key]);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch { return null; }
  },
  async setJson(key: 'cart' | 'attempt', value: unknown | null): Promise<void> {
    try {
      if (value === null) await AsyncStorage.removeItem(KEYS[key]);
      else await AsyncStorage.setItem(KEYS[key], JSON.stringify(value));
    } catch { /* sin persistencia */ }
  },
};
