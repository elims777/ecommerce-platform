package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

/**
 * Тип цены: Розничная, Оптовая и т.д.
 * Связывается с ценами в предложениях через ИдТипаЦены.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class PriceType {

    @XmlElement(name = "Ид")
    private String id;

    @XmlElement(name = "Наименование")
    private String name;

    @XmlElement(name = "Валюта")
    private String currency;
}
