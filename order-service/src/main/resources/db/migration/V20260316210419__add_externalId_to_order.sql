ALTER TABLE orders
ADD COLUMN external_id VARCHAR(50);

CREATE INDEX idx_orders_external_id ON orders(external_id);