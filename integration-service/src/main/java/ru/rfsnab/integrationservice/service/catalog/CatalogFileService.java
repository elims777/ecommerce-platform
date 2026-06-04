package ru.rfsnab.integrationservice.service.catalog;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.rfsnab.integrationservice.config.IntegrationProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

/**
 * Сервис приёма и сохранения файлов от 1С (mode=file).
 *
 * Файлы сохраняются во временную директорию с сохранением структуры путей из 1С:
 * - import.xml, offers.xml → {tempDir}/import.xml, {tempDir}/offers.xml
 * - import_files/60/xxx.jpg → {tempDir}/import_files/60/xxx.jpg
 *
 * Для картинок: после сохранения создаётся задача в image_processing_tasks (отдельно).
 * Файлы удаляются после обработки.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogFileService {

    private final IntegrationProperties properties;

    @PostConstruct
    void ensureTempDirExists() throws IOException {
        Path tempDir = Paths.get(properties.getCommerceml().getTempDir());
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }
    }

    /**
     * Сохранение файла от 1С на диск + копия в archive/{sessionId}/ для диагностики.
     *
     * @param filename  имя файла с относительным путём (например, "import_files/60/xxx.jpg")
     * @param sessionId ID сессии обмена — используется как имя папки в архиве
     * @param input     содержимое файла из тела HTTP-запроса
     * @return абсолютный путь к сохранённому файлу
     */
    public Path saveFile(String filename, String sessionId, InputStream input) throws IOException {
        Path targetPath = resolveAndValidatePath(filename);
        Files.createDirectories(targetPath.getParent());
        Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);

        long sizeBytes = Files.size(targetPath);
        log.info("Получен файл от 1С: {} ({} байт)", filename, sizeBytes);

        archiveFile(filename, sessionId, targetPath);

        return targetPath;
    }

    /**
     * Копирует сохранённый файл в archive/{sessionId}/{filename}.
     * Только XML-файлы — картинки не архивируем (экономим место).
     */
    private void archiveFile(String filename, String sessionId, Path sourcePath) {
        if (sessionId == null || !filename.endsWith(".xml")) {
            return;
        }
        try {
            Path tempDir = Paths.get(properties.getCommerceml().getTempDir());
            Path archivePath = tempDir.resolve("archive").resolve(sessionId).resolve(filename).normalize();
            if (!archivePath.startsWith(tempDir)) {
                return;
            }
            Files.createDirectories(archivePath.getParent());
            Files.copy(sourcePath, archivePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Файл заархивирован: archive/{}/{}", sessionId, filename);
        } catch (IOException e) {
            log.warn("Не удалось заархивировать файл {}: {}", filename, e.getMessage());
        }
    }

    /**
     * Получение пути к файлу в temp-директории.
     */
    public Path getFilePath(String filename) {
        return resolveAndValidatePath(filename);
    }

    /**
     * Удаление файла после обработки.
     */
    public void deleteFile(String filename) throws IOException {
        Path path = resolveAndValidatePath(filename);
        Files.deleteIfExists(path);
    }

    /**
     * Проверка: файл должен находиться внутри tempDir (защита от path traversal).
     */
    private Path resolveAndValidatePath(String filename) {
        Path tempDir = Paths.get(properties.getCommerceml().getTempDir());
        Path resolved = tempDir.resolve(filename).normalize();

        if (!resolved.startsWith(tempDir)) {
            throw new IllegalArgumentException("Некорректный путь файла: " + filename);
        }

        return resolved;
    }

    /**
     * Проверка существования файла.
     */
    public boolean fileExists(String filename) {
        return Files.exists(resolveAndValidatePath(filename));
    }

    /**
     * Удаление папок из archive/, которым больше 14 дней.
     * Каждая папка = одна сессия обмена (имя = sessionId).
     * Запускается раз в сутки в 03:00.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeOldArchives() {
        Path archiveDir = Paths.get(properties.getCommerceml().getTempDir()).resolve("archive");
        if (!Files.exists(archiveDir)) {
            return;
        }
        Instant cutoff = Instant.now().minus(14, ChronoUnit.DAYS);
        try (var sessionDirs = Files.list(archiveDir)) {
            sessionDirs.filter(Files::isDirectory).forEach(sessionDir -> {
                try {
                    Instant lastModified = Files.getLastModifiedTime(sessionDir).toInstant();
                    if (lastModified.isBefore(cutoff)) {
                        deleteDirectoryRecursively(sessionDir);
                        log.info("Архив удалён (старше 14 дней): {}", sessionDir.getFileName());
                    }
                } catch (IOException e) {
                    log.warn("Не удалось удалить архивную папку {}: {}", sessionDir.getFileName(), e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Ошибка при очистке архива: {}", e.getMessage());
        }
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.warn("Не удалось удалить {}: {}", path, e.getMessage());
                }
            });
        }
    }
}