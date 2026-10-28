ALTER TABLE recipients
    ADD COLUMN IF NOT EXISTS customer_type VARCHAR(10) NOT NULL DEFAULT 'B2C';

ALTER TABLE recipients
    ADD CONSTRAINT recipients_customer_type_check CHECK (customer_type IN ('B2C', 'B2B'));

DROP INDEX IF EXISTS idx_recipients_user_id;

CREATE INDEX idx_recipients_user_id_customer_type ON recipients(user_id, customer_type);
