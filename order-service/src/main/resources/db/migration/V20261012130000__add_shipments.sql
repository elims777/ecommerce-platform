-- Ссылка «Отследить»: {number} заменяется номером накладной. У Байкала и Возовоза NULL: URL не сверен, задаётся из админки
ALTER TABLE carriers
    ADD COLUMN tracking_url_template VARCHAR(500);

UPDATE carriers SET tracking_url_template = 'https://www.cdek.ru/ru/tracking?order_id={number}' WHERE code = 'CDEK';
UPDATE carriers SET tracking_url_template = 'https://www.dellin.ru/tracker/orders/{number}/'    WHERE code = 'DELLIN';
UPDATE carriers SET tracking_url_template = 'https://pecom.ru/services-are/shipping-status/?code={number}' WHERE code = 'PEC';

ALTER TABLE orders
    ADD COLUMN shipment_mode      VARCHAR(10),
    ADD COLUMN shipments_complete BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS orders_status_check;

ALTER TABLE orders
    ADD CONSTRAINT orders_status_check CHECK (
        status IN (
            'CREATED',
            'PROCESSING',
            'INVOICE_SENT',
            'PENDING_PAYMENT',
            'AWAITING_CONFIRMATION',
            'PAID',
            'PARTIALLY_PAID',
            'PAYMENT_FAILED',
            'SHIPPED',
            'IN_TRANSIT',
            'ARRIVED_AT_TERMINAL',
            'DELIVERED',
            'CANCELLED',
            'REFUNDED',
            'COMPLETED'
        )
    );

CREATE TABLE shipments (
    id                        BIGSERIAL    PRIMARY KEY,
    order_id                  UUID         REFERENCES orders(id),
    carrier_code              VARCHAR(20)  NOT NULL REFERENCES carriers(code),
    waybill_number            VARCHAR(100) NOT NULL,
    direction                 VARCHAR(10)  NOT NULL,
    sender                    VARCHAR,
    receiver                  VARCHAR,
    route_from                VARCHAR,
    route_to                  VARCHAR,
    shipped_at                TIMESTAMPTZ,
    estimated_delivery_at     TIMESTAMPTZ,
    carrier_status            VARCHAR,
    carrier_status_code       VARCHAR(30),
    carrier_status_updated_at TIMESTAMPTZ  NOT NULL,
    polled_at                 TIMESTAMPTZ,
    polling_stopped           BOOLEAN      NOT NULL DEFAULT FALSE,
    not_found_since           TIMESTAMPTZ,
    not_found_notified        BOOLEAN      NOT NULL DEFAULT FALSE,
    source                    VARCHAR(10)  NOT NULL,
    created_by                BIGINT,
    created_at                TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_shipments_carrier_waybill UNIQUE (carrier_code, waybill_number)
);

-- фильтры журнала и выборка отправлений заказа
CREATE INDEX idx_shipments_order ON shipments (order_id);
CREATE INDEX idx_shipments_status_code ON shipments (carrier_status_code);
CREATE INDEX idx_shipments_created_at ON shipments (created_at DESC);
-- отбор для опроса: только живые отправления
CREATE INDEX idx_shipments_polling ON shipments (carrier_code) WHERE NOT polling_stopped;
