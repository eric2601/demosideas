import React, { useState } from 'react';
import { Alert, KeyboardAvoidingView, Modal, Platform, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { api } from '../api/session';
import { Button, Card, Field, Loading, Notice, StatusBadge } from '../components/ui';
import { useServer } from '../context/ServerContext';
import { useToast } from '../context/ToastContext';
import { formatDateTime, formatQuantity, formatQuantityWithUnit } from '../lib/format';
import { canCancel, canConfirmReception, statusInfo } from '../lib/status';
import { errorText, useLoader } from '../lib/useLoader';
import type { AppStackParams } from '../navigation/types';
import { colors, font, radius, spacing } from '../theme';
import type { OrderDetail, OrderItem } from '../types';

type Props = NativeStackScreenProps<AppStackParams, 'DetallePedido'>;

export function OrderDetailScreen({ route }: Props) {
  const { orderId, justCreated } = route.params;
  const { timezone } = useServer();
  const toast = useToast();
  const { data: order, loading, error, refreshing, pull, setData } = useLoader(() => api.getOrder(orderId), [orderId]);
  const [acting, setActing] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState('');

  if (loading && !order) return <Loading text="Cargando pedido..." />;
  if (!order) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={{ padding: spacing.lg }}>
          <Notice kind="error">{error ?? 'No se encontró el pedido.'}</Notice>
          <Button title="Reintentar" onPress={pull} />
        </View>
      </SafeAreaView>
    );
  }

  const run = async (fn: () => Promise<OrderDetail>, okMsg: string) => {
    if (acting) return;
    setActing(true);
    setActionError(null);
    try {
      setData(await fn());
      toast.show(okMsg, 'success');
      return true;
    } catch (e) {
      setActionError(errorText(e));
      return false;
    } finally {
      setActing(false);
    }
  };

  const confirmReception = () => {
    Alert.alert(
      '¿Confirmar recepción?',
      `Confirmas que recibiste el pedido ${order.folio}. Se guardará tu nombre, la fecha y la hora.`,
      [
        { text: 'Todavía no', style: 'cancel' },
        { text: 'Sí, lo recibí', onPress: () => run(() => api.confirmReception(order.id), 'Recepción confirmada') },
      ],
    );
  };

  const submitCancel = async () => {
    if (reason.trim().length < 3) { setActionError('Escribe el motivo de la cancelación (mínimo 3 letras).'); return; }
    const ok = await run(() => api.cancelOrder(order.id, reason.trim()), 'Pedido cancelado');
    if (ok) { setCancelOpen(false); setReason(''); }
  };

  const askCancel = () => {
    setActionError(null);
    Alert.alert(
      '¿Cancelar este pedido?',
      'El almacén dejará de prepararlo. Esta acción no se puede deshacer.',
      [
        { text: 'No, mantenerlo', style: 'cancel' },
        { text: 'Sí, cancelar', style: 'destructive', onPress: () => setCancelOpen(true) },
      ],
    );
  };

  const hasPartial = order.items.some((i) => i.qtyDelivered > 0 && i.qtyPending > 0);
  const preparedShort = ['parcialmente_surtido', 'listo_entrega', 'en_entrega'].includes(order.status);

  return (
    <SafeAreaView style={styles.safe} edges={['left', 'right', 'bottom']}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={pull} />}
      >
        {justCreated ? <Notice kind="success">{`Tu pedido se envió correctamente. Folio: ${order.folio}`}</Notice> : null}
        {error ? <Notice kind="warning">{`No se pudo actualizar: ${error}`}</Notice> : null}

        <Card>
          <View style={styles.row}>
            <Text style={styles.folio}>{order.folio}</Text>
            <StatusBadge status={order.status} />
          </View>
          <Text style={styles.meta}>Creado: {formatDateTime(order.createdAt, timezone)}</Text>
          <Text style={styles.meta}>Pidió: {order.requestedByName}</Text>
          {order.deliveredAt ? <Text style={styles.meta}>Entregado: {formatDateTime(order.deliveredAt, timezone)}</Text> : null}
          {order.receivedAt ? (
            <Text style={[styles.meta, { color: colors.success, fontWeight: '700' }]}>
              Recepción confirmada: {formatDateTime(order.receivedAt, timezone)}{order.receivedByName ? ` por ${order.receivedByName}` : ''}
            </Text>
          ) : null}
          {order.notes ? <Text style={styles.notes}>Observaciones: {order.notes}</Text> : null}
          {order.status === 'cancelado' && order.cancelReason ? (
            <Text style={[styles.notes, { color: colors.danger }]}>Motivo de cancelación: {order.cancelReason}</Text>
          ) : null}
        </Card>

        {hasPartial ? (
          <Notice kind="warning">
            Este pedido se entregó de forma parcial. Abajo ves lo que ya se entregó y lo que sigue pendiente; el almacén te lo surtirá cuando tenga existencias.
          </Notice>
        ) : null}

        <Text style={styles.section}>Productos</Text>
        {order.items.map((it) => <ItemCard key={it.id} item={it} showPrepared={preparedShort} />)}

        {actionError ? <Notice kind="error">{actionError}</Notice> : null}
        {canConfirmReception(order.status, order.receivedAt) ? (
          <Button title="Confirmar recepción" onPress={confirmReception} loading={acting} disabled={acting} style={{ marginBottom: spacing.md }} />
        ) : null}
        {canCancel(order.status) ? (
          <Button title="Cancelar pedido" variant="danger" onPress={askCancel} disabled={acting} style={{ marginBottom: spacing.md }} />
        ) : null}

        <Text style={styles.section}>Historial</Text>
        <Card>
          {order.events.length === 0 ? <Text style={styles.meta}>Sin movimientos.</Text> : null}
          {order.events.map((e, idx) => (
            <View key={e.id} style={[styles.event, idx > 0 && styles.eventBorder]}>
              <Text style={styles.eventTitle}>{eventTitle(e.event, e.toStatus)}</Text>
              <Text style={styles.metaSmall}>
                {formatDateTime(e.createdAt, timezone)}{e.userName ? ` · ${e.userName}` : ''}
              </Text>
              {e.note ? <Text style={styles.metaSmall}>{e.note}</Text> : null}
            </View>
          ))}
        </Card>
      </ScrollView>

      <Modal visible={cancelOpen} transparent animationType="fade" onRequestClose={() => setCancelOpen(false)}>
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.modalWrap}>
          <View style={styles.modal}>
            <Text style={styles.folio}>Cancelar {order.folio}</Text>
            <Field label="Motivo de la cancelación" value={reason} onChangeText={setReason} multiline maxLength={300} placeholder="Ej. Me equivoqué de productos" />
            {actionError ? <Notice kind="error">{actionError}</Notice> : null}
            <Button title="Cancelar pedido" variant="danger" onPress={submitCancel} loading={acting} disabled={acting} />
            <Button title="Volver" variant="ghost" onPress={() => { setCancelOpen(false); setActionError(null); }} style={{ marginTop: spacing.sm }} />
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </SafeAreaView>
  );
}

