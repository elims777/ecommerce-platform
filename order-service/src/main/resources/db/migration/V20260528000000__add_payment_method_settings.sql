CREATE TABLE payment_method_settings (
    id           BIGINT PRIMARY KEY DEFAULT 1,
    sbp_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    card_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO payment_method_settings (id, sbp_enabled, card_enabled, updated_at)
VALUES (1, false, false, NOW());
