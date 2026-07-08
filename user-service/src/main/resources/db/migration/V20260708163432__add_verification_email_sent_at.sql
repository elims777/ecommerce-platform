ALTER TABLE users ADD COLUMN last_verification_email_at TIMESTAMP;
ALTER TABLE legal_entities ADD COLUMN email_confirm_sent_at TIMESTAMP;
