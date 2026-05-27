package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

/**
 * Значение реквизита товара — пара "наименование → значение".
 * Примеры: ВидНоменклатуры=Товар, ТипНоменклатуры=Запас.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class RequisiteValue {

    @XmlElement(name = "Наименование")
    private String name;

    @XmlElement(name = "Значение")
    private String value;
}
