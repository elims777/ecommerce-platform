CREATE TABLE payments (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID         NOT NULL UNIQUE,
    operation_id VARCHAR(255),
    payment_link VARCHAR(512),
    amount       NUMERIC(19, 2) NOT NULL,
    status       VARCHAR(20)  NOT NULL
                     CHECK (status IN ('PENDING','APPROVED','FAILED','REFUNDED')),
    payment_mode VARCHAR(10)
                     CHECK (payment_mode IN ('CARD','SBP','CASH')),
    customer_email VARCHAR(255),
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP
);
