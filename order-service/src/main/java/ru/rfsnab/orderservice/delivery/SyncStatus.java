package ru.rfsnab.orderservice.delivery;

/**
 * Итог синхронизации терминалов одной ТК.
 */
public enum SyncStatus {
    OK,
    /** Были пропущенные записи или подозрительный ответ ТК. */
    WARNING,
    /** ТК ответила ошибкой или пустым ответом — данные не тронуты. */
    ERROR,
    /** Ключ API в env не задан — синхронизация не выполнялась. */
    NOT_CONFIGURED
}
