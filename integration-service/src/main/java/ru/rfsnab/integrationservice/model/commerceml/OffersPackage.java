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
 * Пакет предложений из offers.xml — содержит типы цен и сами предложения
 * (цена + остаток для каждого товара).
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class OffersPackage {

    @XmlElement(name = "Ид")
    private String id;

    @XmlElement(name = "Наименование")
    private String name;

    @XmlElementWrapper(name = "ТипыЦен")
    @XmlElement(name = "ТипЦены")
    private List<PriceType> priceTypes = new ArrayList<>();

    @XmlElementWrapper(name = "Предложения")
    @XmlElement(name = "Предложение")
    private List<Offer> offers = new ArrayList<>();
}
