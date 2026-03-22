package ru.rfsnab.integrationservice.service.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.rfsnab.integrationservice.config.IntegrationProperties;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты CatalogFileService.
 * Используем @TempDir вместо реальной файловой системы.
 */
@DisplayName("CatalogFileService")
class CatalogFileServiceTest {

    private CatalogFileService fileService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        IntegrationProperties properties = new IntegrationProperties();
        properties.getCommerceml().setTempDir(tempDir.toString());
        fileService = new CatalogFileService(properties);
    }

    @Test
    @DisplayName("сохраняет файл на диск")
    void shouldSaveFileToExchangeDir() throws IOException {
        String content = "<?xml version=\"1.0\"?><test/>";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8));

        fileService.saveFile("import.xml", inputStream);

        Path savedFile = tempDir.resolve("import.xml");
        assertThat(savedFile).exists();
        assertThat(Files.readString(savedFile)).isEqualTo(content);
    }

    @Test
    @DisplayName("сохраняет файл в поддиректорию (import_files/foto/...)")
    void shouldSaveFileToSubdirectory() throws IOException {
        String content = "image-bytes";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8));

        fileService.saveFile("import_files/foto/product.jpg", inputStream);

        Path savedFile = tempDir.resolve("import_files/foto/product.jpg");
        assertThat(savedFile).exists();
    }

    @Test
    @DisplayName("перезаписывает существующий файл")
    void shouldOverwriteExistingFile() throws IOException {
        String original = "original";
        String updated = "updated";

        fileService.saveFile("import.xml",
                new ByteArrayInputStream(original.getBytes(StandardCharsets.UTF_8)));
        fileService.saveFile("import.xml",
                new ByteArrayInputStream(updated.getBytes(StandardCharsets.UTF_8)));

        assertThat(Files.readString(tempDir.resolve("import.xml"))).isEqualTo(updated);
    }

    @Test
    @DisplayName("блокирует path traversal атаку (../)")
    void shouldBlockPathTraversal() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("hack".getBytes());

        assertThatThrownBy(() -> fileService.saveFile("../../etc/passwd", inputStream))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
