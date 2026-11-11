-- Единый формат телефона: +7XXXXXXXXXX (см. @Pattern в DTO).
-- Телефоны складов (warehouse_points.phone_number) и ТК (carrier_terminals.phone) не трогаем — это не номера клиентов.
UPDATE recipients SET phone = '+7' || right(regexp_replace(phone, '\D', '', 'g'), 10)
WHERE regexp_replace(phone, '\D', '', 'g') ~ '^(7|8)?\d{10}$';

UPDATE orders SET customer_phone = '+7' || right(regexp_replace(customer_phone, '\D', '', 'g'), 10)
WHERE regexp_replace(customer_phone, '\D', '', 'g') ~ '^(7|8)?\d{10}$';

UPDATE orders SET pickup_recipient_phone = '+7' || right(regexp_replace(pickup_recipient_phone, '\D', '', 'g'), 10)
WHERE regexp_replace(pickup_recipient_phone, '\D', '', 'g') ~ '^(7|8)?\d{10}$';

UPDATE orders SET delivery_phone = '+7' || right(regexp_replace(delivery_phone, '\D', '', 'g'), 10)
WHERE regexp_replace(delivery_phone, '\D', '', 'g') ~ '^(7|8)?\d{10}$';
