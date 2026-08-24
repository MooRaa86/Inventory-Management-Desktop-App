CREATE TABLE purchases (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    purchase_number TEXT    NOT NULL COLLATE NOCASE UNIQUE,
    supplier_id     INTEGER NOT NULL REFERENCES suppliers (id) ON DELETE RESTRICT,
    purchase_date   TEXT    NOT NULL,
    status          TEXT    NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'RECEIVED', 'CANCELLED')),
    total_cents     INTEGER NOT NULL DEFAULT 0 CHECK (total_cents >= 0),
    notes           TEXT    NOT NULL DEFAULT '',
    created_by      INTEGER REFERENCES users (id) ON DELETE SET NULL,
    received_by     INTEGER REFERENCES users (id) ON DELETE SET NULL,
    received_at     TEXT,
    created_at      TEXT    NOT NULL,
    updated_at      TEXT    NOT NULL
);

CREATE TABLE purchase_items (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    purchase_id      INTEGER NOT NULL REFERENCES purchases (id) ON DELETE CASCADE,
    product_id       INTEGER NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    quantity         NUMERIC NOT NULL CHECK (quantity > 0),
    unit_cost_cents  INTEGER NOT NULL DEFAULT 0 CHECK (unit_cost_cents >= 0),
    line_total_cents INTEGER NOT NULL DEFAULT 0 CHECK (line_total_cents >= 0)
);

CREATE INDEX idx_purchases_supplier ON purchases (supplier_id);
CREATE INDEX idx_purchases_status ON purchases (status);
CREATE INDEX idx_purchases_date ON purchases (purchase_date);
CREATE INDEX idx_purchase_items_purchase ON purchase_items (purchase_id);
CREATE INDEX idx_purchase_items_product ON purchase_items (product_id);
