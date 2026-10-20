import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AuthProvider } from './src/context/AuthContext';
import { CartProvider } from './src/context/CartContext';
import { ConnectionProvider } from './src/context/ConnectionContext';
import { ServerProvider } from './src/context/ServerContext';
import { ToastProvider } from './src/context/ToastContext';
import { RootNavigator } from './src/navigation';

export default function App() {
  return (
    <SafeAreaProvider>
      <StatusBar style="dark" translucent={false} backgroundColor="#FFFFFF" />
      <ServerProvider>
        <ToastProvider>
          <AuthProvider>
            <ConnectionProvider>
              <CartProvider>
                <RootNavigator />
              </CartProvider>
            </ConnectionProvider>
          </AuthProvider>
        </ToastProvider>
      </ServerProvider>
    </SafeAreaProvider>
  );
}
