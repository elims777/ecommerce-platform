package ru.rfsnab.integrationservice.service.catalog;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.rfsnab.integrationservice.config.IntegrationProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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
     * Сохранение файла от 1С на диск.
     * Путь filename приходит от 1С как есть (может содержать поддиректории).
     *
     * @param filename имя файла с относительным путём (например, "import_files/60/xxx.jpg")
     * @param input    содержимое файла из тела HTTP-запроса
     * @return абсолютный путь к сохранённому файлу
     */
    public Path saveFile(String filename, InputStream input) throws IOException {
        Path targetPath = resolveAndValidatePath(filename);

        // Создаём поддиректории если нужно (import_files/60/)
        Files.createDirectories(targetPath.getParent());

        // Атомарная запись (перезапись если файл уже существует)
        Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);

        return targetPath;
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
}