-- Реструктуризация категорий верхнего уровня
-- 3 главных + 3 дополнительных согласно новой структуре каталога

-- ── Главные категории (display_order 1-3) ─────────────────────

-- 1. Противопожарное оборудование — уже есть, обновляем название и порядок
UPDATE categories
SET name         = 'Противопожарное оборудование',
    slug         = 'protivopozharnoe-oborudovanie',
    description  = 'Огнетушители, пожарные рукава, шкафы, модули пожаротушения, средства эвакуации',
    display_order = 1,
    is_active    = true,
    updated_at   = NOW()
WHERE slug = 'protivopozharnoe-oborudovanie';

-- 2. Спецодежда и СИЗ — объединяем «Средства индивидуальной защиты» и «Спецодежда и спецобувь»
--    Оставляем запись с id=2 (siz), переименовываем, деактивируем id=5 (specodezhda)
UPDATE categories
SET name         = 'Спецодежда и СИЗ',
    slug         = 'specodezhda-i-siz',
    description  = 'Спецодежда, спецобувь, СИЗ органов дыхания, головы, рук, глаз, слуха',
    display_order = 2,
    is_active    = true,
    updated_at   = NOW()
WHERE slug = 'siz';

UPDATE categories SET is_active = false, updated_at = NOW() WHERE slug = 'specodezhda-i-specobuv';

-- Перепривязываем товары из категории спецодежды к новой объединённой
UPDATE categories c
SET parent_id = (SELECT id FROM categories WHERE slug = 'specodezhda-i-siz')
WHERE c.parent_id = (SELECT id FROM categories WHERE slug = 'specodezhda-i-specobuv');

UPDATE products
SET category_id = (SELECT id FROM categories WHERE slug = 'specodezhda-i-siz'),
    updated_at  = NOW()
WHERE category_id = (SELECT id FROM categories WHERE slug = 'specodezhda-i-specobuv'
                     LIMIT 1);

-- 3. Медицинские товары и косметология — объединяем «Медицинские товары» и «Дерматологические СИЗ»
UPDATE categories
SET name         = 'Медицинские товары и косметология',
    slug         = 'medicinskie-tovary-i-kosmetologiya',
    description  = 'Медицинские перчатки, перевязочные материалы, антисептики, дезинфекция, защитные кремы',
    display_order = 3,
    is_active    = true,
    updated_at   = NOW()
WHERE slug = 'medicinskie-tovary';

UPDATE categories SET is_active = false, updated_at = NOW() WHERE slug = 'dermatologicheskie-siz';

UPDATE categories c
SET parent_id = (SELECT id FROM categories WHERE slug = 'medicinskie-tovary-i-kosmetologiya')
WHERE c.parent_id = (SELECT id FROM categories WHERE slug = 'dermatologicheskie-siz');

UPDATE products
SET category_id = (SELECT id FROM categories WHERE slug = 'medicinskie-tovary-i-kosmetologiya'),
    updated_at  = NOW()
WHERE category_id = (SELECT id FROM categories WHERE slug = 'dermatologicheskie-siz' LIMIT 1);

-- Деактивируем остальные старые категории верхнего уровня которые не вошли в новую структуру
UPDATE categories SET is_active = false, updated_at = NOW()
WHERE slug IN ('go-i-chs', 'bytovaya-himiya')
  AND parent_id IS NULL;

-- ── Дополнительные категории (display_order 4-6) ──────────────

INSERT INTO categories (name, slug, description, parent_id, is_active, display_order, created_at, updated_at)
VALUES
    ('Металлическая мебель и инструмент', 'metallicheskaya-mebel-i-instrument',
     'Стеллажи, шкафы металлические, верстаки, ручной и электроинструмент',
     NULL, true, 4, NOW(), NOW()),

    ('Безопасность и маркировка', 'bezopasnost-i-markirovka',
     'Знаки безопасности, световые табло, ограждения, сигнальные ленты, разметка',
     NULL, true, 5, NOW(), NOW()),

    ('Расходники и прочее', 'raskhodniki-i-prochee',
     'Батарейки, крепёж, упаковочные материалы, хозяйственные товары',
     NULL, true, 6, NOW(), NOW())
ON CONFLICT (slug) DO UPDATE
    SET name         = EXCLUDED.name,
        description  = EXCLUDED.description,
        display_order = EXCLUDED.display_order,
        is_active    = true,
        updated_at   = NOW();
