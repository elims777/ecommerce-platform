ALTER TABLE carriers
    ADD COLUMN terminal_delivery_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN terminals_synced_at       TIMESTAMPTZ,
    ADD COLUMN last_sync_report          JSONB,
    ADD COLUMN active_terminals_count    INT         NOT NULL DEFAULT 0;

CREATE TABLE carrier_terminals (
    id           BIGSERIAL        PRIMARY KEY,
    carrier_code VARCHAR(20)      NOT NULL REFERENCES carriers(code),
    external_id  VARCHAR          NOT NULL,
    point_type   VARCHAR(20)      NOT NULL,
    city         VARCHAR          NOT NULL,
    name         VARCHAR          NOT NULL,
    address      VARCHAR          NOT NULL,
    latitude     NUMERIC(9, 6)    NOT NULL,
    longitude    NUMERIC(9, 6)    NOT NULL,
    working_hours VARCHAR,
    phone        VARCHAR,
    active       BOOLEAN          NOT NULL DEFAULT TRUE,
    synced_at    TIMESTAMPTZ      NOT NULL,
    CONSTRAINT uq_carrier_terminals_carrier_external UNIQUE (carrier_code, external_id)
);

-- подбор точек города по ТК и типу: только активные
CREATE INDEX idx_carrier_terminals_active_lookup
    ON carrier_terminals (carrier_code, city, point_type) WHERE active;

-- terminal_name / terminal_address — снимок на момент заказа (из БД по id, не из запроса)
ALTER TABLE orders
    ADD COLUMN carrier_terminal_id BIGINT REFERENCES carrier_terminals(id),
    ADD COLUMN terminal_name       VARCHAR,
    ADD COLUMN terminal_address    VARCHAR;
