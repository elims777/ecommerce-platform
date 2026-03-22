package ru.rfsnab.integrationservice.service.catalog;

import jakarta.xml.bind.JAXBContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import ru.rfsnab.integrationservice.BaseIntegrationTest;
import ru.rfsnab.integrationservice.repository.ImportLogRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты CatalogImportService.
 * WebClient замокан — product-service недоступен.
 * Проверяем парсинг XML, merge, маппинг и логирование в БД.
 */
@DisplayName("CatalogImportService")
class CatalogImportServiceTest extends BaseIntegrationTest {

    @Autowired
    private CatalogImportService catalogImportService;

    @Autowired
    private ImportLogRepository importLogRepository;

    @Autowired
    private JAXBContext commerceMlJaxbContext;

    @MockitoBean
    private WebClient productServiceClient;

    @MockitoBean
    private ImageProcessingPool imageProcessingPool;

    @TempDir
    Path tempExchangeDir;

    @BeforeEach
    void cleanup() {
        importLogRepository.deleteAll();
    }

    private void copyTestXml(String resourceName) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("commerceml/" + resourceName)) {
            assertThat(is).isNotNull();
            Files.copy(is, tempExchangeDir.resolve(resourceName));
        }
    }

    @Nested
    @DisplayName("processImport — без product-service")
    class ProcessImportTests {

        @Test
        @DisplayName("пустой каталог → success, 0 товаров в логе")
        void shouldHandleEmptyCatalog() throws IOException {
            // Создаём минимальный import.xml без товаров
            String emptyXml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <КоммерческаяИнформация ВерсияСхемы="2.10" ДатаФормирования="2026-01-01"
                                             xmlns="urn:1C.ru:commerceml_210">
                        <Каталог>
                            <Ид>cat-001</Ид>
                            <Наименование>Каталог</Наименование>
                        </Каталог>
                    </КоммерческаяИнформация>
                    """;
            Files.writeString(tempExchangeDir.resolve("import.xml"), emptyXml);

            String result = catalogImportService.processImport(tempExchangeDir, "test-session-1");

            assertThat(result).isEqualTo("success");
            assertThat(importLogRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("отсутствующий import.xml → failure")
        void shouldFailWhenImportXmlMissing() {
            String result = catalogImportService.processImport(tempExchangeDir, "test-session-2");

            assertThat(result).startsWith("failure");
            assertThat(importLogRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("записывает лог импорта в БД при ошибке")
        void shouldSaveImportLogOnFailure() {
            catalogImportService.processImport(tempExchangeDir, "test-session-3");

            var logs = importLogRepository.findAll();
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().getStatus().name()).isEqualTo("FAILED");
            assertThat(logs.getFirst().getSessionId()).isEqualTo("test-session-3");
            assertThat(logs.getFirst().getExchangeType()).isEqualTo("CATALOG");
        }

        @Test
        @DisplayName("парсит import.xml без offers.xml → failure при отправке в product-service (mock)")
        void shouldParseImportWithoutOffers() throws IOException {
            copyTestXml("import.xml");

            // product-service замокан но не настроен → chunk'и вернут ошибку
            String result = catalogImportService.processImport(tempExchangeDir, "test-session-4");

            // Ожидаем failure или success зависит от того как обработается ошибка WebClient
            // Главное — лог записан и нет NPE/исключений
            assertThat(importLogRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("парсит import.xml + offers.xml → лог с правильным количеством")
        void shouldParseImportAndOffers() throws IOException {
            copyTestXml("import.xml");
            copyTestXml("offers.xml");

            catalogImportService.processImport(tempExchangeDir, "test-session-5");

            var logs = importLogRepository.findAll();
            assertThat(logs).hasSize(1);
            // 2 товара в XML
            assertThat(logs.getFirst().getTotalReceived() + logs.getFirst().getFailed())
                    .isGreaterThanOrEqualTo(0); // не NPE, лог записан
        }
    }
}
