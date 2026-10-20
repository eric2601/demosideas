import React from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { NavigationContainer, DefaultTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';
import { ConnectionBanner } from '../components/ConnectionBanner';
import { Button } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useConnection } from '../context/ConnectionContext';
import { useServer } from '../context/ServerContext';
import { CatalogScreen } from '../screens/CatalogScreen';
import { ChangePasswordScreen } from '../screens/ChangePasswordScreen';
import { HomeScreen } from '../screens/HomeScreen';
import { LoginScreen } from '../screens/LoginScreen';
import { NotificationsScreen } from '../screens/NotificationsScreen';
import { OrderDetailScreen } from '../screens/OrderDetailScreen';
import { OrdersScreen } from '../screens/OrdersScreen';
import { ReviewScreen } from '../screens/ReviewScreen';
import { colors, font, spacing } from '../theme';
import type { AppStackParams, AuthStackParams, TabParams } from './types';

const theme = { ...DefaultTheme, colors: { ...DefaultTheme.colors, background: colors.bg, primary: colors.primary } };

const AuthStack = createNativeStackNavigator<AuthStackParams>();
const AppStack = createNativeStackNavigator<AppStackParams>();
const Tab = createBottomTabNavigator<TabParams>();

const ICONS: Record<keyof TabParams, React.ComponentProps<typeof Ionicons>['name']> = {
  Inicio: 'home',
  Catalogo: 'add-circle',
  Pedidos: 'list',
  Avisos: 'notifications',
};

function Tabs() {
  const { unreadCount } = useConnection();
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerTitleAlign: 'center',
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarLabelStyle: { fontSize: font.small, fontWeight: '700' },
        tabBarStyle: { height: 64, paddingBottom: 8, paddingTop: 6 },
        tabBarIcon: ({ color, size }) => <Ionicons name={ICONS[route.name]} size={size + 2} color={color} />,
      })}
    >
      <Tab.Screen name="Inicio" component={HomeScreen} options={{ title: 'Inicio', headerShown: false }} />
      <Tab.Screen name="Catalogo" component={CatalogScreen} options={{ title: 'Crear pedido', tabBarLabel: 'Pedir' }} />
      <Tab.Screen name="Pedidos" component={OrdersScreen} options={{ title: 'Mis pedidos' }} />
      <Tab.Screen
        name="Avisos"
        component={NotificationsScreen}
        options={{ title: 'Avisos', tabBarBadge: unreadCount > 0 ? unreadCount : undefined }}
      />
    </Tab.Navigator>
  );
}

function AppNavigator() {
  return (
    <AppStack.Navigator screenOptions={{ headerTitleAlign: 'center' }}>
      <AppStack.Screen name="Tabs" component={Tabs} options={{ headerShown: false }} />
      <AppStack.Screen name="Resumen" component={ReviewScreen} options={{ title: 'Resumen del pedido' }} />
      <AppStack.Screen name="DetallePedido" component={OrderDetailScreen} options={{ title: 'Detalle del pedido' }} />
    </AppStack.Navigator>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return <View style={styles.center}>{children}</View>;
}

export function RootNavigator() {
  const { ready } = useServer();
  const { status, user, retryRestore, logout } = useAuth();

  if (!ready || status === 'loading') {
    return <Centered><ActivityIndicator size="large" color={colors.primary} /></Centered>;
  }

  if (status === 'unreachable') {
    return (
      <Centered>
        <Text style={styles.title}>No hay conexión con el servidor</Text>
        <Text style={styles.text}>Revisa que el teléfono esté en el Wi-Fi del restaurante y que el servidor esté encendido.</Text>
        <Button title="Reintentar" onPress={retryRestore} style={{ alignSelf: 'stretch', marginBottom: spacing.md }} />
        <Button title="Cambiar servidor o usuario" variant="ghost" onPress={logout} style={{ alignSelf: 'stretch' }} />
      </Centered>
    );
  }

  return (
    <View style={{ flex: 1 }}>
      {status === 'signedIn' ? <ConnectionBanner /> : null}
      <View style={{ flex: 1 }}>
        <NavigationContainer theme={theme}>
          {status === 'signedOut' ? (
            <AuthStack.Navigator screenOptions={{ headerShown: false }}>
              <AuthStack.Screen name="Login" component={LoginScreen} />
            </AuthStack.Navigator>
          ) : user?.mustChangePassword ? (
            <AuthStack.Navigator screenOptions={{ headerShown: false }}>
              <AuthStack.Screen name="Login" component={ChangePasswordScreen} />
            </AuthStack.Navigator>
          ) : (
            <AppNavigator />
          )}
        </NavigationContainer>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl, backgroundColor: colors.bg },
  title: { fontSize: font.title, fontWeight: '800', color: colors.text, textAlign: 'center', marginBottom: spacing.sm },
  text: { fontSize: font.body, color: colors.textMuted, textAlign: 'center', marginBottom: spacing.xl },
});