function eventTitle(event: string, to: string | null): string {
  if (event === 'creado') return 'Pedido creado';
  if (to) return statusInfo(to).label;
  return event.replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase());
}

function ItemCard({ item, showPrepared }: { item: OrderItem; showPrepared: boolean }) {
  const authorized = item.qtyAuthorized ?? item.qtyRequested;
  const reduced = item.qtyAuthorized !== null && item.qtyAuthorized < item.qtyRequested;
  const partial = item.qtyDelivered > 0 && item.qtyPending > 0;
  const u = item.unitName;
  return (
    <Card style={partial ? { borderColor: colors.warning } : undefined}>
      <Text style={styles.itemName}>{item.productName}</Text>
      <Text style={styles.metaSmall}>Código {item.sku}</Text>
      <View style={styles.qtyGrid}>
        <Qty label="Solicitado" value={formatQuantityWithUnit(item.qtyRequested, u)} />
        <Qty label="Autorizado" value={item.qtyAuthorized === null ? 'Por revisar' : formatQuantityWithUnit(authorized, u)} warn={reduced} />
        {showPrepared ? <Qty label="Preparado" value={formatQuantityWithUnit(item.qtyPrepared, u)} /> : null}
        <Qty label="Entregado" value={formatQuantityWithUnit(item.qtyDelivered, u)} good={item.qtyDelivered > 0} />
        <Qty label="Pendiente" value={formatQuantityWithUnit(item.qtyPending, u)} warn={partial} />
      </View>
      {reduced ? <Text style={styles.warnText}>El almacén autorizó menos de lo que pediste.</Text> : null}
      {partial ? (
        <Text style={styles.warnText}>
          Se entregó {formatQuantity(item.qtyDelivered)} {u}; quedan pendientes {formatQuantity(item.qtyPending)} {u}.
        </Text>
      ) : null}
    </Card>
  );
}

function Qty({ label, value, warn, good }: { label: string; value: string; warn?: boolean; good?: boolean }) {
  return (
    <View style={styles.qty}>
      <Text style={styles.qtyLabel}>{label}</Text>
      <Text style={[styles.qtyValue, warn && { color: colors.warning }, good && { color: colors.success }]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  scroll: { padding: spacing.lg, paddingBottom: spacing.xxl },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm },
  folio: { fontSize: font.title, fontWeight: '800', color: colors.text },
  meta: { fontSize: font.body, color: colors.textMuted, marginTop: 2 },
  metaSmall: { fontSize: font.small + 1, color: colors.textMuted },
  notes: { fontSize: font.body, color: colors.text, marginTop: spacing.sm },
  section: { fontSize: font.large, fontWeight: '700', color: colors.text, marginVertical: spacing.md },
  itemName: { fontSize: font.large, fontWeight: '700', color: colors.text },
  qtyGrid: { flexDirection: 'row', flexWrap: 'wrap', marginTop: spacing.sm, gap: spacing.sm },
  qty: { minWidth: '30%', flexGrow: 1 },
  qtyLabel: { fontSize: font.small, color: colors.textMuted },
  qtyValue: { fontSize: font.body, fontWeight: '700', color: colors.text },
  warnText: { marginTop: spacing.sm, color: colors.warning, fontSize: font.body },
  event: { paddingVertical: spacing.sm },
  eventBorder: { borderTopWidth: 1, borderTopColor: colors.border },
  eventTitle: { fontSize: font.body, fontWeight: '700', color: colors.text },
  modalWrap: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: spacing.lg },
  modal: { backgroundColor: colors.white, borderRadius: radius.lg, padding: spacing.lg },
});
