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
 * Предложение — привязка цены и остатков к товару.
 * Ид совпадает с Ид товара из import.xml → по нему джойним данные.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class Offer {

    /** Совпадает с CmlProduct.id — ключ для объединения import.xml + offers.xml */
    @XmlElement(name = "Ид")
    private String id;

    @XmlElement(name = "Наименование")
    private String name;

    @XmlElementWrapper(name = "Цены")
    @XmlElement(name = "Цена")
    private List<OfferPrice> prices = new ArrayList<>();

    /** Остаток на складе */
    @XmlElement(name = "Количество")
    private String quantity;

    @XmlElementWrapper(name = "СтавкиНалогов")
    @XmlElement(name = "СтавкаНалога")
    private List<TaxRate> taxRates = new ArrayList<>();
}
