INSERT INTO payment_method_settings (id, sbp_enabled, card_enabled, updated_at)
VALUES (1, false, false, NOW())
ON CONFLICT (id) DO NOTHING;
