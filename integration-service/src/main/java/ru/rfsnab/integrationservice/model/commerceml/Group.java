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
 * Группа (категория) товаров — рекурсивная: может содержать дочерние группы.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class Group {

    @XmlElement(name = "Ид")
    private String id;

    @XmlElement(name = "Наименование")
    private String name;

    /** Вложенные подгруппы (recursive tree structure) */
    @XmlElementWrapper(name = "Группы")
    @XmlElement(name = "Группа")
    private List<Group> subGroups = new ArrayList<>();
}