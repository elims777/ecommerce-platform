-- Автовозобновление ФТК-импорта после рестарта сервиса:
-- счётчик попыток, чтобы воспроизводимый краш не зациклил перезапуски.
ALTER TABLE import_log ADD COLUMN resume_attempts INT NOT NULL DEFAULT 0;
