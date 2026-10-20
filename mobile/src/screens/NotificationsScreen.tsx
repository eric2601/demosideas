import React, { useState } from 'react';
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import { CompositeScreenProps } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { api } from '../api/session';
import { Button, Card, Empty, Loading, Notice } from '../components/ui';
import { useConnection } from '../context/ConnectionContext';
import { useServer } from '../context/ServerContext';
import { formatDateTime } from '../lib/format';
import { errorText, useLoader } from '../lib/useLoader';
import type { AppStackParams, TabParams } from '../navigation/types';
import { colors, font, spacing } from '../theme';
import type { AppNotification } from '../types';

type Props = CompositeScreenProps<BottomTabScreenProps<TabParams, 'Avisos'>, NativeStackScreenProps<AppStackParams>>;

export function NotificationsScreen({ navigation }: Props) {
  const { timezone } = useServer();
  const { setUnreadCount } = useConnection();
  const { data, loading, error, refreshing, pull, setData } = useLoader(() => api.notifications({ pageSize: 50 }));
  const [actionError, setActionError] = useState<string | null>(null);

  const open = async (n: AppNotification) => {
    const orderId = Number((n.payload as { orderId?: unknown })?.orderId);
    if (!n.read && data) {
      setData({ ...data, unreadCount: Math.max(0, data.unreadCount - 1), data: data.data.map((x) => (x.id === n.id ? { ...x, read: true } : x)) });
      setUnreadCount(Math.max(0, data.unreadCount - 1));
      api.markNotificationRead(n.id).catch((e) => setActionError(errorText(e)));
    }
    if (Number.isInteger(orderId) && orderId > 0) navigation.navigate('DetallePedido', { orderId });
  };

  const readAll = async () => {
    try {
      await api.markAllNotificationsRead();
      if (data) setData({ unreadCount: 0, data: data.data.map((x) => ({ ...x, read: true })) });
      setUnreadCount(0);
    } catch (e) {
      setActionError(errorText(e));
    }
  };

  if (loading && !data) return <Loading text="Cargando avisos..." />;

  return (
    <SafeAreaView style={styles.safe} edges={['left', 'right']}>
      <FlatList
        data={data?.data ?? []}
        keyExtractor={(n) => String(n.id)}
        contentContainerStyle={{ padding: spacing.lg }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={pull} />}
        ListHeaderComponent={
          <View>
            {error ? <Notice kind="error">{error}</Notice> : null}
            {actionError ? <Notice kind="error">{actionError}</Notice> : null}
            {data && data.unreadCount > 0 ? (
              <Button title={`Marcar todos como leídos (${data.unreadCount})`} variant="secondary" small onPress={readAll} style={{ marginBottom: spacing.md }} />
            ) : null}
          </View>
        }
        ListEmptyComponent={<Empty title="No tienes avisos" hint="Aquí verás cuando el almacén cambie el estado de tus pedidos." />}
        renderItem={({ item }) => (
          <Pressable accessibilityRole="button" onPress={() => open(item)}>
            <Card style={!item.read ? { borderColor: colors.primary, backgroundColor: colors.primarySoft } : undefined}>
              <Text style={[styles.title, !item.read && { fontWeight: '800' }]}>{item.title}</Text>
              {item.body ? <Text style={styles.body}>{item.body}</Text> : null}
              <Text style={styles.time}>{formatDateTime(item.createdAt, timezone)}</Text>
            </Card>
          </Pressable>
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  title: { fontSize: font.large, fontWeight: '600', color: colors.text },
  body: { fontSize: font.body, color: colors.text, marginTop: 2 },
  time: { fontSize: font.small, color: colors.textMuted, marginTop: spacing.xs },
});
