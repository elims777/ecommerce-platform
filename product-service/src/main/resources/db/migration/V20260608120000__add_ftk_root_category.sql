-- Корневая категория для товаров ФТК (дилерский каталог Факел)
-- Скрыта из основной навигации, но товары из неё видны в каталоге через подкатегории

INSERT INTO categories (name, slug, description, parent_id, is_active, display_order, created_at, updated_at)
VALUES (
    'ФТК — Факел',
    'ftk',
    'Товары дилерского партнёра Факел (спецодежда, СИЗ, рабочая обувь)',
    NULL,
    true,
    99,
    NOW(),
    NOW()
)
ON CONFLICT (slug) DO UPDATE
    SET name        = EXCLUDED.name,
        description = EXCLUDED.description,
        is_active   = true,
        updated_at  = NOW();
