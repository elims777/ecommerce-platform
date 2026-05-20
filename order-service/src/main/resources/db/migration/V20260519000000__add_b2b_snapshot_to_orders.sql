ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS customer_type VARCHAR(10) NOT NULL DEFAULT 'B2C',
    ADD COLUMN IF NOT EXISTS company_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS inn           VARCHAR(12);

ALTER TABLE orders
    ADD CONSTRAINT orders_customer_type_check
    CHECK (customer_type IN ('B2C', 'B2B'));
