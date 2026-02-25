ALTER TABLE orders ADD COLUMN customer_email VARCHAR(255);
UPDATE orders SET customer_email = 'unknown@example.com' WHERE customer_email IS NULL;
ALTER TABLE orders ALTER COLUMN customer_email SET NOT NULL;