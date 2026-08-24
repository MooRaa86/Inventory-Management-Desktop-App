-- Drop sku/barcode from products (schema simplification).
-- SQLite cannot DROP COLUMN with UNIQUE constraints reliably, so rebuild the table.
-- Runs OUTSIDE a transaction so PRAGMA foreign_keys is honored.
PRAGMA foreign_keys=OFF;

CREATE TABLE products_new (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL,
    description   TEXT    NOT NULL DEFAULT '',
    category_id   INTEGER REFERENCES categories (id) ON DELETE RESTRICT,
    unit_id       INTEGER NOT NULL REFERENCES units (id) ON DELETE RESTRICT,
    supplier_id   INTEGER REFERENCES suppliers (id) ON DELETE SET NULL,
    min_stock     NUMERIC NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
    max_stock     NUMERIC CHECK (max_stock IS NULL OR max_stock >= 0),
    current_stock NUMERIC NOT NULL DEFAULT 0 CHECK (current_stock >= 0),
    cost_cents    INTEGER NOT NULL DEFAULT 0 CHECK (cost_cents >= 0),
    sell_cents    INTEGER NOT NULL DEFAULT 0 CHECK (sell_cents >= 0),
    active        INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at    TEXT    NOT NULL,
    updated_at    TEXT    NOT NULL
);

INSERT INTO products_new (id, name, description, category_id, unit_id, supplier_id,
                          min_stock, max_stock, current_stock, cost_cents, sell_cents,
                          active, created_at, updated_at)
SELECT id, name, description, category_id, unit_id, supplier_id,
       min_stock, max_stock, current_stock, cost_cents, sell_cents,
       active, created_at, updated_at
FROM products;

DROP TABLE products;
ALTER TABLE products_new RENAME TO products;

CREATE INDEX idx_products_name ON products (name);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_supplier ON products (supplier_id);
CREATE INDEX idx_products_active ON products (active);
CREATE INDEX idx_products_current_stock ON products (current_stock);

PRAGMA foreign_key_check;
PRAGMA foreign_keys=ON;
