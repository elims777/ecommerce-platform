package ru.rfsnab.orderservice.models.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderDocumentType {
    COMMERCIAL_OFFER("Коммерческое предложение"),
    INVOICE("Счёт"),
    UPD("УПД"),
    CERTIFICATE("Сертификат");

    private final String displayName;
}
