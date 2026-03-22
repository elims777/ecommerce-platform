package ru.rfsnab.integrationservice.service.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import ru.rfsnab.integrationservice.BaseIntegrationTest;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.model.ImportLog;
import ru.rfsnab.integrationservice.repository.ImportLogRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderStatusImportService")
class OrderStatusImportServiceTest extends BaseIntegrationTest {

    @Autowired
    private OrderStatusImportService orderStatusImportService;

    @Autowired
    private ImportLogRepository importLogRepository;

    @Autowired
    private IntegrationProperties properties;

    @MockitoBean(name = "orderServiceClient")
    private WebClient orderServiceClient;

    @BeforeEach
    void cleanup() throws IOException {
        importLogRepository.deleteAll();
        // Очищаем temp dir
        Path tempDir = Path.of(properties.getCommerceml().getTempDir());
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .filter(Files::isRegularFile)
                    .forEach(f -> {
                        try { Files.deleteIfExists(f); } catch (IOException ignored) {}
                    });
        } else {
            Files.createDirectories(tempDir);
        }
    }

    private void writeOrdersXml(String content) throws IOException {
        Path tempDir = Path.of(properties.getCommerceml().getTempDir());
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("orders.xml"), content);
    }

    @Nested
    @DisplayName("processStatusUpdate")
    class ProcessStatusTests {

        @Test
        @DisplayName("success когда orders.xml отсутствует")
        void shouldReturnSuccessWhenNoFile() {
            String result = orderStatusImportService.processStatusUpdate();

            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("success когда XML без документов")
        void shouldReturnSuccessWhenEmptyXml() throws IOException {
            writeOrdersXml("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <КоммерческаяИнформация ВерсияСхемы="2.10" ДатаФормирования="2026-03-20"
                                             xmlns="urn:1C.ru:commerceml_210">
                    </КоммерческаяИнформация>
                    """);

            String result = orderStatusImportService.processStatusUpdate();

            assertThat(result).isEqualTo("success");
            assertThat(importLogRepository.count()).isEqualTo(1);

            ImportLog log = importLogRepository.findAll().getFirst();
            assertThat(log.getExchangeType()).isEqualTo("ORDER_STATUS");
            assertThat(log.getStatus()).isEqualTo(ImportLog.ImportStatus.SUCCESS);
        }

        @Test
        @DisplayName("записывает import_log при обработке")
        void shouldSaveImportLog() throws IOException {
            // XML с документом, но order-service замокан — упадёт при PATCH
            writeOrdersXml("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <КоммерческаяИнформация ВерсияСхемы="2.10" ДатаФормирования="2026-03-20"
                                             xmlns="urn:1C.ru:commerceml_210">
                        <Документ>
                            <Ид>550e8400-e29b-41d4-a716-446655440000</Ид>
                            <Номер>РФ-000123</Номер>
                            <Дата>2026-03-20</Дата>
                            <ЗначенияРеквизитов>
                                <ЗначениеРеквизита>
                                    <Наименование>Статус заказа</Наименование>
                                    <Значение>PROCESSING</Значение>
                                </ЗначениеРеквизита>
                            </ЗначенияРеквизитов>
                        </Документ>
                    </КоммерческаяИнформация>
                    """);

            // order-service замокан без настройки → упадёт, но лог запишется
            String result = orderStatusImportService.processStatusUpdate();

            assertThat(importLogRepository.count()).isEqualTo(1);
            ImportLog log = importLogRepository.findAll().getFirst();
            assertThat(log.getExchangeType()).isEqualTo("ORDER_STATUS");
            assertThat(log.getTotalReceived()).isEqualTo(1);
        }

        @Test
        @DisplayName("failure при невалидном XML")
        void shouldReturnFailureOnInvalidXml() throws IOException {
            writeOrdersXml("это не XML вообще");

            String result = orderStatusImportService.processStatusUpdate();

            assertThat(result).startsWith("failure");
            assertThat(importLogRepository.count()).isEqualTo(1);

            ImportLog log = importLogRepository.findAll().getFirst();
            assertThat(log.getStatus()).isEqualTo(ImportLog.ImportStatus.FAILED);
        }
    }
}
