# API REST y eventos en tiempo real

Base: `http://<ip-servidor>:3000/api` · JSON · camelCase · fechas ISO-8601 (UTC; se muestran en la zona horaria de `GET /api/config`).
Autenticación: `Authorization: Bearer <token>` (se obtiene en `POST /auth/login`).
Errores: `{ "error": { "code", "message", "details?" } }` con HTTP 400/401/403/404/409/423/429/500.
Listas paginadas: `?page=1&pageSize=25` → `{ data, total, page, pageSize }`.

## Públicos
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/health` | Estado del servidor y la BD |
| GET | `/config` | `companyName, timezone, showPrices, notificationSound, serverTime` |
| POST | `/auth/login` | `{username,password}` → `{token,user}`; `user = {id,username,fullName,role,roleName,mustChangePassword,restaurant|null,permissions[]}` |

## Sesión
`GET /auth/me` · `POST /auth/logout` · `POST /auth/change-password {currentPassword,newPassword}`

## Catálogo e inventario (almacén)
| Ruta | Permiso |
|---|---|
| `GET/POST /categories`, `PUT /categories/:id` | lectura: cualquiera · escritura `products.write` |
| `GET/POST /units`, `PUT /units/:id` | escritura `settings.write` |
| `GET /products?q&categoryId&active&stock=ok|low|out`, `GET /products/:id` | `products.read` |
| `POST /products`, `PUT /products/:id`, `POST /products/:id/activate|deactivate`, `DELETE /products/:id` (409 si tiene historial) | `products.write` |
| `GET /inventory?q&status`, `GET /inventory/:productId` | `inventory.read` — `onHand, reserved, available, minStock, lastCountDifference, lastMovementAt, stockStatus` |
| `POST /inventory/adjustments {productId,type,quantity,reason,notes?,direction?}` | `inventory.adjust` (`correccion_autorizada`: solo admin, requiere `direction in|out`) |
| `POST /inventory/counts {productId,countedQuantity,reason,notes?}` | `inventory.adjust` |
| `GET /movements?productId&type&from&to&q&page` | `movements.read` |
| `GET/POST /suppliers`, `PUT /suppliers/:id` | `suppliers.read/write` |
| `GET /receipts`, `GET /receipts/:id`, `POST /receipts {supplierId,invoiceRef?,notes?,items:[{productId,quantity,unitCost}]}` | `receipts.read/create` |

## Faltantes y compras
| Ruta | Descripción |
|---|---|
| `GET /shortages?status=open|<estado>&priority`, `GET /shortages/:id` | `shortages.read` |
| `POST /shortages {productId,quantityRequired,priority?,reason?,notes?}` | 201 nuevo / 200 si ya había uno abierto (no duplica) |
| `PATCH /shortages/:id {status?,priority?,notes?}` · `POST /shortages/refresh` | `shortages.write` |
| `GET /purchases/suggestions` | faltante − por recibir = `suggested` |
| `GET /purchases?status`, `GET /purchases/:id` | `purchases.read` |
| `POST /purchases {supplierId,items:[{productId,quantity,unitCost}],shortageIds?,expectedDate?,notes?}`, `PUT /purchases/:id` (borrador) | `purchases.write` |
| `POST /purchases/:id/submit` · `/authorize` (`purchases.authorize`) · `/cancel {reason}` | 204 |
| `POST /purchases/:id/receive {invoiceRef?,items:[{productId,quantity,unitCost}]}` | `receipts.create`; recepción parcial o completa |

## Pedidos
| Ruta | Quién |
|---|---|
| `POST /orders {items:[{productId,quantity}],notes?}` + cabecera `Idempotency-Key` | restaurante (201; 200 si la clave ya existía) |
| `GET /orders?status(uno o varios separados por coma)&q&restaurantId&page`, `GET /orders/:id` | almacén/entrega; el restaurante solo ve los suyos |
| `POST /orders/:id/review` (pasa a revisión) · `/accept {items?:[{itemId,authorizedQuantity}],note?}` · `/reserve-pending` · `/prepare {items?:[{itemId,preparedQuantity}],closePartial?,note?}` | `orders.manage` |
| `POST /orders/:id/dispatch {assignedTo?}` · `/deliver` | `orders.manage` o `deliveries.update` |
| `POST /orders/:id/confirm-reception` | restaurante dueño del pedido |
| `POST /orders/:id/cancel {reason}` | almacén (antes de despachar); restaurante solo en `nuevo`/`pendiente_revision` |
| `GET /deliveries`, `GET /delivery-staff` | `deliveries.read` |
| `GET /restaurant-catalog` | restaurante: catálogo activo y autorizado con `available`, `stockStatus` (`ok|low|out`) (y `price` si está habilitado) |

Estados: `nuevo → pendiente_revision → en_preparacion ⇄ parcialmente_surtido → listo_entrega → en_entrega → entregado` (y `cancelado` antes de `en_entrega`).
Partidas: `qtyRequested, qtyAuthorized, qtyReserved*, qtyPrepared, qtyDelivered, qtyPending` (*solo personal).

## Otros
`GET /dashboard` · `GET/POST /restaurants`, `PUT /restaurants/:id`, `GET/PUT /restaurants/:id/products` · `GET/POST /users`, `PUT /users/:id`, `POST /users/:id/reset-password`, `GET /roles` · `GET /notifications?unread=true`, `POST /notifications/:id/read`, `POST /notifications/read-all` · `GET/PUT /settings` · `GET /audit`.

## Tiempo real (Socket.IO)
Conectar a la raíz del servidor con `io(url, { auth: { token } })`. Los eventos se emiten **solo después del COMMIT**.
- Personal: sala `staff`. Restaurante: sala `restaurant:<id>`.
- `order:new` `{orderId,folio,restaurantId,restaurantName,itemCount}` (staff)
- `order:updated` `{orderId,folio,status,previousStatus}` (staff y restaurante dueño)
- `notification:new` `{id,type,title,body,payload,createdAt}` (staff o restaurante según corresponda)
- `inventory:changed` `{productId,onHand,reserved}` (staff)
Tipos de notificación: `pedido_nuevo, pedido_cancelado, pedido_cantidades, pedido_estado, pedido_listo, producto_agotado, stock_minimo, compra_recibida`.
El cliente debe reconectar solo y, al reconectar, recargar los datos por REST (los eventos no se reenvían).
