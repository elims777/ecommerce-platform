ALTER TABLE products
    ADD COLUMN IF NOT EXISTS is_variant_child BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS parent_product_id BIGINT REFERENCES products(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_products_parent_id ON products(parent_product_id);
