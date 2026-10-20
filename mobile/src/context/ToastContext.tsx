import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, font, radius, spacing } from '../theme';

type Kind = 'info' | 'success' | 'error';
interface ToastItem { id: number; message: string; kind: Kind }

interface ToastApi {
  show: (message: string, kind?: Kind) => void;
}

const Ctx = createContext<ToastApi>({ show: () => {} });

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toast, setToast] = useState<ToastItem | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const counter = useRef(0);
  const insets = useSafeAreaInsets();

  const show = useCallback((message: string, kind: Kind = 'info') => {
    counter.current += 1;
    setToast({ id: counter.current, message, kind });
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setToast(null), 5000);
  }, []);

  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); }, []);

  const value = useMemo(() => ({ show }), [show]);
  const bg = toast?.kind === 'error' ? colors.danger : toast?.kind === 'success' ? colors.success : colors.primaryDark;

  return (
    <Ctx.Provider value={value}>
      {children}
      {toast && (
        <View pointerEvents="box-none" style={[styles.wrap, { top: insets.top + spacing.sm }]}>
          <Pressable
            accessibilityRole="alert"
            onPress={() => setToast(null)}
            style={[styles.toast, { backgroundColor: bg }]}
          >
            <Text style={styles.text}>{toast.message}</Text>
          </Pressable>
        </View>
      )}
    </Ctx.Provider>
  );
}

export const useToast = () => useContext(Ctx);

const styles = StyleSheet.create({
  wrap: { position: 'absolute', left: spacing.lg, right: spacing.lg, zIndex: 100 },
  toast: { borderRadius: radius.md, paddingVertical: spacing.md, paddingHorizontal: spacing.lg, elevation: 6 },
  text: { color: colors.white, fontSize: font.body, fontWeight: '600' },
});
