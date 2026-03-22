/**
 * JAXB-маппинг для формата CommerceML 2.08 (1С Fresh УНФ).
 * Namespace: urn:1C.ru:commerceml_210, кириллические XML-теги.
 */
@XmlSchema(
        namespace = "urn:1C.ru:commerceml_210",
        elementFormDefault = XmlNsForm.QUALIFIED,
        xmlns = {
                @XmlNs(prefix = "", namespaceURI = "urn:1C.ru:commerceml_210")
        }
)
package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.annotation.XmlNs;
import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;