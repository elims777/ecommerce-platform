package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

/**
 * Контакт контрагента (CommerceML): пара Тип/Значение внутри блока <Контакты>.
 * Используется для email (Тип=Почта). Телефон 1С принимает плоским тегом <Телефон>.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class CmlContact {

    @XmlElement(name = "Тип")
    private String type;

    @XmlElement(name = "Значение")
    private String value;
}
