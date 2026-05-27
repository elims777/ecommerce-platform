ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS pickup_recipient_name  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pickup_recipient_phone VARCHAR(20);

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
            'PAYMENT_FAILED',
            'SHIPPED',
            'IN_TRANSIT',
            'DELIVERED',
            'CANCELLED',
            'REFUNDED'
        )
    );
