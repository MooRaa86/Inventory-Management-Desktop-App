CREATE TABLE units (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL COLLATE NOCASE UNIQUE,
    symbol     TEXT    NOT NULL COLLATE NOCASE UNIQUE,
    active     INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT    NOT NULL,
    updated_at TEXT    NOT NULL
);

INSERT INTO units (name, symbol, active, created_at, updated_at) VALUES
    ('Piece', 'pcs', 1, datetime('now'), datetime('now')),
    ('Box',   'box', 1, datetime('now'), datetime('now')),
    ('Pack',  'pack', 1, datetime('now'), datetime('now')),
    ('Kilogram', 'kg', 1, datetime('now'), datetime('now')),
    ('Gram',  'g',   1, datetime('now'), datetime('now')),
    ('Liter', 'L',   1, datetime('now'), datetime('now')),
    ('Meter', 'm',   1, datetime('now'), datetime('now'));
