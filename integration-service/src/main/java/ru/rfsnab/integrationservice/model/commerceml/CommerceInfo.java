package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Корневой элемент CommerceML 2.08.
 * Используется и для import.xml (Классификатор + Каталог),
 * и для offers.xml (ПакетПредложений).
 */
@Getter
@Setter
@XmlRootElement(name = "КоммерческаяИнформация")
@XmlAccessorType(XmlAccessType.FIELD)
public class CommerceInfo {

    @XmlAttribute(name = "ВерсияСхемы")
    private String schemaVersion;

    @XmlAttribute(name = "ДатаФормирования")
    private String formationDate;

    /** Присутствует в import.xml */
    @XmlElement(name = "Классификатор")
    private Classifier classifier;

    /** Присутствует в import.xml */
    @XmlElement(name = "Каталог")
    private Catalog catalog;

    /** Присутствует в offers.xml */
    @XmlElement(name = "ПакетПредложений")
    private OffersPackage offersPackage;

    /** Документы (заказы) — для выгрузки в 1С и получения статусов */
    @XmlElement(name = "Документ")
    private List<CmlDocument> documents = new ArrayList<>();
}