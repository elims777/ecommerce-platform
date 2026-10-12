INSERT INTO payment_method_settings (id, sbp_enabled, card_enabled, updated_at)
VALUES (1, false, false, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO carriers (code, name, enabled, address_delivery_enabled, sort_order, updated_at) VALUES ('CDEK', 'СДЭК', true, true, 1, NOW()), ('DELLIN', 'Деловые Линии', true, true, 2, NOW()), ('PEC', 'ПЭК', true, true, 3, NOW()), ('BAIKAL', 'Байкал Сервис', true, true, 4, NOW()), ('VOZOVOZ', 'Возовоз', true, true, 5, NOW()) ON CONFLICT (code) DO NOTHING;
