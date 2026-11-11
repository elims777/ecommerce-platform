/** Единый формат телефона на бэкенде — +7XXXXXXXXXX. */

export const PHONE_PREFIX = '+7';

/** 10 цифр после +7 — то, что видит и вводит пользователь. */
export const toPhoneInput = (value?: string | null): string =>
    (value ?? '').replace(/\D/g, '').slice(-10);

/** Значение для API: +7 + 10 цифр. */
export const toPhoneValue = (value?: string | null): string =>
    PHONE_PREFIX + toPhoneInput(value);
