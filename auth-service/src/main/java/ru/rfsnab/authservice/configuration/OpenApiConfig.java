package ru.rfsnab.authservice.configuration;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация OpenAPI (Swagger) для Auth Service
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Auth Service API",
                version = "1.0",
                description = "API для аутентификации и авторизации пользователей (JWT, OAuth2)",
                contact = @Contact(
                        name = "Max",
                        email = "elims777@yandex.ru"
                )
        )
)
@SecurityScheme(
        name = "Bearer Authentication",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class OpenApiConfig {
}