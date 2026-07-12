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
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class CmlContragent {

    @XmlElement(name = "Ид")
    private String id;

    @XmlElement(name = "Наименование")
    private String name;

    @XmlElement(name = "Роль")
    private String role;

    @XmlElement(name = "ПолноеНаименование")
    private String fullName;

    @XmlElement(name = "Фамилия")
    private String lastName;

    @XmlElement(name = "Имя")
    private String firstName;

    @XmlElement(name = "ИНН")
    private String inn;

    @XmlElement(name = "ПочтовыйАдрес")
    private String postalAddress;

    @XmlElementWrapper(name = "Контакты")
    @XmlElement(name = "Контакт")
    private List<CmlContact> contacts;

    @XmlElementWrapper(name = "Адреса")
    @XmlElement(name = "Адрес")
    private List<CmlAddress> addresses;
}
