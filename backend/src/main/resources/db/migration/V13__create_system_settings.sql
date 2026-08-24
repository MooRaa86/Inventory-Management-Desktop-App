CREATE TABLE system_settings (
    setting_key   TEXT PRIMARY KEY,
    setting_value TEXT NOT NULL DEFAULT '',
    updated_at    TEXT NOT NULL,
    updated_by    TEXT NOT NULL DEFAULT ''
);

INSERT INTO system_settings (setting_key, setting_value, updated_at, updated_by) VALUES
    ('company.name',               'Company Inventory', datetime('now'), 'system'),
    ('company.currency',           'USD',               datetime('now'), 'system'),
    ('backup.enabled',             'true',              datetime('now'), 'system'),
    ('backup.time',                '02:00',             datetime('now'), 'system'),
    ('backup.retention.count',     '30',                datetime('now'), 'system'),
    ('backup.auto.cleanup',        'true',              datetime('now'), 'system'),
    ('backup.verification',        'true',              datetime('now'), 'system'),
    ('session.timeout.minutes',    '30',                datetime('now'), 'system'),
    ('inventory.allow_negative',   'false',             datetime('now'), 'system'),
    ('app.version',                '1.0.0',             datetime('now'), 'system');
