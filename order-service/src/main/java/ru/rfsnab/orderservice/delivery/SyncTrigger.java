package ru.rfsnab.orderservice.delivery;

/**
 * Что запустило синхронизацию терминалов.
 */
public enum SyncTrigger {
    SCHEDULED,
    STARTUP,
    MANUAL
}
