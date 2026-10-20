import React, { useCallback, useMemo, useState } from 'react';
import { FlatList, Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import { CompositeScreenProps } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { api } from '../api/session';
import { ProductCard } from '../components/ProductCard';
import { Chip, Empty, Loading, Notice } from '../components/ui';
import { useCart } from '../context/CartContext';
import { useLoader } from '../lib/useLoader';
import type { AppStackParams, TabParams } from '../navigation/types';
import { colors, font, radius, spacing, TOUCH } from '../theme';
import type { CatalogProduct } from '../types';

type Props = CompositeScreenProps<BottomTabScreenProps<TabParams, 'Catalogo'>, NativeStackScreenProps<AppStackParams>>;

const norm = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

export function CatalogScreen({ navigation }: Props) {
  const cart = useCart();
  const { data, loading, error, refreshing, pull } = useLoader(() => api.catalog());
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<number | null>(null);

  const categories = useMemo(() => {
    const m = new Map<number, string>();
    (data ?? []).forEach((p) => m.set(p.categoryId, p.categoryName));
    return [...m.entries()].map(([id, name]) => ({ id, name }));
  }, [data]);

  const filtered = useMemo(() => {
    const q = norm(query.trim());
    return (data ?? []).filter((p) =>
      (category === null || p.categoryId === category) &&
      (!q || norm(p.name).includes(q) || norm(p.sku).includes(q)));
  }, [data, query, category]);

  const render = useCallback(({ item }: { item: CatalogProduct }) => (
    <ProductCard product={item} quantity={cart.quantityOf(item.id)} onChange={(q) => cart.setQuantity(item, q)} />
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ), [cart.lines, cart.setQuantity]);

  return (
    <SafeAreaView style={styles.safe} edges={['left', 'right']}>
      <View style={styles.top}>
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Buscar por nombre o código"
          placeholderTextColor={colors.textMuted}
          style={styles.search}
          autoCorrect={false}
          clearButtonMode="while-editing"
        />
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginTop: spacing.sm }}>
          <Chip label="Todas" selected={category === null} onPress={() => setCategory(null)} />
          {categories.map((c) => (
            <Chip key={c.id} label={c.name} selected={category === c.id} onPress={() => setCategory(c.id)} />
          ))}
        </ScrollView>
      </View>

      {loading && !data ? <Loading text="Cargando productos..." /> : (
        <FlatList
          data={filtered}
          keyExtractor={(p) => String(p.id)}
          renderItem={render}
          extraData={cart.lines}
          contentContainerStyle={{ padding: spacing.lg, paddingBottom: 120 }}
          keyboardShouldPersistTaps="handled"
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={pull} />}
          ListHeaderComponent={error ? <Notice kind="error">{error}</Notice> : null}
          ListEmptyComponent={<Empty title="No hay productos" hint={query || category ? 'Prueba con otra búsqueda o categoría.' : 'Tu restaurante aún no tiene productos habilitados.'} />}
        />
      )}

      {cart.count > 0 ? (
        <View style={styles.cartBar}>
          <Pressable accessibilityRole="button" onPress={() => navigation.navigate('Resumen')} style={styles.cartBtn}>
            <Text style={styles.cartText}>Ver mi pedido ({cart.count} {cart.count === 1 ? 'producto' : 'productos'})</Text>
          </Pressable>
        </View>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  top: { backgroundColor: colors.white, padding: spacing.md, borderBottomWidth: 1, borderBottomColor: colors.border },
  search: {
    height: 52, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, paddingHorizontal: spacing.md,
    fontSize: font.large, color: colors.text, backgroundColor: colors.bg,
  },
  cartBar: { position: 'absolute', left: 0, right: 0, bottom: 0, padding: spacing.md, backgroundColor: 'rgba(244,246,249,0.95)' },
  cartBtn: { minHeight: TOUCH, borderRadius: radius.md, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center', elevation: 4 },
  cartText: { color: colors.white, fontSize: font.large, fontWeight: '700' },
});
