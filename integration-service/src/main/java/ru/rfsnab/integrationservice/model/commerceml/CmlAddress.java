package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

/**
 * Адрес контрагента — пара "тип → представление".
 * Тип "Email"/"Телефон" — та же группа полей, что "Юр. адрес"/"Факт. адрес" в карточке контрагента УНФ.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class CmlAddress {

    @XmlElement(name = "Тип")
    private String type;

    @XmlElement(name = "Представление")
    private String representation;
}