import React, { useRef, useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { StackActions } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { ApiError } from '../api/client';
import { api } from '../api/session';
import { QuantityStepper } from '../components/QuantityStepper';
import { Button, Card, Empty, Field, Notice } from '../components/ui';
import { useCart } from '../context/CartContext';
import { useToast } from '../context/ToastContext';
import { formatQuantity, isDecimalUnit } from '../lib/format';
import type { AppStackParams } from '../navigation/types';
import { colors, font, spacing } from '../theme';

type Props = NativeStackScreenProps<AppStackParams, 'Resumen'>;

export function ReviewScreen({ navigation }: Props) {
  const cart = useCart();
  const toast = useToast();
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<{ text: string; connection: boolean } | null>(null);
  const busy = useRef(false);

  const send = async () => {
    if (busy.current) return;
    busy.current = true;
    setSending(true);
    setError(null);
    try {
      // La clave se crea una vez por intento y se reutiliza en cada reintento hasta que haya éxito
      const key = cart.currentKey();
      const order = await api.createOrder(cart.buildBody(), key);
      cart.completeAttempt();
      cart.clear();
      toast.show(`Pedido ${order.folio} enviado`, 'success');
      navigation.dispatch(StackActions.replace('DetallePedido', { orderId: order.id, justCreated: true }));
    } catch (e) {
      if (e instanceof ApiError && e.isConnectionProblem) {
        setError({
          connection: true,
          text: 'No pudimos confirmar el envío por un problema de conexión. Tu pedido sigue aquí y no se perdió. Toca "Reintentar envío"; no se duplicará.',
        });
      } else {
        setError({ connection: false, text: e instanceof ApiError ? e.message : 'No se pudo enviar el pedido. Intenta de nuevo.' });
      }
    } finally {
      busy.current = false;
      setSending(false);
    }
  };

  const confirm = () => {
    Alert.alert(
      '¿Enviar el pedido?',
      `Se enviarán ${cart.count} producto(s) al almacén.`,
      [
        { text: 'Revisar otra vez', style: 'cancel' },
        { text: 'Sí, enviar', onPress: send },
      ],
    );
  };

  if (cart.lines.length === 0) {
    return (
      <SafeAreaView style={styles.safe}>
        <Empty title="Tu pedido está vacío" hint="Agrega productos desde el catálogo." />
        <View style={{ padding: spacing.lg }}><Button title="Ir al catálogo" onPress={() => navigation.goBack()} /></View>
      </SafeAreaView>
    );
  }

  const retrying = !!error?.connection || cart.hasPendingAttempt();

  return (
    <SafeAreaView style={styles.safe} edges={['left', 'right', 'bottom']}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
          <Text style={styles.heading}>Revisa tu pedido</Text>
          {cart.lines.map((l) => (
            <Card key={l.productId}>
              <Text style={styles.name}>{l.name}</Text>
              <Text style={styles.meta}>Código {l.sku} · {formatQuantity(l.quantity)} {l.unitName}</Text>
              <View style={{ marginTop: spacing.sm }}>
                <QuantityStepper
                  value={l.quantity}
                  allowDecimals={isDecimalUnit(l.unitName)}
                  onChange={(q) => cart.setQuantity({ id: l.productId, sku: l.sku, name: l.name, unitName: l.unitName }, q)}
                />
              </View>
              <Button title="Quitar" variant="ghost" small style={{ marginTop: spacing.sm }} onPress={() => cart.remove(l.productId)} />
            </Card>
          ))}

          <Field
            label="Observaciones (opcional)"
            value={cart.notes}
            onChangeText={cart.setNotes}
            placeholder="Ej. Entregar por la puerta trasera"
            multiline
            maxLength={500}
          />

          {error ? <Notice kind={error.connection ? 'warning' : 'error'}>{error.text}</Notice> : null}

          <Button
            title={sending ? 'Enviando...' : retrying && error ? 'Reintentar envío' : 'Enviar pedido'}
            onPress={confirm}
            disabled={sending}
            loading={sending}
          />
          <Text style={styles.foot}>
            Si pides más de lo que hay disponible, el almacén te surtirá lo que tenga y te avisará lo pendiente.
          </Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  scroll: { padding: spacing.lg, paddingBottom: spacing.xxl },
  heading: { fontSize: font.title, fontWeight: '800', color: colors.text, marginBottom: spacing.md },
  name: { fontSize: font.large, fontWeight: '700', color: colors.text },
  meta: { fontSize: font.body, color: colors.textMuted },
  foot: { marginTop: spacing.md, color: colors.textMuted, fontSize: font.small + 1, textAlign: 'center' },
});
