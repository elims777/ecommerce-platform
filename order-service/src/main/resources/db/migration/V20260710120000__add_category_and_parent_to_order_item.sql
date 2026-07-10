ALTER TABLE order_items ADD COLUMN category_external_id VARCHAR(50);
ALTER TABLE order_items ADD COLUMN parent_product_id BIGINT;
