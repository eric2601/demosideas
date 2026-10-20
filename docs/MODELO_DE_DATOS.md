# Modelo de datos y reglas de negocio

Esquema: `server/migrations/001_init.sql` (PostgreSQL 16). Claves foráneas, `CHECK` e índices únicos hacen cumplir las reglas aunque falle la aplicación.

## Entidades
| Tabla | Propósito |
|---|---|
| `roles`, `users` | Autenticación y autorización. `users.restaurant_id` solo para rol `restaurante`. Contraseñas con bcrypt (`password_hash`). |
| `restaurants`, `restaurant_product_access` | Clientes del almacén y catálogo restringido opcional (sin filas = todo el catálogo activo). |
| `categories`, `units`, `products` | Catálogo. Los productos nunca se borran si tienen historial (FK `RESTRICT`); se desactivan. |
| `inventory` | Saldo 1:1 por producto: `on_hand` (físico), `reserved`. `CHECK (reserved <= on_hand)`. Disponible = `on_hand − reserved`. |
| `inventory_movements` | Libro de movimientos **solo inserción** (trigger impide UPDATE/DELETE). Guarda existencia/reserva anterior y posterior, documento origen (`reference_*`), usuario y motivo. |
| `suppliers`, `goods_receipts(+_items)` | Entradas de mercancía (inmutables). Pueden estar ligadas a una orden de compra. |
| `purchase_orders(+_items)`, `purchase_order_shortages` | Compras: `qty_ordered`, `qty_received`, `original_shortage` (faltante original para comparar). |
| `shortages` | Faltantes. Índice único parcial: **una sola incidencia abierta por producto**. |
| `orders`, `order_items`, `order_events`, `deliveries` | Pedidos. `order_items` conserva SKU, nombre, unidad y precio del momento. `order_events` (inmutable) es el historial de cambios. |
| `notifications`, `notification_reads` | Notificaciones persistentes (personal o restaurante) y lectura por usuario. |
| `audit_logs` | Bitácora inmutable de operaciones sensibles (login, ajustes, usuarios, autorizaciones…). |
| `settings` | Zona horaria, nombre, mostrar precios, sonido. |

## Reglas clave
- **Existencias**: `disponible = física − reservada`. Toda escritura de saldo pasa por `applyMovement` (`server/src/services/inventory.ts`), que bloquea la fila (`SELECT … FOR UPDATE`, en orden de producto para evitar interbloqueos), valida invariantes, actualiza y registra el movimiento en la misma transacción.
- **Reserva vs. salida**: el pedido **reserva** al crearse (solo hasta lo disponible; lo demás queda sin reservar y genera faltante, el pedido nunca se pierde). La **salida definitiva** ocurre una sola vez al **despachar** (`en_entrega`): baja existencia y reserva; la reserva sobrante se libera. `orders.stock_exited_at` y la máquina de estados evitan descontar dos veces. Cancelar antes de despachar libera reservas.
- **Estados del pedido**: `nuevo → pendiente_revision → en_preparacion ⇄ parcialmente_surtido → listo_entrega → en_entrega → entregado`; `cancelado` hasta antes de `en_entrega`. `nuevo` puede pasar directo a `en_preparacion` al aceptar.
- **Faltante** = `max(0, demanda de pedidos sin reservar + stock mínimo − disponible)`. Se crea/actualiza/resuelve automáticamente tras cada operación que cambie existencias o demanda, sin duplicar abiertos. Al recibir compra pasa a `parcialmente_resuelto` o `resuelto`.
- **Compras**: sugerido = faltante − por recibir de órdenes vigentes (borrador, pendiente, autorizada, parcial). Las existencias solo suben al **registrar una entrada/recepción**; crear u autorizar una orden no toca el inventario. No se puede recibir más de lo pendiente.
- **Correcciones**: las entradas y movimientos no se editan; se corrigen con ajustes (`ajuste_positivo/negativo`, `devolucion`, `correccion_autorizada` solo admin) que generan movimientos nuevos.
- **Idempotencia**: `POST /orders` con `Idempotency-Key` (único por restaurante); reintentos devuelven el mismo pedido.
- **Aislamiento**: un usuario de restaurante solo ve/modifica pedidos y notificaciones de su restaurante (verificado en servidor).
- **Notificaciones**: se guardan dentro de la misma transacción y se emiten por Socket.IO solo tras el `COMMIT`.
- **Zona horaria**: se almacena `timestamptz` (UTC); la presentación usa `settings.timezone`.
