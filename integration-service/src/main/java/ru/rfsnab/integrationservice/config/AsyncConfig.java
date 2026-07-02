package ru.rfsnab.integrationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /**
     * Executor для асинхронного ФТК-импорта (importFromFtp).
     * Импорт последовательный, конкурентность не нужна — один поток лишь освобождает
     * HTTP request thread контроллера от долгого ожидания (импорт трёх порций может идти долго).
     */
    @Bean
    public Executor ftkImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("ftk-import-");
        executor.initialize();
        return executor;
    }
}
