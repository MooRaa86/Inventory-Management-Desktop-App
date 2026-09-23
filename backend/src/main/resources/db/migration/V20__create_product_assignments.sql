CREATE TABLE product_assignments (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id  INTEGER NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    holder_id   INTEGER NOT NULL REFERENCES holders (id) ON DELETE RESTRICT,
    quantity    NUMERIC NOT NULL CHECK (quantity > 0),
    notes       TEXT    NOT NULL DEFAULT '',
    assigned_at TEXT    NOT NULL,
    assigned_by TEXT    NOT NULL DEFAULT '',
    updated_at  TEXT    NOT NULL,
    UNIQUE (product_id, holder_id)
);

CREATE INDEX idx_assignments_product ON product_assignments (product_id);
CREATE INDEX idx_assignments_holder ON product_assignments (holder_id);
CREATE INDEX idx_assignments_holder_product ON product_assignments (holder_id, product_id);