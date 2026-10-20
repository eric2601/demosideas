import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import { CompositeScreenProps, useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { api } from '../api/session';
import { Card, Chip, Empty, Loading, Notice, StatusBadge } from '../components/ui';
import { useConnection } from '../context/ConnectionContext';
import { useServer } from '../context/ServerContext';
import { formatDateTime } from '../lib/format';
import { FILTERS, FilterKey } from '../lib/status';
import { errorText } from '../lib/useLoader';
import type { AppStackParams, TabParams } from '../navigation/types';
import { colors, font, spacing } from '../theme';
import type { OrderSummary } from '../types';

type Props = CompositeScreenProps<BottomTabScreenProps<TabParams, 'Pedidos'>, NativeStackScreenProps<AppStackParams>>;
const PAGE = 25;

async function fetchPage(filter: FilterKey, page: number) {
  const def = FILTERS.find((f) => f.key === filter)!;
  if (def.statuses.length <= 1) {
    return api.listOrders({ status: def.statuses[0], page, pageSize: PAGE });
  }
  // Grupo de varios estados: se traen (hasta 100) de cada uno y se combinan por fecha
  const parts = await Promise.all(def.statuses.map((s) => api.listOrders({ status: s, pageSize: 100 })));
  const data = parts.flatMap((p) => p.data).sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
  return { data, total: data.length, page: 1, pageSize: data.length };
}

export function OrdersScreen({ navigation, route }: Props) {
  const { timezone } = useServer();
  const { dataVersion } = useConnection();
  const [filter, setFilter] = useState<FilterKey>('todos');
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const seq = useRef(0);

  // Filtro recibido desde la pantalla principal
  useEffect(() => {
    const f = route.params?.filter;
    if (f) setFilter(f);
  }, [route.params?.filter]);

  const load = useCallback(async (pull = false) => {
    const mine = ++seq.current;
    if (pull) setRefreshing(true);
    try {
      const r = await fetchPage(filter, 1);
      if (mine !== seq.current) return;
      setOrders(r.data); setTotal(r.total); setPage(1); setError(null);
    } catch (e) {
      if (mine === seq.current) setError(errorText(e));
    } finally {
      if (mine === seq.current) { setLoading(false); setRefreshing(false); }
    }
  }, [filter]);

  useFocusEffect(useCallback(() => { load(); }, [load, dataVersion]));

  const more = async () => {
    if (loadingMore || loading || orders.length >= total) return;
    setLoadingMore(true);
    try {
      const r = await fetchPage(filter, page + 1);
      setOrders((cur) => {
        const ids = new Set(cur.map((o) => o.id));
        return [...cur, ...r.data.filter((o) => !ids.has(o.id))];
      });
      setPage(page + 1);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={['left', 'right']}>
      <View style={styles.chips}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          {FILTERS.map((f) => (
            <Chip key={f.key} label={f.label} selected={filter === f.key}
              onPress={() => { if (f.key !== filter) { setLoading(true); setOrders([]); setFilter(f.key); } }} />
          ))}
        </ScrollView>
      </View>
      {loading && orders.length === 0 ? <Loading text="Cargando pedidos..." /> : (
        <FlatList
          data={orders}
          keyExtractor={(o) => String(o.id)}
          contentContainerStyle={{ padding: spacing.lg }}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => load(true)} />}
          onEndReached={more}
          onEndReachedThreshold={0.4}
          ListHeaderComponent={error ? <Notice kind="error">{error}</Notice> : null}
          ListEmptyComponent={<Empty title="No hay pedidos aquí" hint="Cuando envíes un pedido aparecerá en esta lista." />}
          ListFooterComponent={loadingMore ? <ActivityIndicator color={colors.primary} style={{ margin: spacing.lg }} /> : null}
          renderItem={({ item }) => (
            <Pressable accessibilityRole="button" onPress={() => navigation.navigate('DetallePedido', { orderId: item.id })}>
              <Card>
                <View style={styles.row}>
                  <Text style={styles.folio}>{item.folio}</Text>
                  <StatusBadge status={item.status} />
                </View>
                <Text style={styles.meta}>{formatDateTime(item.createdAt, timezone)}</Text>
                <Text style={styles.meta}>{item.itemCount} producto(s)</Text>
              </Card>
            </Pressable>
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  chips: { backgroundColor: colors.white, padding: spacing.md, borderBottomWidth: 1, borderBottomColor: colors.border },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.xs },
  folio: { fontSize: font.large, fontWeight: '800', color: colors.text },
  meta: { fontSize: font.body, color: colors.textMuted },
});
