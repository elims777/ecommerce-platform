package ru.rfsnab.integrationservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Integration Service")
class IntegrationServiceApplicationTest extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("контекст приложения загружается")
    void contextLoads() {
        // Spring контекст поднимается без ошибок с Testcontainers PostgreSQL
        assertThat(context).isNotNull();
    }
}
