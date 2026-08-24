ALTER TABLE stock_movements ADD COLUMN product_sku TEXT NOT NULL DEFAULT '';
CREATE INDEX idx_stock_movements_sku ON stock_movements (product_sku);
