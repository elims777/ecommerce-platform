-- Удаляем variant_id из cart_items (order-service управляет своей схемой,
-- но колонка могла попасть сюда при тестах — страхуемся через IF EXISTS)
ALTER TABLE cart_items DROP COLUMN IF EXISTS variant_id;

-- Новые поля товара
ALTER TABLE products ADD COLUMN IF NOT EXISTS barcode VARCHAR(50);
ALTER TABLE products ADD COLUMN IF NOT EXISTS country_of_origin VARCHAR(100);

-- Документы товара (сертификаты, PDF и т.д.)
CREATE TABLE IF NOT EXISTS product_documents (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    url           VARCHAR(500) NOT NULL,
    content_type  VARCHAR(100),
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_product_documents_product ON product_documents(product_id);

-- Дропаем product_variants — данные больше не нужны,
-- перед деплоем БД очищается полностью
DROP TABLE IF EXISTS product_variants;
