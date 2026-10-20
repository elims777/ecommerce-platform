package ru.rfsnab.orderservice.delivery.models.entity.enums;

/**
 * Что запустило синхронизацию терминалов.
 */
public enum SyncTrigger {
    SCHEDULED,
    STARTUP,
    MANUAL
}
