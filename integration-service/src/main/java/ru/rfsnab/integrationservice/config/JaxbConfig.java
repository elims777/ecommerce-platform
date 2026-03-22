package ru.rfsnab.integrationservice.config;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.rfsnab.integrationservice.model.commerceml.CommerceInfo;

@Configuration
public class JaxbConfig {

    @Bean
    public JAXBContext commerceMLJaxbContext() throws JAXBException {
        return JAXBContext.newInstance(CommerceInfo.class);
    }
}
