CREATE TABLE issues (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    issue_number  TEXT    NOT NULL COLLATE NOCASE UNIQUE,
    department    TEXT    NOT NULL,
    status        TEXT    NOT NULL DEFAULT 'DRAFT'
                  CHECK (status IN ('DRAFT', 'APPROVED', 'COMPLETED', 'CANCELLED')),
    requested_by  TEXT    NOT NULL DEFAULT '',
    approved_by   INTEGER REFERENCES users (id) ON DELETE SET NULL,
    completed_by  INTEGER REFERENCES users (id) ON DELETE SET NULL,
    completed_at  TEXT,
    notes         TEXT    NOT NULL DEFAULT '',
    created_by    INTEGER REFERENCES users (id) ON DELETE SET NULL,
    created_at    TEXT    NOT NULL,
    updated_at    TEXT    NOT NULL
);

CREATE TABLE issue_items (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    issue_id   INTEGER NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    product_id INTEGER NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    quantity   NUMERIC NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_issues_status ON issues (status);
CREATE INDEX idx_issues_department ON issues (department);
CREATE INDEX idx_issue_items_issue ON issue_items (issue_id);
CREATE INDEX idx_issue_items_product ON issue_items (product_id);
