package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

/**
 * Цена товара в рамках предложения.
 * ИдТипаЦены → связь с PriceType (Розничная/Оптовая).
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class OfferPrice {

    /** Текстовое представление: "100 RUB за шт" */
    @XmlElement(name = "Представление")
    private String representation;

    /** UUID типа цены — связь с PriceType.id */
    @XmlElement(name = "ИдТипаЦены")
    private String priceTypeId;

    /** Цена за единицу */
    @XmlElement(name = "ЦенаЗаЕдиницу")
    private String pricePerUnit;

    @XmlElement(name = "Валюта")
    private String currency;

    @XmlElement(name = "Единица")
    private String unit;

    @XmlElement(name = "Коэффициент")
    private String coefficient;
}
