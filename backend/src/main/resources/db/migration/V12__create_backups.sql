CREATE TABLE backups (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    filename        TEXT    NOT NULL UNIQUE,
    backup_type     TEXT    NOT NULL
                    CHECK (backup_type IN ('AUTOMATIC', 'MANUAL', 'SAFETY', 'IMPORTED')),
    status          TEXT    NOT NULL
                    CHECK (status IN ('SUCCESS', 'FAILED')),
    size_bytes      INTEGER NOT NULL DEFAULT 0,
    verified        INTEGER NOT NULL DEFAULT 0 CHECK (verified IN (0, 1)),
    note            TEXT    NOT NULL DEFAULT '',
    error_message   TEXT    NOT NULL DEFAULT '',
    created_by      INTEGER REFERENCES users (id) ON DELETE SET NULL,
    created_by_name TEXT    NOT NULL DEFAULT 'system',
    created_at      TEXT    NOT NULL
);

CREATE INDEX idx_backups_type_status ON backups (backup_type, status);
CREATE INDEX idx_backups_created_at ON backups (created_at);
