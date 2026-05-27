-- Products: поля для связи с номенклатурой 1С
ALTER TABLE products ADD COLUMN external_id VARCHAR(50);
ALTER TABLE products ADD COLUMN sku VARCHAR(100);
ALTER TABLE products ADD COLUMN external_code VARCHAR(50);
ALTER TABLE products ADD COLUMN unit_of_measure VARCHAR(20);
ALTER TABLE products ADD COLUMN vat_rate INTEGER;

-- external_id — UUID товара из 1С, уникальный ключ для матчинга при синхронизации
CREATE UNIQUE INDEX idx_products_external_id ON products(external_id) WHERE external_id IS NOT NULL;

-- sku — артикул, может быть не уникальным (в 1С бывают дубли артикулов)
CREATE INDEX idx_products_sku ON products(sku) WHERE sku IS NOT NULL;

-- external_code — код номенклатуры 1С (НФ-00003735), для обратной связи при обмене заказами
CREATE INDEX idx_products_external_code ON products(external_code) WHERE external_code IS NOT NULL;

-- Categories: поле для связи с группами/категориями 1С (запас на будущее)
ALTER TABLE categories ADD COLUMN external_id VARCHAR(50);

CREATE UNIQUE INDEX idx_categories_external_id ON categories(external_id) WHERE external_id IS NOT NULL;

-- Дефолтная категория для товаров из 1С, ожидающих ручного распределения.
-- isActive = false — не видна на сайте, только в админке.
INSERT INTO categories (name, slug, description, is_active, display_order)
VALUES ('Импорт из 1С', 'import-1c', 'Товары из 1С, ожидающие распределения по категориям', false, 999);