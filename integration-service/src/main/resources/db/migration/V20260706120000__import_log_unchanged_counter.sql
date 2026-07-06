-- Честный счётчик "обновлено": товары без изменений значимых полей
-- при повторном импорте больше не считаются как updated.
ALTER TABLE import_log ADD COLUMN unchanged INT NOT NULL DEFAULT 0;
