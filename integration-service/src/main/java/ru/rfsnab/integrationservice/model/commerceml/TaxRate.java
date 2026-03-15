package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

/**
 * Ставка налога (НДС).
 * Наименование: "НДС", Ставка: "20", "10", "0", "Без НДС".
 * Маппинг: Ставка → vatRate в product-service.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class TaxRate {

    @XmlElement(name = "Наименование")
    private String name;

    /** Значение ставки: "20", "10", "0", "Без НДС" */
    @XmlElement(name = "Ставка")
    private String rate;
}
