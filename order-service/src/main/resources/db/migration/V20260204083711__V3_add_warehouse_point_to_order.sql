ALTER TABLE orders ADD COLUMN warehouse_point_id BIGINT;

ALTER TABLE orders ADD CONSTRAINT fk_orders_warehouse_point
    FOREIGN KEY (warehouse_point_id) REFERENCES warehouse_points(id);

CREATE INDEX idx_orders_warehouse_point_id ON orders(warehouse_point_id);