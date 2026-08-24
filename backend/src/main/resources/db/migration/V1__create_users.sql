CREATE TABLE users (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    username               TEXT    NOT NULL COLLATE NOCASE UNIQUE,
    email                  TEXT    NOT NULL COLLATE NOCASE UNIQUE,
    full_name              TEXT    NOT NULL,
    password_hash          TEXT    NOT NULL,
    active                 INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    must_change_password   INTEGER NOT NULL DEFAULT 0 CHECK (must_change_password IN (0, 1)),
    failed_login_attempts  INTEGER NOT NULL DEFAULT 0,
    locked_until           TEXT,
    last_login_at          TEXT,
    created_at             TEXT    NOT NULL,
    updated_at             TEXT    NOT NULL
);

CREATE INDEX idx_users_active ON users (active);
