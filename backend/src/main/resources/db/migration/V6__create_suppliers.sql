CREATE TABLE suppliers (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL COLLATE NOCASE UNIQUE,
    phone      TEXT    NOT NULL DEFAULT '',
    email      TEXT    NOT NULL DEFAULT '',
    address    TEXT    NOT NULL DEFAULT '',
    tax_number TEXT    NOT NULL DEFAULT '',
    notes      TEXT    NOT NULL DEFAULT '',
    active     INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT    NOT NULL,
    updated_at TEXT    NOT NULL
);

CREATE INDEX idx_suppliers_active ON suppliers (active);
CREATE INDEX idx_suppliers_name ON suppliers (name);
