package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

/**
 * Блок <АдресРегистрации> контрагента в orders.xml — адрес строкой.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class CmlRegistrationAddress {

    @XmlElement(name = "Представление")
    private String representation;
}
