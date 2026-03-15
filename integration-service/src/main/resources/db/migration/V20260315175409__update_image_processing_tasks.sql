-- Дополнение таблиц integration-service: колонки для session tracking и import status

-- image_processing_tasks: добавляем session_id для связи задачи с сессией обмена
ALTER TABLE image_processing_tasks ADD COLUMN IF NOT EXISTS session_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_image_tasks_session ON image_processing_tasks(session_id);

-- import_log: добавляем session_id, status и временные метки начала/окончания
ALTER TABLE import_log ADD COLUMN IF NOT EXISTS session_id VARCHAR(64);
ALTER TABLE import_log ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE import_log ADD COLUMN IF NOT EXISTS started_at TIMESTAMP;
ALTER TABLE import_log ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_import_log_session ON import_log(session_id);