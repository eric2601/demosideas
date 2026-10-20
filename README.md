# Sistema de control de almacén, inventarios y pedidos para restaurantes

Sistema cliente-servidor para una red local (sin Internet):

| Carpeta | Contenido |
|---|---|
| `server/` | API REST + Socket.IO (Node.js, Express, TypeScript) y migraciones PostgreSQL |
| `web/` | Panel web del almacén (React, Vite, TypeScript, Ant Design) |
| `mobile/` | App Android para restaurantes (React Native + Expo, genera APK) |
| `docs/` | API, modelo de datos y reglas de negocio, instalación en red local |

## Inicio rápido (desarrollo)
```bash
# 1. PostgreSQL con usuario/BD "almacen" (o: docker compose up -d db)
cd server && cp .env.example .env && npm install
npm run seed:demo        # solo pruebas: datos ficticios, usuarios *.demo / Demo-Temporal-2026
npm run dev              # API en http://localhost:3000
cd ../web && npm install && npm run dev
```
Producción, firewall, IP fija, respaldos y límites de HTTP: [`docs/INSTALACION_RED_LOCAL.md`](docs/INSTALACION_RED_LOCAL.md).
Contrato de la API y eventos: [`docs/API.md`](docs/API.md) · Esquema y reglas: [`docs/MODELO_DE_DATOS.md`](docs/MODELO_DE_DATOS.md).

## Usuarios de demostración (contraseña temporal `Demo-Temporal-2026`)
`admin.demo`, `almacen.demo`, `compras.demo`, `entrega.demo`, `rest1.demo`, `rest2.demo`

## Pruebas
`cd server && npm test` — 18 pruebas de integración contra PostgreSQL (concurrencia de reservas, idempotencia, ciclo de pedido, compras parciales, faltantes, permisos, inmutabilidad del historial).
