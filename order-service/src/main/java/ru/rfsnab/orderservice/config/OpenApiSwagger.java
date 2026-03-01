package ru.rfsnab.orderservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация OpenAPI (Swagger) для Order Service.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Order Service API",
                version = "1.0",
                description = "API для управления заказами, корзиной",
                contact = @Contact(
                        name = "Max",
                        email = "elims777@gmail.com"
                )
        )
)
public class OpenApiSwagger {
}
