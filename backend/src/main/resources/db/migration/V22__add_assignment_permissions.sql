INSERT INTO permissions (code, description) VALUES
    ('HOLDER_VIEW',           'View holders (users/departments/places)'),
    ('HOLDER_MANAGE',         'Create/update/delete holders'),
    ('ASSIGNMENT_VIEW',       'View product assignments'),
    ('ASSIGNMENT_MANAGE',     'Assign/unassign/transfer products');

-- ADMIN: all new permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'ADMIN'), id FROM permissions
WHERE code IN ('HOLDER_VIEW','HOLDER_MANAGE','ASSIGNMENT_VIEW','ASSIGNMENT_MANAGE');

-- WAREHOUSE_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'WAREHOUSE_MANAGER'), id FROM permissions
WHERE code IN ('HOLDER_VIEW','HOLDER_MANAGE','ASSIGNMENT_VIEW','ASSIGNMENT_MANAGE');

-- WAREHOUSE_EMPLOYEE (view only)
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'WAREHOUSE_EMPLOYEE'), id FROM permissions
WHERE code IN ('HOLDER_VIEW','ASSIGNMENT_VIEW');

-- VIEWER (view only)
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'VIEWER'), id FROM permissions
WHERE code IN ('HOLDER_VIEW','ASSIGNMENT_VIEW');