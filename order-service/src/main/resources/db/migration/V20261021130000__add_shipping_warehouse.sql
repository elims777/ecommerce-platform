ALTER TABLE orders
    ADD COLUMN shipping_warehouse_id   BIGINT,
    ADD COLUMN shipping_warehouse_name VARCHAR(100),
    ADD COLUMN shipping_warehouse_city VARCHAR(100);
