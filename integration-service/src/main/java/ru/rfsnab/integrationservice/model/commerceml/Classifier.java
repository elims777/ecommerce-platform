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
 * Классификатор — содержит дерево групп (категорий) товаров.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class Classifier {

    @XmlElement(name = "Ид")
    private String id;

    @XmlElement(name = "Наименование")
    private String name;

    /** Дерево групп (категорий). Каждая группа может содержать вложенные группы. */
    @XmlElementWrapper(name = "Группы")
    @XmlElement(name = "Группа")
    private List<Group> groups = new ArrayList<>();
}