CREATE TABLE roles (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL DEFAULT ''
);

INSERT INTO roles (name, description) VALUES
    ('ADMIN',              'Full system administrator'),
    ('WAREHOUSE_MANAGER',  'Warehouse manager'),
    ('WAREHOUSE_EMPLOYEE', 'Warehouse employee'),
    ('VIEWER',             'Read-only viewer');
