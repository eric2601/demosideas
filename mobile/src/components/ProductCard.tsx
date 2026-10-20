import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { formatMoney, formatQuantity, isDecimalUnit } from '../lib/format';
import { colors, font, spacing } from '../theme';
import type { CatalogProduct } from '../types';
import { QuantityStepper } from './QuantityStepper';
import { Button, Card } from './ui';

const STOCK = {
  ok: { label: 'Disponible', color: colors.success, bg: colors.successSoft },
  low: { label: 'Pocas existencias', color: colors.warning, bg: colors.warningSoft },
  out: { label: 'Agotado', color: colors.danger, bg: colors.dangerSoft },
} as const;

export function stockLabel(p: Pick<CatalogProduct, 'stockStatus'>) {
  return STOCK[p.stockStatus] ?? STOCK.ok;
}

interface Props {
  product: CatalogProduct;
  quantity: number;
  onChange: (qty: number) => void;
}

function ProductCardBase({ product, quantity, onChange }: Props) {
  const s = stockLabel(product);
  const over = quantity > product.available;
  return (
    <Card>
      <View style={styles.head}>
        <View style={{ flex: 1 }}>
          <Text style={styles.name}>{product.name}</Text>
          <Text style={styles.meta}>Código {product.sku} · {product.categoryName} · {product.unitName}</Text>
        </View>
      </View>
      <View style={styles.stockRow}>
        <View style={[styles.pill, { backgroundColor: s.bg }]}>
          <Text style={[styles.pillText, { color: s.color }]}>{s.label}</Text>
        </View>
        <Text style={styles.avail}>
          {product.stockStatus === 'out' ? 'Sin existencias ahora' : `Hay ${formatQuantity(product.available)} ${product.unitName}`}
        </Text>
        {product.price !== undefined && product.price !== null ? (
          <Text style={styles.price}>{formatMoney(product.price)}</Text>
        ) : null}
      </View>
      {quantity > 0 ? (
        <>
          <QuantityStepper value={quantity} onChange={onChange} allowDecimals={isDecimalUnit(product.unitName)} />
          {over ? (
            <Text style={styles.warn}>
              Pediste más de lo disponible ahora. No hay problema: el almacén te surtirá lo que tenga y te avisará lo que quede pendiente.
            </Text>
          ) : null}
        </>
      ) : (
        <Button title="Agregar al pedido" variant="secondary" onPress={() => onChange(1)} />
      )}
    </Card>
  );
}

export const ProductCard = React.memo(ProductCardBase);

const styles = StyleSheet.create({
  head: { flexDirection: 'row', marginBottom: spacing.sm },
  name: { fontSize: font.large, fontWeight: '700', color: colors.text },
  meta: { fontSize: font.small, color: colors.textMuted, marginTop: 2 },
  stockRow: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: spacing.sm, marginBottom: spacing.md },
  pill: { borderRadius: 999, paddingHorizontal: spacing.md, paddingVertical: 3 },
  pillText: { fontSize: font.small, fontWeight: '700' },
  avail: { fontSize: font.body, color: colors.text },
  price: { fontSize: font.body, fontWeight: '700', color: colors.primaryDark, marginLeft: 'auto' },
  warn: { marginTop: spacing.sm, color: colors.warning, fontSize: font.small + 1 },
});
