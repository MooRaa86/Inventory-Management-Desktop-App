CREATE TABLE permissions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL DEFAULT ''
);

CREATE TABLE role_permissions (
    role_id       INTEGER NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

INSERT INTO permissions (code, description) VALUES
    ('USER_VIEW',         'View users'),
    ('USER_CREATE',       'Create users'),
    ('USER_UPDATE',       'Update users'),
    ('USER_DISABLE',      'Enable/disable users'),
    ('ROLE_ASSIGN',       'Assign roles to users'),

    ('PRODUCT_VIEW',      'View products'),
    ('PRODUCT_CREATE',    'Create products'),
    ('PRODUCT_UPDATE',    'Update products'),
    ('PRODUCT_DELETE',    'Delete/deactivate products'),

    ('CATEGORY_VIEW',     'View categories'),
    ('CATEGORY_CREATE',   'Create categories'),
    ('CATEGORY_UPDATE',   'Update categories'),
    ('CATEGORY_DELETE',   'Delete categories'),

    ('UNIT_VIEW',         'View units'),
    ('UNIT_CREATE',       'Create units'),
    ('UNIT_UPDATE',       'Update units'),
    ('UNIT_DELETE',       'Delete units'),

    ('STOCK_VIEW',        'View stock levels and movements'),
    ('STOCK_IN',          'Receive stock into warehouse'),
    ('STOCK_OUT',         'Issue stock out of warehouse'),
    ('STOCK_ADJUST',      'Adjust stock levels'),

    ('SUPPLIER_VIEW',     'View suppliers'),
    ('SUPPLIER_CREATE',   'Create suppliers'),
    ('SUPPLIER_UPDATE',   'Update suppliers'),
    ('SUPPLIER_DELETE',   'Delete suppliers'),

    ('PURCHASE_VIEW',     'View purchases'),
    ('PURCHASE_CREATE',   'Create purchases'),
    ('PURCHASE_RECEIVE',  'Receive purchases into stock'),

    ('ISSUE_VIEW',        'View warehouse issues'),
    ('ISSUE_CREATE',      'Create warehouse issues'),
    ('ISSUE_APPROVE',     'Approve warehouse issues'),
    ('ISSUE_COMPLETE',    'Complete warehouse issues'),
    ('ISSUE_CANCEL',      'Cancel warehouse issues'),

    ('REPORT_VIEW',       'View reports'),
    ('REPORT_EXPORT',     'Export reports'),

    ('BACKUP_VIEW',       'View backups'),
    ('BACKUP_CREATE',     'Create backups'),
    ('BACKUP_RESTORE',    'Restore backups'),
    ('BACKUP_DELETE',     'Delete backups'),
    ('BACKUP_EXPORT',     'Export backups'),
    ('BACKUP_IMPORT',     'Import backups'),

    ('AUDIT_VIEW',        'View audit logs'),

    ('SETTINGS_VIEW',     'View settings'),
    ('SETTINGS_UPDATE',   'Change settings');

-- ADMIN: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'ADMIN'), id FROM permissions;

-- WAREHOUSE_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'WAREHOUSE_MANAGER'), id FROM permissions
WHERE code IN (
    'PRODUCT_VIEW','PRODUCT_CREATE','PRODUCT_UPDATE',
    'CATEGORY_VIEW','CATEGORY_CREATE','CATEGORY_UPDATE','CATEGORY_DELETE',
    'UNIT_VIEW','UNIT_CREATE','UNIT_UPDATE','UNIT_DELETE',
    'STOCK_VIEW','STOCK_IN','STOCK_OUT','STOCK_ADJUST',
    'SUPPLIER_VIEW','SUPPLIER_CREATE','SUPPLIER_UPDATE','SUPPLIER_DELETE',
    'PURCHASE_VIEW','PURCHASE_CREATE','PURCHASE_RECEIVE',
    'ISSUE_VIEW','ISSUE_CREATE','ISSUE_APPROVE','ISSUE_COMPLETE','ISSUE_CANCEL',
    'REPORT_VIEW','REPORT_EXPORT',
    'AUDIT_VIEW'
);

-- WAREHOUSE_EMPLOYEE
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'WAREHOUSE_EMPLOYEE'), id FROM permissions
WHERE code IN (
    'PRODUCT_VIEW','CATEGORY_VIEW','UNIT_VIEW',
    'STOCK_VIEW','STOCK_IN','STOCK_OUT',
    'SUPPLIER_VIEW',
    'PURCHASE_VIEW',
    'ISSUE_VIEW','ISSUE_CREATE',
    'REPORT_VIEW'
);

-- VIEWER
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'VIEWER'), id FROM permissions
WHERE code IN (
    'PRODUCT_VIEW','CATEGORY_VIEW','UNIT_VIEW',
    'STOCK_VIEW','SUPPLIER_VIEW','PURCHASE_VIEW','ISSUE_VIEW',
    'REPORT_VIEW'
);

CREATE INDEX idx_role_permissions_role ON role_permissions (role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions (permission_id);
