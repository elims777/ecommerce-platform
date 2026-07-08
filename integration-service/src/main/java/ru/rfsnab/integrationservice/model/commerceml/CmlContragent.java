package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

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

    @XmlElement(name = "Телефон")
    private String phone;

    @XmlElement(name = "Почта")
    private String email;
}
