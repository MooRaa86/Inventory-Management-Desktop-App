CREATE TABLE assignment_transfers (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id        INTEGER NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    from_holder_id    INTEGER REFERENCES holders (id) ON DELETE SET NULL,
    to_holder_id      INTEGER REFERENCES holders (id) ON DELETE SET NULL,
    quantity          NUMERIC NOT NULL CHECK (quantity > 0),
    action            TEXT    NOT NULL
                      CHECK (action IN ('ASSIGN','UNASSIGN','TRANSFER')),
    notes             TEXT    NOT NULL DEFAULT '',
    transfer_by       TEXT    NOT NULL DEFAULT '',
    created_at        TEXT    NOT NULL
);

CREATE INDEX idx_transfers_product ON assignment_transfers (product_id, created_at);
CREATE INDEX idx_transfers_from_holder ON assignment_transfers (from_holder_id);
CREATE INDEX idx_transfers_to_holder ON assignment_transfers (to_holder_id);
CREATE INDEX idx_transfers_created_at ON assignment_transfers (created_at);