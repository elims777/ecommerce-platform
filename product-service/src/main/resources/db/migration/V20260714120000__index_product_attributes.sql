-- Ускоряет агрегацию фасетов (DISTINCT по name/value) и EXISTS-фильтр листинга.
CREATE INDEX IF NOT EXISTS idx_product_attributes_name_value
    ON product_attributes (attribute_name, attribute_value);
