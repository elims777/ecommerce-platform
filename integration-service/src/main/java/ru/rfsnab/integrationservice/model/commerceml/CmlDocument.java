package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Документ CommerceML — заказ.
 * Используется для:
 * - Выгрузки заказов в 1С (mode=query)
 * - Получения обновлённых статусов от 1С (mode=import)
 */
@Getter
@Setter
@XmlAccessorType(value = XmlAccessType.FIELD)
public class CmlDocument {
    /** Наш orderId (UUID) — ключ связи с order-service */
    @XmlElement(name = "Ид")
    private String id;

    /** Номер заказа (наш orderNumber или номер 1С при обратной синхронизации) */
    @XmlElement(name = "Номер")
    private String number;

    @XmlElement(name = "Дата")
    private String date;

    @XmlElement(name = "Валюта")
    private String currency;

    @XmlElement(name = "Сумма")
    private String totalAmount;

    @XmlElement(name = "Комментарий")
    private String comment;

    @XmlElement(name = "Роль")
    private String role;

    @XmlElementWrapper(name = "Контрагенты")
    @XmlElement(name = "Контрагент")
    private List<CmlContragent> contragents = new ArrayList<>();

    @XmlElementWrapper(name = "Товары")
    @XmlElement(name = "Товар")
    private List<CmlOrderProduct> products = new ArrayList<>();

    @XmlElementWrapper(name = "ЗначенияРеквизитов")
    @XmlElement(name = "ЗначениеРеквизита")
    private List<RequisiteValue> requisiteValues = new ArrayList<>();
}
