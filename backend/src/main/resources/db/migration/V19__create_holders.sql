CREATE TABLE holders (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL COLLATE NOCASE UNIQUE,
    type       TEXT    NOT NULL DEFAULT 'OTHER'
               CHECK (type IN ('USER','DEPARTMENT','PLACE','OTHER')),
    contact    TEXT    NOT NULL DEFAULT '',
    notes      TEXT    NOT NULL DEFAULT '',
    active     INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT    NOT NULL,
    updated_at TEXT    NOT NULL
);

CREATE INDEX idx_holders_name ON holders (name);
CREATE INDEX idx_holders_type ON holders (type);
CREATE INDEX idx_holders_active ON holders (active);