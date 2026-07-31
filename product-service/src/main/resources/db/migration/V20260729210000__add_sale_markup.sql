-- Гибкая система акций: метка + процент на категории и на товаре.
-- Цены в products НЕ меняются — процент применяется на лету при выдаче.
-- Положительный процент = наценка, отрицательный = скидка.

ALTER TABLE categories
    ADD COLUMN is_sale BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN sale_markup_percent NUMERIC(5, 2);

ALTER TABLE products
    ADD COLUMN is_sale BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN sale_markup_percent NUMERIC(5, 2);

-- Переносим захардкоженное поведение: ФТК-товары в "Распродаже" продавались с +10%.
-- Slug может отличаться (уникализация при коллизии имён), поэтому ищем ещё и по названию.
UPDATE categories
   SET is_sale = true, sale_markup_percent = 10.00
 WHERE slug = 'rasprodazha' OR lower(name) = 'распродажа';

-- Выборка товаров для раздела "Акции"
CREATE INDEX idx_products_is_sale ON products (is_sale) WHERE is_sale = true;
