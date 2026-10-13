package ru.rfsnab.orderservice;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import ru.rfsnab.orderservice.delivery.CarrierCode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Все миграции Flyway накатываются на чистую PostgreSQL, а Hibernate (ddl-auto=validate) сверяет с ними сущности.
 * Остальные интеграционные тесты строят схему через ddl-auto=create и миграции не проверяют, поэтому здесь
 * отдельная БД: общая с Base уже содержит таблицы, и Flyway упал бы на V1.
 * Kafka и Redis берутся из контейнеров {@link BaseIntegrationTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@SuppressWarnings("resource")
@DisplayName("Миграции Flyway + validate сущностей")
class FlywayMigrationsIntegrationTest {

    private static final PostgreSQLContainer<?> CLEAN_DB = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("order_migrations_db").withUsername("test").withPassword("test");

    static {
        CLEAN_DB.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", CLEAN_DB::getJdbcUrl);
        registry.add("spring.datasource.username", CLEAN_DB::getUsername);
        registry.add("spring.datasource.password", CLEAN_DB::getPassword);
        registry.add("spring.kafka.bootstrap-servers", BaseIntegrationTest.kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", BaseIntegrationTest.redis::getHost);
        registry.add("spring.data.redis.port", () -> BaseIntegrationTest.redis.getMappedPort(6379));
    }

    @Autowired
    private Flyway flyway;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("контекст поднимается, все миграции применены, сид ТК и таблицы этапов 2-4 на месте")
    void migrationsApplyAndEntitiesValidate() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from carriers", Integer.class)).isEqualTo(CarrierCode.values().length);
        assertThat(jdbcTemplate.queryForObject(
                "select tracking_url_template from carriers where code = 'DELLIN'", String.class)).contains("{number}");
        assertThat(jdbcTemplate.queryForObject("select count(*) from carrier_terminals", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint where conname = 'uq_shipments_carrier_waybill'", Integer.class)).isEqualTo(1);
    }
}
