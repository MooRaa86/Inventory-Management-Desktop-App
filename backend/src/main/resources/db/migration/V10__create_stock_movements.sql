CREATE TABLE stock_movements (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id     INTEGER NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    movement_type  TEXT    NOT NULL
                   CHECK (movement_type IN ('STOCK_IN','STOCK_OUT','ADJUSTMENT_IN','ADJUSTMENT_OUT','RETURN_IN','RETURN_OUT')),
    quantity       NUMERIC NOT NULL CHECK (quantity > 0),
    previous_stock NUMERIC NOT NULL,
    new_stock      NUMERIC NOT NULL CHECK (new_stock >= 0),
    reference      TEXT    NOT NULL DEFAULT '',
    reason         TEXT    NOT NULL DEFAULT '',
    notes          TEXT    NOT NULL DEFAULT '',
    user_id        INTEGER REFERENCES users (id) ON DELETE SET NULL,
    username       TEXT    NOT NULL DEFAULT '',
    created_at     TEXT    NOT NULL
);

CREATE INDEX idx_stock_movements_product ON stock_movements (product_id, created_at);
CREATE INDEX idx_stock_movements_created_at ON stock_movements (created_at);
CREATE INDEX idx_stock_movements_type_date ON stock_movements (movement_type, created_at);
CREATE INDEX idx_stock_movements_username ON stock_movements (username);
