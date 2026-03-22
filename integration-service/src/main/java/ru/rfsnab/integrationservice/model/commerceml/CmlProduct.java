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
 * Товар из каталога CommerceML.
 * Префикс Cml — чтобы не конфликтовать с domain entity Product.
 *
 * Маппинг полей:
 * - Ид → externalId
 * - Артикул → sku
 * - Наименование → name
 * - Описание → shortDescription (зона 1С)
 * - БазоваяЕдиница → unitOfMeasure
 * - Картинка → список путей к изображениям для обработки
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class CmlProduct {

    @XmlElement(name = "Ид")
    private String id;

    @XmlElement(name = "Наименование")
    private String name;

    @XmlElement(name = "Артикул")
    private String sku;

    /** Описание из 1С → shortDescription в product-service */
    @XmlElement(name = "Описание")
    private String description;

    @XmlElement(name = "БазоваяЕдиница")
    private BaseUnit baseUnit;

    /**
     * ID групп (категорий), к которым принадлежит товар.
     * В CommerceML: Группы → Ид (список UUID'ов, не вложенные объекты).
     */
    @XmlElementWrapper(name = "Группы")
    @XmlElement(name = "Ид")
    private List<String> groupIds = new ArrayList<>();

    /**
     * Пути к картинкам (относительно каталога обмена).
     * Может быть несколько элементов Картинка.
     */
    @XmlElement(name = "Картинка")
    private List<String> images = new ArrayList<>();

    /** Реквизиты: ВидНоменклатуры, ТипНоменклатуры и др. */
    @XmlElementWrapper(name = "ЗначенияРеквизитов")
    @XmlElement(name = "ЗначениеРеквизита")
    private List<RequisiteValue> requisiteValues = new ArrayList<>();
}
