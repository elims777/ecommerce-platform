CREATE TABLE warehouses (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(30)  NOT NULL UNIQUE,
    name             VARCHAR(100) NOT NULL,
    city             VARCHAR(100) NOT NULL,
    pickup_available BOOLEAN      NOT NULL DEFAULT FALSE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE product_stocks (
    id           BIGSERIAL PRIMARY KEY,
    product_id   BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
    quantity     INT    NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_stocks_product_warehouse UNIQUE (product_id, warehouse_id)
);
CREATE INDEX idx_product_stocks_warehouse ON product_stocks(warehouse_id);

-- Индекс по sku уже есть (idx_products_sku, V20260310210040)
ALTER TABLE products
    ADD COLUMN merged_into_product_id BIGINT REFERENCES products(id) ON DELETE SET NULL,
    ADD COLUMN merge_locked BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_products_merged_into ON products(merged_into_product_id);

INSERT INTO warehouses(code, name, city, pickup_available, sort_order) VALUES
    ('SYKTYVKAR', 'Основной склад', 'Сыктывкар', TRUE, 1),
    ('PUSHKIN',   'Склад Пушкин',   'Пушкин',    FALSE, 2);

-- Перенос текущих остатков: Факел -> Пушкин, остальное (1С и ручные) -> Сыктывкар
INSERT INTO product_stocks(product_id, warehouse_id, quantity)
SELECT p.id, w.id, p.stock_quantity
FROM products p
JOIN warehouses w ON w.code = CASE WHEN p.source = 'FTK' THEN 'PUSHKIN' ELSE 'SYKTYVKAR' END
WHERE p.stock_quantity > 0;
