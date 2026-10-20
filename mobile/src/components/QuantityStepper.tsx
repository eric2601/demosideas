import React, { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { clampQuantity, formatQuantity, parseQuantityInput, sanitizeQuantityText } from '../lib/format';
import { colors, font, radius, spacing } from '../theme';

interface Props {
  value: number;
  onChange: (n: number) => void;
  allowDecimals: boolean;
  /** Mínimo permitido al bajar con "−" (0 permite quitar el producto) */
  min?: number;
}

/** Selector de cantidad con botones grandes y entrada numérica (hasta 3 decimales). */
export function QuantityStepper({ value, onChange, allowDecimals, min = 0 }: Props) {
  const [text, setText] = useState(formatQuantityPlain(value));
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    if (!focused) setText(formatQuantityPlain(value));
  }, [value, focused]);

  const step = 1;
  const dec = () => onChange(Math.max(min, clampQuantity(value - step)));
  const inc = () => onChange(clampQuantity(value + step));

  const commit = () => {
    setFocused(false);
    const n = parseQuantityInput(text, allowDecimals);
    if (n === null) { setText(formatQuantityPlain(value)); return; }
    const final = clampQuantity(n);
    onChange(final < min ? min : final);
  };

  return (
    <View style={styles.row}>
      <Pressable accessibilityRole="button" accessibilityLabel="Quitar uno" onPress={dec} style={styles.btn}>
        <Text style={styles.btnText}>−</Text>
      </Pressable>
      <TextInput
        accessibilityLabel="Cantidad"
        value={text}
        onFocus={() => setFocused(true)}
        onChangeText={(t) => setText(sanitizeQuantityText(t, allowDecimals))}
        onBlur={commit}
        onEndEditing={commit}
        keyboardType={allowDecimals ? 'decimal-pad' : 'number-pad'}
        selectTextOnFocus
        style={styles.input}
      />
      <Pressable accessibilityRole="button" accessibilityLabel="Agregar uno" onPress={inc} style={[styles.btn, styles.btnPlus]}>
        <Text style={[styles.btnText, { color: colors.white }]}>+</Text>
      </Pressable>
    </View>
  );
}

// En el campo no se agrupan miles para poder editar con comodidad
const formatQuantityPlain = (n: number) => formatQuantity(n).replace(/,/g, '');

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  btn: {
    width: 56, height: 56, borderRadius: radius.md, backgroundColor: colors.primarySoft,
    alignItems: 'center', justifyContent: 'center',
  },
  btnPlus: { backgroundColor: colors.primary },
  btnText: { fontSize: 32, lineHeight: 36, fontWeight: '700', color: colors.primary },
  input: {
    flex: 1, minWidth: 70, height: 56, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md,
    textAlign: 'center', fontSize: font.title, fontWeight: '700', color: colors.text, backgroundColor: colors.white,
  },
});
