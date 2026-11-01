package ru.rfsnab.authservice.utils;

/**
 * Маскирует email для логов: ivan@mail.ru → i***@mail.ru
 */
public final class EmailMasker {

    private static final String MASK = "***";

    private EmailMasker() {
    }

    public static String mask(String email) {
        if (email == null) {
            return MASK;
        }
        int at = email.indexOf('@');
        return at < 1 ? MASK : email.charAt(0) + MASK + email.substring(at);
    }
}
