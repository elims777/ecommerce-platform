INSERT INTO payment_method_settings (id, sbp_enabled, card_enabled, updated_at)
VALUES (1, false, false, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO carriers (code, name, enabled, address_delivery_enabled, terminal_delivery_enabled, active_terminals_count, sort_order, updated_at) VALUES ('CDEK', 'СДЭК', true, true, false, 0, 1, NOW()), ('DELLIN', 'Деловые Линии', true, true, false, 0, 2, NOW()), ('PEC', 'ПЭК', true, true, false, 0, 3, NOW()), ('BAIKAL', 'Байкал Сервис', true, true, false, 0, 4, NOW()), ('VOZOVOZ', 'Возовоз', true, true, false, 0, 5, NOW()) ON CONFLICT (code) DO NOTHING;
