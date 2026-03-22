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
 * Каталог — контейнер для списка товаров из import.xml.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class Catalog {

    @XmlElement(name = "Ид")
    private String id;

    @XmlElement(name = "Наименование")
    private String name;

    @XmlElementWrapper(name = "Товары")
    @XmlElement(name = "Товар")
    private List<CmlProduct> products = new ArrayList<>();
}
