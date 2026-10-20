import React from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import { api } from '../api/session';
import { ConnectionDot } from '../components/ConnectionBanner';
import { Button, Card, Notice } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { useServer } from '../context/ServerContext';
import { computeCounters, ALL_STATUSES, FilterKey, OrderCounters } from '../lib/status';
import { useLoader } from '../lib/useLoader';
import type { TabParams } from '../navigation/types';
import { colors, font, radius, spacing, TOUCH } from '../theme';

async function loadCounters(): Promise<OrderCounters> {
  const open = ALL_STATUSES.filter((s) => s !== 'entregado' && s !== 'cancelado');
  const results = await Promise.all(open.map((s) => api.listOrders({ status: s, pageSize: 1 })));
  const byStatus: Record<string, number> = {};
  open.forEach((s, i) => { byStatus[s] = results[i].total; });
  return computeCounters(byStatus);
}

export function HomeScreen({ navigation }: BottomTabScreenProps<TabParams, 'Inicio'>) {
  const { user, logout } = useAuth();
  const { serverConfig } = useServer();
  const cart = useCart();
  const { data, error, refreshing, pull } = useLoader(loadCounters);

  const go = (filter: FilterKey) => navigation.navigate('Pedidos', { filter });

  return (
    <SafeAreaView style={styles.safe} edges={['left', 'right']}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={pull} />}
      >
        <View style={styles.header}>
          <View style={{ flex: 1 }}>
            <Text style={styles.hello}>Hola, {user?.fullName.split(' ')[0]}</Text>
            <Text style={styles.restaurant}>{user?.restaurant?.name}</Text>
            {serverConfig?.companyName ? <Text style={styles.company}>{serverConfig.companyName}</Text> : null}
          </View>
          <ConnectionDot />
        </View>

        <Button title="Crear pedido" onPress={() => navigation.navigate('Catalogo')} style={styles.big} />
        {cart.count > 0 ? (
          <Text style={styles.cartHint}>Tienes {cart.count} producto(s) en tu pedido sin enviar.</Text>
        ) : null}

        <Text style={styles.sectionTitle}>Tus pedidos</Text>
        {error && !data ? <Notice kind="error">{error}</Notice> : null}
        <View style={styles.counters}>
          <CounterCard label="Pendientes" value={data?.pendientes} color="#8A5A00" bg="#FFF1CC" onPress={() => go('pendientes')} />
          <CounterCard label="En preparación" value={data?.enPreparacion} color="#3949AB" bg="#E8EAF8" onPress={() => go('enPreparacion')} />
          <CounterCard label="Listos" value={data?.listos} color="#00695C" bg="#DDF3EF" onPress={() => go('listos')} />
        </View>

        <Button title="Ver historial de pedidos" variant="ghost" onPress={() => go('todos')} />
        <Card style={{ marginTop: spacing.lg }}>
          <Text style={styles.small}>Cuenta: {user?.username}</Text>
          <Button title="Cerrar sesión" variant="ghost" small onPress={logout} style={{ marginTop: spacing.sm }} />
        </Card>
      </ScrollView>
    </SafeAreaView>
  );
}

function CounterCard({ label, value, color, bg, onPress }: { label: string; value?: number; color: string; bg: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={[styles.counter, { backgroundColor: bg }]}>
      <Text style={[styles.counterValue, { color }]}>{value ?? '–'}</Text>
      <Text style={[styles.counterLabel, { color }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  scroll: { padding: spacing.lg, paddingBottom: spacing.xxl },
  header: { flexDirection: 'row', alignItems: 'flex-start', marginBottom: spacing.lg },
  hello: { fontSize: font.body, color: colors.textMuted },
  restaurant: { fontSize: font.hero, fontWeight: '800', color: colors.text },
  company: { fontSize: font.small, color: colors.textMuted },
  big: { minHeight: 72, marginBottom: spacing.sm },
  cartHint: { color: colors.warning, fontSize: font.body, marginBottom: spacing.sm },
  sectionTitle: { fontSize: font.large, fontWeight: '700', marginTop: spacing.lg, marginBottom: spacing.md, color: colors.text },
  counters: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.lg },
  counter: { flex: 1, minHeight: TOUCH * 2, borderRadius: radius.lg, alignItems: 'center', justifyContent: 'center', padding: spacing.sm },
  counterValue: { fontSize: 36, fontWeight: '800' },
  counterLabel: { fontSize: font.small + 1, fontWeight: '700', textAlign: 'center' },
  small: { fontSize: font.small, color: colors.textMuted },
});
