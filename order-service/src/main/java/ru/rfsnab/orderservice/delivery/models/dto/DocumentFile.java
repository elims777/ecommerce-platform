package ru.rfsnab.orderservice.delivery.models.dto;

/** Скачанный документ: имя файла и содержимое. */
public record DocumentFile(String fileName, byte[] content) {
}
