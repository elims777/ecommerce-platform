package ru.rfsnab.orderservice;


import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Базовый класс для интеграционных тестов.
 * Поднимает PostgreSQL, Kafka, Redis через Testcontainers.
 * Контейнеры static — один набор на все тесты (lifecycle per JVM).
 */
@SpringBootTest
@SuppressWarnings("resource")
@ActiveProfiles("test")
public class BaseIntegrationTest {

    protected static final PostgreSQLContainer<?> postgres;
    protected static final KafkaContainer kafka;
    protected static final GenericContainer<?> redis;

    static {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("order_test_db")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
        kafka.start();

        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry){
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
