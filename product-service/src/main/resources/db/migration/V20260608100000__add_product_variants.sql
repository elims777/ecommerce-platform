-- Поле source для различения внутренних товаров и товаров от партнёров
ALTER TABLE products ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'INTERNAL';

-- Таблица вариантов товара (размер/рост/цвет и т.д.)
CREATE TABLE product_variants (
    id             BIGSERIAL PRIMARY KEY,
    product_id     BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku            VARCHAR(100),
    price          DECIMAL(10, 2),
    wholesale_price DECIMAL(10, 2),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    attributes     JSONB,
    is_active      BOOLEAN NOT NULL DEFAULT true,
    external_id    VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_variants_product  ON product_variants(product_id);
CREATE INDEX idx_product_variants_sku      ON product_variants(sku) WHERE sku IS NOT NULL;
CREATE UNIQUE INDEX idx_product_variants_external_id ON product_variants(external_id) WHERE external_id IS NOT NULL;

-- Мигрируем каждый существующий товар в вариант-по-умолчанию.
-- Поля sku/price/wholesale_price/stock_quantity на products остаются (deprecated).
INSERT INTO product_variants (product_id, sku, price, wholesale_price, stock_quantity, is_active, external_id, created_at, updated_at)
SELECT
    id,
    sku,
    price,
    wholesale_price,
    stock_quantity,
    is_active,
    CASE WHEN external_id IS NOT NULL THEN external_id || '#default' ELSE NULL END,
    created_at,
    updated_at
FROM products;
