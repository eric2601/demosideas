import React, { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ApiError } from '../api/client';
import { api } from '../api/session';
import { Button, Card, Field, Notice } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useServer } from '../context/ServerContext';
import { normalizeServerUrl } from '../lib/serverUrl';
import { colors, font, spacing } from '../theme';

export function LoginScreen() {
  const { baseUrl, setBaseUrl } = useServer();
  const { login, notice, clearNotice } = useAuth();
  const [urlText, setUrlText] = useState(baseUrl ?? '');
  const [urlError, setUrlError] = useState<string | null>(null);
  const [test, setTest] = useState<{ ok: boolean; text: string } | null>(null);
  const [testing, setTesting] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const validateUrl = () => {
    const r = normalizeServerUrl(urlText);
    if (!r.ok) { setUrlError(r.error); return null; }
    setUrlError(null);
    return r.url;
  };

  const onTest = async () => {
    setTest(null);
    const url = validateUrl();
    if (!url) return;
    setTesting(true);
    try {
      await api.health(url);
      let name = '';
      try { name = (await api.config(url)).companyName ?? ''; } catch { /* opcional */ }
      await setBaseUrl(url);
      setUrlText(url);
      setTest({ ok: true, text: `Conexión correcta${name ? ` con ${name}` : ''}. Dirección guardada.` });
    } catch (e) {
      setTest({ ok: false, text: e instanceof ApiError ? e.message : 'No se pudo conectar con el servidor.' });
    } finally {
      setTesting(false);
    }
  };

  const onLogin = async () => {
    setError(null);
    clearNotice();
    const url = validateUrl();
    if (!url) return;
    if (!username.trim() || !password) { setError('Escribe tu usuario y tu contraseña.'); return; }
    setBusy(true);
    try {
      if (url !== baseUrl) await setBaseUrl(url);
      await login(username, password);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo iniciar sesión. Intenta de nuevo.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
          <Text style={styles.title}>Pedidos Almacén</Text>
          <Text style={styles.subtitle}>Pide productos a tu almacén de forma fácil y rápida.</Text>

          {notice ? <Notice kind="warning">{notice}</Notice> : null}

          <Card>
            <Text style={styles.section}>1. Servidor</Text>
            <Field
              label="Dirección del servidor"
              value={urlText}
              onChangeText={(t) => { setUrlText(t); setUrlError(null); setTest(null); }}
              placeholder="http://192.168.1.100:3000"
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="url"
              error={urlError}
            />
            <Text style={styles.hint}>Pídele a quien administra el almacén la dirección (IP y puerto).</Text>
            <Button title="Probar conexión" variant="secondary" onPress={onTest} loading={testing} />
            {test ? <View style={{ marginTop: spacing.md }}><Notice kind={test.ok ? 'success' : 'error'}>{test.text}</Notice></View> : null}
          </Card>

          <Card>
            <Text style={styles.section}>2. Tu cuenta</Text>
            <Field label="Usuario" value={username} onChangeText={setUsername} autoCapitalize="none" autoCorrect={false} textContentType="username" />
            <Field label="Contraseña" value={password} onChangeText={setPassword} secureTextEntry autoCapitalize="none" textContentType="password" onSubmitEditing={onLogin} />
            {error ? <Notice kind="error">{error}</Notice> : null}
            <Button title="Entrar" onPress={onLogin} loading={busy} />
          </Card>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  scroll: { padding: spacing.lg, paddingBottom: spacing.xxl },
  title: { fontSize: font.hero, fontWeight: '800', color: colors.primaryDark, marginTop: spacing.lg },
  subtitle: { fontSize: font.body, color: colors.textMuted, marginBottom: spacing.lg, marginTop: spacing.xs },
  section: { fontSize: font.large, fontWeight: '700', color: colors.text, marginBottom: spacing.md },
  hint: { fontSize: font.small, color: colors.textMuted, marginBottom: spacing.md },
});
