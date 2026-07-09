package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

/**
 * Контакт контрагента — пара "тип → значение".
 * Типы по спецификации CommerceML: "Электронная почта", "Телефон рабочий".
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
