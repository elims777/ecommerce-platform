-- variant_id в корзине — nullable для обратной совместимости с текущими товарами
ALTER TABLE cart_items ADD COLUMN variant_id BIGINT NULL;

-- variant_id и атрибуты варианта в позиции заказа (snapshot на момент создания)
ALTER TABLE order_items ADD COLUMN variant_id BIGINT NULL;
ALTER TABLE order_items ADD COLUMN variant_attributes VARCHAR(500) NULL;
