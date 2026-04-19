package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

/**
 * Товарная позиция в документе заказа CommerceML.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class CmlOrderProduct {

    /** ExternalId товара из 1С */
    @XmlElement(name = "Ид")
    private String id;

    @XmlElement(name = "Наименование")
    private String name;

    @XmlElement(name = "Количество")
    private String quantity;

    @XmlElement(name = "ЦенаЗаЕдиницу")
    private String pricePerUnit;

    @XmlElement(name = "Сумма")
    private String subtotal;

    @XmlElement(name = "ИдКаталога")
    private String catalogId;

    @XmlElement(name = "БазоваяЕдиница")
    private String baseUnit;
}
