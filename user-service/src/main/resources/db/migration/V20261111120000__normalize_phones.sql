-- Единый формат телефона: +7XXXXXXXXXX (см. @Pattern в DTO).
-- Не-российские номера (не 10/11 цифр с 7/8) не трогаем.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_phone_unique;

UPDATE users SET phone = '+7' || right(regexp_replace(phone, '\D', '', 'g'), 10)
WHERE regexp_replace(phone, '\D', '', 'g') ~ '^(7|8)?\d{10}$';

UPDATE legal_entities SET phone = '+7' || right(regexp_replace(phone, '\D', '', 'g'), 10)
WHERE regexp_replace(phone, '\D', '', 'g') ~ '^(7|8)?\d{10}$';

UPDATE user_addresses SET phone = '+7' || right(regexp_replace(phone, '\D', '', 'g'), 10)
WHERE regexp_replace(phone, '\D', '', 'g') ~ '^(7|8)?\d{10}$';

ALTER TABLE users ADD CONSTRAINT users_phone_unique UNIQUE (phone);
