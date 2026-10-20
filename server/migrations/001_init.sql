-- Esquema inicial del sistema de almacén. Ver docs/MODELO_DE_DATOS.md

CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Los registros históricos no se pueden modificar ni borrar
CREATE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'La tabla % es de solo inserción (registro histórico auditable)', TG_TABLE_NAME
    USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

-- ───────── Secuencias de folios ─────────
CREATE SEQUENCE seq_order_folio;
CREATE SEQUENCE seq_receipt_folio;
CREATE SEQUENCE seq_purchase_folio;
CREATE SEQUENCE seq_shortage_folio;

-- ───────── Configuración ─────────
CREATE TABLE settings (
  key        text PRIMARY KEY,
  value      jsonb NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- ───────── Usuarios y roles ─────────
CREATE TABLE roles (
  id   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code text NOT NULL UNIQUE,
  name text NOT NULL
);

CREATE TABLE restaurants (
  id           integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code         text NOT NULL UNIQUE,
  name         text NOT NULL,
  address      text,
  manager_name text NOT NULL,
  phone        text,
  active       boolean NOT NULL DEFAULT true,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE users (
  id                   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username             text NOT NULL,
  full_name            text NOT NULL,
  password_hash        text NOT NULL,
  role_id              integer NOT NULL REFERENCES roles(id),
  restaurant_id        integer REFERENCES restaurants(id),
  active               boolean NOT NULL DEFAULT true,
  must_change_password boolean NOT NULL DEFAULT false,
  failed_logins        integer NOT NULL DEFAULT 0,
  locked_until         timestamptz,
  last_login_at        timestamptz,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_username_uq ON users (lower(username));

-- ───────── Catálogo ─────────
CREATE TABLE categories (
  id          integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        text NOT NULL UNIQUE,
  description text,
  active      boolean NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE units (
  id         integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code       text NOT NULL UNIQUE,
  name       text NOT NULL,
  active     boolean NOT NULL DEFAULT true
);

CREATE TABLE products (
  id          integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  sku         text NOT NULL UNIQUE,
  name        text NOT NULL,
  description text,
  category_id integer NOT NULL REFERENCES categories(id),
  unit_id     integer NOT NULL REFERENCES units(id),
  min_stock   numeric(14,3) NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
  cost_unit   numeric(14,2) NOT NULL DEFAULT 0 CHECK (cost_unit >= 0),
  sale_price  numeric(14,2) CHECK (sale_price IS NULL OR sale_price >= 0),
  active      boolean NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX products_category_idx ON products (category_id);

-- Catálogo restringido por restaurante (si un restaurante no tiene filas, ve todo el catálogo activo)
CREATE TABLE restaurant_product_access (
  restaurant_id integer NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
  product_id    integer NOT NULL REFERENCES products(id),
  PRIMARY KEY (restaurant_id, product_id)
);

-- ───────── Inventario ─────────
CREATE TABLE inventory (
  product_id            integer PRIMARY KEY REFERENCES products(id),
  on_hand               numeric(14,3) NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
  reserved              numeric(14,3) NOT NULL DEFAULT 0 CHECK (reserved >= 0),
  last_count_qty        numeric(14,3),
  last_count_difference numeric(14,3),
  last_counted_at       timestamptz,
  last_movement_at      timestamptz,
  updated_at            timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT reserved_le_on_hand CHECK (reserved <= on_hand)
);

CREATE TABLE inventory_movements (
  id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id       integer NOT NULL REFERENCES products(id),
  type             text NOT NULL CHECK (type IN (
    'entrada_compra','salida_pedido','reserva','liberacion_reserva',
    'ajuste_positivo','ajuste_negativo','devolucion','correccion_autorizada')),
  quantity         numeric(14,3) NOT NULL CHECK (quantity > 0),
  delta_on_hand    numeric(14,3) NOT NULL,
  delta_reserved   numeric(14,3) NOT NULL,
  on_hand_before   numeric(14,3) NOT NULL,
  on_hand_after    numeric(14,3) NOT NULL,
  reserved_before  numeric(14,3) NOT NULL,
  reserved_after   numeric(14,3) NOT NULL,
  reference_type   text,
  reference_id     integer,
  reference_folio  text,
  reason           text,
  notes            text,
  user_id          integer REFERENCES users(id),
  created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX movements_product_idx ON inventory_movements (product_id, created_at DESC);
CREATE INDEX movements_created_idx ON inventory_movements (created_at DESC);
CREATE TRIGGER movements_immutable BEFORE UPDATE OR DELETE ON inventory_movements
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ───────── Proveedores, entradas y compras ─────────
CREATE TABLE suppliers (
  id         integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name       text NOT NULL UNIQUE,
  contact    text,
  phone      text,
  email      text,
  address    text,
  active     boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE purchase_orders (
  id              integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  folio           text NOT NULL UNIQUE,
  supplier_id     integer NOT NULL REFERENCES suppliers(id),
  status          text NOT NULL DEFAULT 'borrador' CHECK (status IN (
    'borrador','pendiente_autorizacion','autorizada','parcialmente_recibida','recibida','cancelada')),
  expected_date   date,
  notes           text,
  created_by      integer NOT NULL REFERENCES users(id),
  submitted_at    timestamptz,
  authorized_by   integer REFERENCES users(id),
  authorized_at   timestamptz,
  cancelled_at    timestamptz,
  cancel_reason   text,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX purchase_orders_status_idx ON purchase_orders (status);

CREATE TABLE purchase_order_items (
  id                 integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  purchase_order_id  integer NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
  product_id         integer NOT NULL REFERENCES products(id),
  qty_ordered        numeric(14,3) NOT NULL CHECK (qty_ordered > 0),
  qty_received       numeric(14,3) NOT NULL DEFAULT 0 CHECK (qty_received >= 0),
  unit_cost          numeric(14,2) NOT NULL CHECK (unit_cost >= 0),
  original_shortage  numeric(14,3),
  UNIQUE (purchase_order_id, product_id),
  CONSTRAINT received_le_ordered CHECK (qty_received <= qty_ordered)
);
CREATE INDEX po_items_product_idx ON purchase_order_items (product_id);

CREATE TABLE goods_receipts (
  id                integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  folio             text NOT NULL UNIQUE,
  supplier_id       integer NOT NULL REFERENCES suppliers(id),
  purchase_order_id integer REFERENCES purchase_orders(id),
  invoice_ref       text,
  received_at       timestamptz NOT NULL DEFAULT now(),
  total_amount      numeric(14,2) NOT NULL DEFAULT 0,
  notes             text,
  user_id           integer NOT NULL REFERENCES users(id),
  created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER receipts_immutable BEFORE UPDATE OR DELETE ON goods_receipts
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE goods_receipt_items (
  id                     integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  receipt_id             integer NOT NULL REFERENCES goods_receipts(id),
  product_id             integer NOT NULL REFERENCES products(id),
  purchase_order_item_id integer REFERENCES purchase_order_items(id),
  quantity               numeric(14,3) NOT NULL CHECK (quantity > 0),
  unit_cost              numeric(14,2) NOT NULL CHECK (unit_cost >= 0),
  line_total             numeric(14,2) NOT NULL
);
CREATE TRIGGER receipt_items_immutable BEFORE UPDATE OR DELETE ON goods_receipt_items
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ───────── Faltantes ─────────
CREATE TABLE shortages (
  id                 integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  folio              text NOT NULL UNIQUE,
  product_id         integer NOT NULL REFERENCES products(id),
  quantity_required  numeric(14,3) NOT NULL CHECK (quantity_required >= 0),
  on_hand_snapshot   numeric(14,3) NOT NULL,
  available_snapshot numeric(14,3) NOT NULL,
  quantity_short     numeric(14,3) NOT NULL CHECK (quantity_short >= 0),
  reason             text NOT NULL CHECK (reason IN (
    'pedido_excede','stock_minimo','agotado','inventario_fisico','solicitud_compra','manual')),
  priority           text NOT NULL DEFAULT 'media' CHECK (priority IN ('baja','media','alta')),
  status             text NOT NULL DEFAULT 'pendiente' CHECK (status IN (
    'pendiente','en_revision','en_proceso_compra','parcialmente_resuelto','resuelto')),
  notes              text,
  detected_at        timestamptz NOT NULL DEFAULT now(),
  resolved_at        timestamptz,
  created_by         integer REFERENCES users(id),
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now()
);
-- Una sola incidencia abierta por producto (evita duplicados)
CREATE UNIQUE INDEX shortages_one_open_per_product
  ON shortages (product_id) WHERE status <> 'resuelto';

CREATE TABLE purchase_order_shortages (
  purchase_order_id integer NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
  shortage_id       integer NOT NULL REFERENCES shortages(id),
  PRIMARY KEY (purchase_order_id, shortage_id)
);

-- ───────── Pedidos de restaurantes ─────────
CREATE TABLE orders (
  id              integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  folio           text NOT NULL UNIQUE,
  restaurant_id   integer NOT NULL REFERENCES restaurants(id),
  requested_by    integer NOT NULL REFERENCES users(id),
  assigned_to     integer REFERENCES users(id),
  status          text NOT NULL DEFAULT 'nuevo' CHECK (status IN (
    'nuevo','pendiente_revision','en_preparacion','parcialmente_surtido',
    'listo_entrega','en_entrega','entregado','cancelado')),
  notes           text,
  warehouse_notes text,
  idempotency_key text,
  stock_exited_at timestamptz,
  delivered_at    timestamptz,
  received_at     timestamptz,
  received_by     integer REFERENCES users(id),
  cancel_reason   text,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (restaurant_id, idempotency_key)
);
CREATE INDEX orders_status_idx ON orders (status, created_at DESC);
CREATE INDEX orders_restaurant_idx ON orders (restaurant_id, created_at DESC);

CREATE TABLE order_items (
  id             integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id       integer NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_id     integer NOT NULL REFERENCES products(id),
  sku            text NOT NULL,
  product_name   text NOT NULL,
  unit_name      text NOT NULL,
  unit_price     numeric(14,2),
  qty_requested  numeric(14,3) NOT NULL CHECK (qty_requested > 0),
  qty_authorized numeric(14,3) CHECK (qty_authorized >= 0),
  qty_reserved   numeric(14,3) NOT NULL DEFAULT 0 CHECK (qty_reserved >= 0),
  qty_prepared   numeric(14,3) NOT NULL DEFAULT 0 CHECK (qty_prepared >= 0),
  qty_delivered  numeric(14,3) NOT NULL DEFAULT 0 CHECK (qty_delivered >= 0),
  UNIQUE (order_id, product_id)
);
CREATE INDEX order_items_product_idx ON order_items (product_id);

CREATE TABLE order_events (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id    integer NOT NULL REFERENCES orders(id),
  event       text NOT NULL,
  from_status text,
  to_status   text,
  user_id     integer REFERENCES users(id),
  note        text,
  changes     jsonb,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX order_events_order_idx ON order_events (order_id, created_at);
CREATE TRIGGER order_events_immutable BEFORE UPDATE OR DELETE ON order_events
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE deliveries (
  id            integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id      integer NOT NULL UNIQUE REFERENCES orders(id),
  assigned_to   integer REFERENCES users(id),
  status        text NOT NULL DEFAULT 'en_ruta' CHECK (status IN ('pendiente','en_ruta','entregada')),
  dispatched_at timestamptz,
  delivered_at  timestamptz,
  received_by   integer REFERENCES users(id),
  received_at   timestamptz,
  notes         text,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);

-- ───────── Notificaciones y auditoría ─────────
CREATE TABLE notifications (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type          text NOT NULL,
  title         text NOT NULL,
  body          text,
  payload       jsonb NOT NULL DEFAULT '{}'::jsonb,
  audience      text NOT NULL CHECK (audience IN ('staff','restaurant')),
  restaurant_id integer REFERENCES restaurants(id),
  created_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT restaurant_audience CHECK ((audience = 'restaurant') = (restaurant_id IS NOT NULL))
);
CREATE INDEX notifications_created_idx ON notifications (created_at DESC);

CREATE TABLE notification_reads (
  notification_id bigint NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  user_id         integer NOT NULL REFERENCES users(id),
  read_at         timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (notification_id, user_id)
);

CREATE TABLE audit_logs (
  id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    integer REFERENCES users(id),
  action     text NOT NULL,
  entity     text,
  entity_id  text,
  detail     jsonb,
  ip         text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_created_idx ON audit_logs (created_at DESC);
CREATE TRIGGER audit_immutable BEFORE UPDATE OR DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ───────── Triggers updated_at ─────────
CREATE TRIGGER t_upd BEFORE UPDATE ON restaurants FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER t_upd BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER t_upd BEFORE UPDATE ON categories FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER t_upd BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER t_upd BEFORE UPDATE ON inventory FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER t_upd BEFORE UPDATE ON suppliers FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER t_upd BEFORE UPDATE ON purchase_orders FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER t_upd BEFORE UPDATE ON shortages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER t_upd BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER t_upd BEFORE UPDATE ON deliveries FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────── Datos base (no son datos de demostración) ─────────
INSERT INTO roles (code, name) VALUES
  ('admin', 'Administrador'),
  ('almacen', 'Encargado de almacén'),
  ('compras', 'Compras'),
  ('entrega', 'Personal de entrega'),
  ('restaurante', 'Usuario de restaurante');

INSERT INTO units (code, name) VALUES
  ('pza', 'Pieza'), ('caja', 'Caja'), ('paq', 'Paquete'),
  ('kg', 'Kilogramo'), ('lt', 'Litro'), ('bolsa', 'Bolsa');

INSERT INTO settings (key, value) VALUES
  ('company_name', '"Almacén Central"'),
  ('timezone', '"America/Mexico_City"'),
  ('show_prices', 'false'),
  ('notification_sound', 'true');
