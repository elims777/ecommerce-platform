package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlValue;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовая единица измерения товара.
 * Пример XML: <БазоваяЕдиница Код="796" НаименованиеПолное="Штука" МеждународноеСокращение="PCE">шт</БазоваяЕдиница>
 * Маппинг: value (текстовое содержимое) → unitOfMeasure в product-service.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class BaseUnit {

    /** Код по ОКЕИ (Общероссийский классификатор единиц измерения) */
    @XmlAttribute(name = "Код")
    private String code;

    @XmlAttribute(name = "НаименованиеПолное")
    private String fullName;

    @XmlAttribute(name = "МеждународноеСокращение")
    private String internationalAbbreviation;

    /** Краткое наименование: "шт", "кг", "л" и т.д. */
    @XmlValue
    private String value;
}
