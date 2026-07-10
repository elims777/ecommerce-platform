package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Товарная позиция в документе заказа CommerceML.
 * Порядок тегов по стандарту CommerceML 2.10:
 * Ид → Артикул → Наименование → Группы → БазоваяЕдиница → ЦенаЗаЕдиницу → Количество → Сумма.
 * 1С сопоставляет номенклатуру по Ид, при отсутствии — по Артикулу.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"id", "sku", "name", "groupIds", "baseUnit", "pricePerUnit", "quantity", "subtotal"})
public class CmlOrderProduct {

    /** ExternalId товара из 1С (GUID номенклатуры) либо FTK-{article} */
    @XmlElement(name = "Ид")
    private String id;

    /** Артикул — fallback-сопоставление и данные для создания номенклатуры в 1С */
    @XmlElement(name = "Артикул")
    private String sku;

    @XmlElement(name = "Наименование")
    private String name;

    /**
     * ID групп (категорий), к которым принадлежит товар.
     * В CommerceML: Группы → Ид (список UUID'ов, не вложенные объекты).
     * null по умолчанию (не пустой список) — иначе JAXB выводит пустой тег &lt;Группы/&gt;.
     */
    @XmlElementWrapper(name = "Группы")
    @XmlElement(name = "Ид")
    private List<String> groupIds;

    @XmlElement(name = "БазоваяЕдиница")
    private BaseUnit baseUnit;

    @XmlElement(name = "ЦенаЗаЕдиницу")
    private String pricePerUnit;

    @XmlElement(name = "Количество")
    private String quantity;

    @XmlElement(name = "Сумма")
    private String subtotal;
}
