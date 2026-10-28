ALTER TABLE carts
    ADD COLUMN IF NOT EXISTS customer_type VARCHAR(10) NOT NULL DEFAULT 'B2C';

ALTER TABLE carts
    ADD CONSTRAINT carts_customer_type_check CHECK (customer_type IN ('B2C', 'B2B'));

ALTER TABLE carts DROP CONSTRAINT IF EXISTS carts_user_id_key;

ALTER TABLE carts
    ADD CONSTRAINT carts_user_id_customer_type_key UNIQUE (user_id, customer_type);
