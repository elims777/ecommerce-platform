CREATE TABLE warehouse_points (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    city            VARCHAR(100) NOT NULL,
    street          VARCHAR(150) NOT NULL,
    building        VARCHAR(20) NOT NULL,
    postal_code     VARCHAR(10),
    phone_number    VARCHAR(20) NOT NULL,
    working_hours   VARCHAR(200),
    description     VARCHAR(500),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO warehouse_points(name, city, street, building, postal_code, phone_number,
                             working_hours, description)
VALUES ('Основной склад RFsnab.ru',
        'Сыктывкар',
        'Сысольское шоссе',
        '69',
        '167000',
        '+7(8212)29-69-71',
        '9:00 - 17:00',
        'Основной склад для самовывоза')