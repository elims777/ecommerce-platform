-- Груз и детали отправления для строки журнала; NULL = ТК не сообщает
ALTER TABLE shipments
    ADD COLUMN weight_kg    NUMERIC(12, 3),
    ADD COLUMN volume_m3    NUMERIC(12, 4),
    ADD COLUMN places_count INTEGER,
    ADD COLUMN cost         NUMERIC(12, 2),
    ADD COLUMN paid         BOOLEAN,
    ADD COLUMN from_address BOOLEAN,
    ADD COLUMN to_address   BOOLEAN,
    ADD COLUMN delivered_at TIMESTAMPTZ,
    ADD COLUMN documents    JSONB,
    ADD COLUMN packages     JSONB;

COMMENT ON COLUMN shipments.from_address IS 'true = забор от адреса, false = от терминала/ПВЗ';
COMMENT ON COLUMN shipments.to_address IS 'true = доставка до адреса, false = до терминала/ПВЗ';
COMMENT ON COLUMN shipments.documents IS '[{code, name, ref}]; ref — URL или UID документа в ТК, на фронт не отдаётся';
COMMENT ON COLUMN shipments.packages IS '[{number, weightKg, lengthCm, widthCm, heightCm}]';
