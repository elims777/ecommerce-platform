-- ИНН сторон и плательщики отправления; NULL = ТК не сообщает
ALTER TABLE shipments
    ADD COLUMN sender_inn   VARCHAR(12),
    ADD COLUMN receiver_inn VARCHAR(12),
    ADD COLUMN payers       JSONB;

COMMENT ON COLUMN shipments.payers IS '[{name, inn, role}]; role SENDER/RECEIVER/THIRD или NULL (тогда сопоставляется по ИНН/названию)';
