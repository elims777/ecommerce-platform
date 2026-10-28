package ru.rfsnab.notificationservice.utils;

public final class LogMasker {

    private static final String MASK = "***";

    private LogMasker() {}

    /** a***@mail.ru; значение без «@» целиком заменяется маской. */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at < 1) {
            return MASK;
        }
        return email.charAt(0) + MASK + email.substring(at);
    }
}
