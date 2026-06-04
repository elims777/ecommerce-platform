ALTER TABLE users ADD CONSTRAINT users_phone_unique UNIQUE (phone);
ALTER TABLE users ADD COLUMN newsletter_consent BOOLEAN NOT NULL DEFAULT FALSE;
