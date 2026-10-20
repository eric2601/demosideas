# Pedidos Almacén (app móvil Android para restaurantes)

App React Native (Expo SDK 53 + TypeScript) para que los restaurantes hagan y den seguimiento a sus pedidos al almacén.
Funciona **en red local, sin Internet**: se conecta por HTTP y Socket.IO al servidor del almacén (`/server`). No usa Firebase ni servicios externos.

## Qué hace

- Login solo para usuarios con rol `restaurante` (otros roles se rechazan con un mensaje claro). Sesión guardada de forma segura (SecureStore) y restaurada al abrir; si el servidor responde 401 se vuelve al login. Cambio obligatorio de contraseña temporal.
- Dirección del servidor configurable en la pantalla de login (con botón **Probar conexión**). No hay ninguna URL fija en el código.
- Pantalla principal con contadores (pendientes / en preparación / listos), botón grande **Crear pedido**, historial e indicador de conexión; banner rojo si no hay conexión con el servidor.
- Catálogo con disponibilidad **confirmada por el servidor**, búsqueda, filtro por categoría y cantidades con decimales (hasta 3) para kg/lt. Se puede pedir más de lo disponible (aviso amable, el almacén gestiona el faltante).
- Resumen del pedido, observaciones, confirmación explícita y envío con `Idempotency-Key`: la clave se genera una vez por intento y se reutiliza en cada reintento (también si la app se cierra), así un corte de red no duplica pedidos. El carrito no se pierde si falla.
- Seguimiento: lista con filtros y paginación, detalle con solicitado / autorizado / preparado / entregado / pendiente, historial de eventos en la zona horaria del servidor, cancelar (solo `nuevo` o `pendiente_revision`) y **Confirmar recepción**.
- Tiempo real con Socket.IO (reconexión automática; al reconectar se recarga todo por REST), avisos dentro de la app y pantalla de notificaciones.

## Estructura

```
App.tsx, index.ts
src/api/        cliente HTTP tipado (timeout 15 s, AbortController), endpoints, sesión
src/context/    Server, Auth, Connection (socket + health), Cart, Toast
src/screens/    Login, CambiarClave, Inicio, Catálogo, Resumen, Pedidos, Detalle, Avisos
src/components/ botones, tarjetas, selector de cantidad, banner de conexión
src/lib/        lógica pura: idempotencia, formato, estados/contadores, URL del servidor
src/theme, src/types, src/navigation
scripts/smoke.ts  prueba de humo contra el backend real
__tests__/        pruebas unitarias (jest-expo)
```

## Desarrollo

Requisitos: Node 20+ y npm.

```bash
cd mobile
npm install
npm run typecheck        # tsc --noEmit
npm test                 # pruebas unitarias
npx expo-doctor
npx expo export --platform android   # comprueba que el bundle compila
npm start                # Expo dev server (usa Expo Go o un development build)
```

Prueba de humo contra el backend (con el servidor corriendo en el puerto 3000 y la BD de demostración sembrada):

```bash
SERVER_URL=http://localhost:3000 npm run smoke
# FULL=1 recorre además aceptar -> preparar -> despachar -> entregar -> confirmar recepción (consume 1 unidad de inventario de la BD de desarrollo)
```

## Generar el APK

Configuración ya incluida: `app.json` (nombre "Pedidos Almacén", paquete `com.almacen.restaurantes`, orientación vertical) y el plugin `expo-build-properties` con `android.usesCleartextTraffic = true` (necesario para HTTP en red local). `eas.json` define los perfiles `development`, `preview` (APK, distribución interna) y `production` (AAB).

### Opción A: EAS Build (en la nube de Expo; requiere Internet y cuenta Expo, solo para compilar)

```bash
npm install -g eas-cli      # o: npx eas-cli ...
eas login
eas build -p android --profile preview
```

Al terminar, EAS muestra un enlace/QR para descargar el `.apk`.

### Opción B: compilación local, sin nube

Requisitos: **JDK 17** y **Android SDK** (platform-tools, build-tools y plataforma 35; variable `ANDROID_HOME` definida; acepta licencias con `sdkmanager --licenses`).

```bash
cd mobile
npm install
npx expo prebuild --platform android      # genera la carpeta android/
cd android
./gradlew assembleRelease
```

El APK queda en:

```
mobile/android/app/build/outputs/apk/release/app-release.apk
```

Nota: sin configuración extra, la compilación `release` que genera `expo prebuild` se firma con la llave de depuración incluida en la plantilla, suficiente para instalar en los teléfonos del restaurante. Para publicar en Google Play crea tu propia llave y perfil `production`.

### Instalar el APK

- Con cable: activa "Depuración USB" en el teléfono y ejecuta `adb install -r app-release.apk`.
- Sin cable: copia el `.apk` al teléfono (USB, WhatsApp, carpeta compartida...), ábrelo desde "Archivos" y permite "Instalar apps de orígenes desconocidos" para esa aplicación cuando Android lo pida.

## Apuntar a la IP del servidor

1. En la PC del almacén averigua su IP local (`ip addr` en Linux, `ipconfig` en Windows), por ejemplo `192.168.1.100`. Conviene reservarle una IP fija en el router.
2. Verifica que el servidor escuche en toda la red (`HOST=0.0.0.0`, puerto 3000) y que el firewall permita el puerto 3000.
3. Conecta el teléfono al mismo Wi-Fi.
4. Abre la app, escribe `http://192.168.1.100:3000` en **Dirección del servidor** y pulsa **Probar conexión**. Se guarda en el teléfono; puede cambiarse cerrando sesión.

## Limitaciones de HTTP en la red local

- La app usa HTTP sin cifrar (por eso `usesCleartextTraffic`). Es aceptable dentro de una LAN privada y confiable, pero el usuario, la contraseña y los datos viajan sin cifrar: no uses la app en Wi-Fi públicos ni expongas el puerto a Internet.
- Si algún día el servidor se publica detrás de HTTPS, basta con escribir `https://...` en la dirección del servidor.
- Sin conexión no se pueden enviar pedidos: el carrito se conserva y se reintenta con la misma clave de idempotencia cuando vuelva la red.
- No hay notificaciones push con la app cerrada (no se usa Firebase); los avisos llegan mientras la app está abierta y al abrirla se recargan los datos.
- Firma y foto de recepción no están implementadas.
