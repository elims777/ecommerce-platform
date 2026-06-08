package ru.rfsnab.integrationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "integration")
@Getter
@Setter
public class IntegrationProperties {

    private CommercemlProperties commerceml = new CommercemlProperties();
    private ServiceProperties productService = new ServiceProperties();
    private ServiceProperties orderService = new ServiceProperties();
    private ImageProcessingProperties imageProcessing = new ImageProcessingProperties();
    private FtkProperties ftk = new FtkProperties();

    @NestedConfigurationProperty
    private ImportConfig importConfig = new ImportConfig();

    @Getter
    @Setter
    public static class CommercemlProperties {
        /** Логин для Basic auth от 1С */
        private String username;
        /** Пароль для Basic auth от 1С */
        private String password;
        /** Лимит размера файла в байтах (для ответа mode=init) */
        private long fileLimit = 52428800L; // 50MB
        /** Временная директория для файлов обмена */
        private String tempDir = "/tmp/1c-exchange";
    }

    @Getter
    @Setter
    public static class ServiceProperties {
        private String url;
        private int batchSize = 100;
    }

    @Getter
    @Setter
    public static class ImageProcessingProperties {
        // --- Адаптивный пул ---
        /** Минимум потоков (core pool size) */
        private int minThreads = 2;
        /** Максимум потоков */
        private int maxThreads = 20;
        /** Максимум потоков на ядро CPU (для расчёта верхней границы) */
        private int maxThreadsPerCpu = 5;
        /** Порог очереди для scale up */
        private int scaleUpThreshold = 100;
        /** Порог очереди для scale down */
        private int scaleDownThreshold = 10;
        /** Интервал проверки нагрузки для адаптации пула (сек) */
        private int checkIntervalSeconds = 30;
        /** Размер bounded buffer (in-memory очередь) */
        private int bufferMaxSize = 500;

        // --- Image pipeline ---
        /** Максимальный размер по большей стороне (px) */
        private int maxImageWidth = 1200;
        /** Качество WebP: 0.0–1.0 */
        private double webpQuality = 0.80;

        // --- DB polling ---
        /** Сколько задач забирать из БД за один poll */
        private int pollBatchSize = 50;
        /** Интервал poll из БД (мс) */
        private long pollIntervalMs = 5000;
        /** Максимум retry при ошибке обработки */
        private int maxRetries = 3;
    }

    @Getter
    @Setter
    public static class ImportConfig {
        private int chunkSize = 100;
        private int maxConcurrentRequests = 4;
    }

    @Getter
    @Setter
    public static class FtkProperties {
        /** Лимит товаров (родителей) на один запуск. 0 = без лимита */
        private int importLimit = 100;
        /** Slug корневой категории ФТК (должна существовать в product-service) */
        private String rootCategorySlug = "ftk";
        /** Таймаут скачивания одного изображения в секундах */
        private int imageDownloadTimeoutSec = 30;
    }
}