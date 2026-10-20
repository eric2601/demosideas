import React, { useState } from 'react';
import { ScrollView, StyleSheet, Text } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ApiError } from '../api/client';
import { api } from '../api/session';
import { Button, Field, Notice } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { colors, font, spacing } from '../theme';

export function validateNewPassword(p: string): string | null {
  if (p.length < 8) return 'La contraseña nueva debe tener al menos 8 caracteres.';
  if (!/[A-Za-z]/.test(p) || !/\d/.test(p)) return 'Debe contener letras y números.';
  return null;
}

/** Pantalla obligatoria cuando el usuario debe cambiar su contraseña temporal. */
export function ChangePasswordScreen() {
  const { refreshUser, logout } = useAuth();
  const toast = useToast();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setError(null);
    if (!current) { setError('Escribe tu contraseña actual (la temporal).'); return; }
    const v = validateNewPassword(next);
    if (v) { setError(v); return; }
    if (next !== confirm) { setError('Las contraseñas nuevas no coinciden.'); return; }
    setBusy(true);
    try {
      await api.changePassword(current, next);
      await refreshUser();
      toast.show('Contraseña actualizada', 'success');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo cambiar la contraseña.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>Crea tu contraseña</Text>
        <Text style={styles.sub}>Por seguridad debes cambiar la contraseña temporal antes de continuar.</Text>
        <Field label="Contraseña actual" value={current} onChangeText={setCurrent} secureTextEntry autoCapitalize="none" />
        <Field label="Contraseña nueva" value={next} onChangeText={setNext} secureTextEntry autoCapitalize="none" />
        <Text style={styles.hint}>Mínimo 8 caracteres, con letras y números.</Text>
        <Field label="Repite la contraseña nueva" value={confirm} onChangeText={setConfirm} secureTextEntry autoCapitalize="none" />
        {error ? <Notice kind="error">{error}</Notice> : null}
        <Button title="Guardar contraseña" onPress={submit} loading={busy} />
        <Button title="Salir" variant="ghost" onPress={logout} style={{ marginTop: spacing.md }} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  scroll: { padding: spacing.lg },
  title: { fontSize: font.hero, fontWeight: '800', color: colors.primaryDark, marginTop: spacing.lg },
  sub: { fontSize: font.body, color: colors.textMuted, marginBottom: spacing.lg, marginTop: spacing.xs },
  hint: { fontSize: font.small, color: colors.textMuted, marginBottom: spacing.md, marginTop: -spacing.sm },
});
