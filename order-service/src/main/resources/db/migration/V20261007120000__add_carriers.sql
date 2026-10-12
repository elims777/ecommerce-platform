CREATE TABLE carriers (
    code                     VARCHAR(20)  PRIMARY KEY,
    name                     VARCHAR(100) NOT NULL,
    enabled                  BOOLEAN      NOT NULL DEFAULT FALSE,
    address_delivery_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order               INT          NOT NULL,
    updated_at               TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO carriers (code, name, enabled, address_delivery_enabled, sort_order) VALUES
    ('CDEK',    'СДЭК',          TRUE, TRUE, 1),
    ('DELLIN',  'Деловые Линии', TRUE, TRUE, 2),
    ('PEC',     'ПЭК',           TRUE, TRUE, 3),
    ('BAIKAL',  'Байкал Сервис', TRUE, TRUE, 4),
    ('VOZOVOZ', 'Возовоз',       TRUE, TRUE, 5);

-- carrier_name — снимок carriers.name на момент заказа
ALTER TABLE orders
    ADD COLUMN carrier_code VARCHAR(20) REFERENCES carriers(code),
    ADD COLUMN carrier_name VARCHAR(100);

ALTER TABLE warehouse_points
    ADD COLUMN latitude  NUMERIC(9, 6),
    ADD COLUMN longitude NUMERIC(9, 6);
