package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Контрагент в документе заказа CommerceML.
 * Формат сверен с реальной выгрузкой из 1С УНФ: реквизиты юрлица и контакты — плоскими тегами.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class CmlContragent {

    @XmlElement(name = "Ид")
    private String id;

    @XmlElement(name = "Наименование")
    private String name;

    @XmlElement(name = "ПолноеНаименование")
    private String fullName;

    /** Только для юрлиц: наличие делает контрагента организацией в 1С. */
    @XmlElement(name = "ОфициальноеНаименование")
    private String officialName;

    @XmlElement(name = "ИНН")
    private String inn;

    @XmlElement(name = "КПП")
    private String kpp;

    @XmlElement(name = "Роль")
    private String role;

    @XmlElement(name = "Фамилия")
    private String lastName;

    @XmlElement(name = "Имя")
    private String firstName;

    @XmlElement(name = "АдресРегистрации")
    private CmlRegistrationAddress registrationAddress;

    /** Email — единственный формат CommerceML, который 1С кладёт в карточку: <Контакты> с Тип=Почта. */
    @XmlElementWrapper(name = "Контакты")
    @XmlElement(name = "Контакт")
    private List<CmlContact> contacts;

    /** Телефон 1С принимает плоским тегом (нестандартная доработка обмена). */
    @XmlElement(name = "Телефон")
    private String phone;
}
