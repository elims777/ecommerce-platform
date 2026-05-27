package ru.rfsnab.integrationservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Integration Service")
class IntegrationServiceApplicationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("контекст приложения загружается")
    void contextLoads() {
        // Проверяем что Spring контекст поднимается без ошибок
        // с Testcontainers PostgreSQL
    }
}
