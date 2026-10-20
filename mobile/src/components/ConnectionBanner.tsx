import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useConnection } from '../context/ConnectionContext';
import { colors, font, spacing } from '../theme';

/** Banner visible cuando no hay conexión con el servidor o falta el tiempo real. */
export function ConnectionBanner() {
  const { link, checkNow } = useConnection();
  if (link === 'online') return null;
  const offline = link === 'offline';
  return (
    <Pressable onPress={checkNow} style={[styles.banner, { backgroundColor: offline ? colors.danger : colors.warning }]}>
      <Text style={styles.text}>
        {offline
          ? 'Sin conexión con el servidor. Revisa que estés en el Wi-Fi del restaurante. Toca para reintentar.'
          : 'Conectado, pero sin avisos en vivo. Se reconectará solo.'}
      </Text>
    </Pressable>
  );
}

/** Indicador pequeño: punto de color + texto. */
export function ConnectionDot() {
  const { link } = useConnection();
  const color = link === 'online' ? colors.success : link === 'degraded' ? colors.warning : colors.danger;
  const label = link === 'online' ? 'Conectado' : link === 'degraded' ? 'Parcial' : 'Sin conexión';
  return (
    <View style={styles.dotWrap} accessibilityLabel={`Estado de conexión: ${label}`}>
      <View style={[styles.dot, { backgroundColor: color }]} />
      <Text style={styles.dotText}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: { paddingVertical: spacing.sm, paddingHorizontal: spacing.lg },
  text: { color: colors.white, fontSize: font.small + 1, fontWeight: '600', textAlign: 'center' },
  dotWrap: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  dot: { width: 12, height: 12, borderRadius: 6 },
  dotText: { fontSize: font.small, color: colors.textMuted, fontWeight: '600' },
});
