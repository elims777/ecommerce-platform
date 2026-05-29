ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP;
ALTER TABLE users ADD COLUMN unsubscribe_token VARCHAR(36) UNIQUE;
ALTER TABLE users ADD COLUMN last_inactivity_email_at TIMESTAMP;
