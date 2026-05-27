package ru.rfsnab.integrationservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "Basic Authentication";

        return new OpenAPI()
                .info(new Info()
                        .title("Integration Service API")
                        .version("1.0.0")
                        .description("Сервис интеграции с 1С Fresh УНФ по протоколу CommerceML 2.08. "
                                + "Обмен каталогом товаров, ценами, остатками, заказами и статусами.")
                        .contact(new Contact()
                                .name("Max")
                                .email("elims777@gmail.com")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")));
    }
}
