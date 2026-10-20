# Panel web del almacén

React 18 + TypeScript + Vite + Ant Design 5. Funciona sin Internet (sin CDN).

## Desarrollo
1. Levanta el backend: `cd ../server && npm run dev` (puerto 3000).
2. `npm install && npm run dev` -> http://localhost:5173 (el proxy envía `/api` y `/socket.io` a localhost:3000).

## Producción
`npm run build` genera `dist/`, que el servidor sirve automáticamente (reinicia el servidor si `dist/` no existía al arrancar).

Scripts: `dev`, `build` (typecheck + vite build), `preview`, `typecheck`.

## Estructura
`src/api` cliente HTTP y hooks · `src/auth` sesión y permisos · `src/realtime` Socket.IO, notificaciones y sonido · `src/pages` un archivo por módulo · `src/types.ts` tipos de la API.

Los usuarios con rol `restaurante` no pueden usar el panel (usan la app Android).
