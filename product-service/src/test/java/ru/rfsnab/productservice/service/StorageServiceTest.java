package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.productservice.exception.InvalidFileException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StorageService Unit Tests")
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private StorageService storageService;

    private static final String BUCKET_NAME = "test-bucket";
    private static final String ENDPOINT = "https://storage.yandexcloud.net";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucketName", BUCKET_NAME);
        ReflectionTestUtils.setField(storageService, "endpoint", ENDPOINT);
    }

    // ==================== uploadFile() Tests ====================

    @Nested
    @DisplayName("uploadFile()")
    class UploadFileTests {

        @Test
        @DisplayName("успешно загружает файл и возвращает URL")
        void uploadFile_Success_ReturnsUrl() {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "image",
                    "test.jpg",
                    "image/jpeg",
                    "test content".getBytes()
            );
            String fileKey = "products/1/test.jpg";

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            // When
            String result = storageService.uploadFile(file, fileKey);

            // Then
            assertThat(result).isEqualTo(ENDPOINT + "/" + BUCKET_NAME + "/" + fileKey);
            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("выбрасывает исключение при ошибке S3")
        void uploadFile_S3Error_ThrowsException() {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "image",
                    "test.jpg",
                    "image/jpeg",
                    "test content".getBytes()
            );

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(S3Exception.builder().message("S3 error").build());

            // When & Then
            assertThatThrownBy(() -> storageService.uploadFile(file, "products/1/test.jpg"))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("S3");
        }
    }

    // ==================== deleteFile() Tests ====================

    @Nested
    @DisplayName("deleteFile()")
    class DeleteFileTests {

        @Test
        @DisplayName("успешно удаляет файл")
        void deleteFile_Success() {
            // Given
            String fileKey = "products/1/test.jpg";
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenReturn(DeleteObjectResponse.builder().build());

            // When
            storageService.deleteFile(fileKey);

            // Then
            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("выбрасывает исключение при ошибке S3")
        void deleteFile_S3Error_ThrowsException() {
            // Given
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(S3Exception.builder().message("Delete error").build());

            // When & Then
            assertThatThrownBy(() -> storageService.deleteFile("products/1/test.jpg"))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("удалении");
        }
    }

    // ==================== fileExists() Tests ====================

    @Nested
    @DisplayName("fileExists()")
    class FileExistsTests {

        @Test
        @DisplayName("возвращает true для существующего файла")
        void fileExists_FileExists_ReturnsTrue() {
            // Given
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder().build());

            // When
            boolean result = storageService.fileExists("products/1/test.jpg");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("возвращает false для несуществующего файла")
        void fileExists_FileNotExists_ReturnsFalse() {
            // Given
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().build());

            // When
            boolean result = storageService.fileExists("non-existent.jpg");

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== getPublicUrl() Tests ====================

    @Nested
    @DisplayName("getPublicUrl()")
    class GetPublicUrlTests {

        @Test
        @DisplayName("формирует правильный URL")
        void getPublicUrl_ReturnsCorrectUrl() {
            // When
            String result = storageService.getPublicUrl("products/1/image.jpg");

            // Then
            assertThat(result).isEqualTo(ENDPOINT + "/" + BUCKET_NAME + "/products/1/image.jpg");
        }
    }

    // ==================== validateImage() Tests ====================

    @Nested
    @DisplayName("validateImage()")
    class ValidateImageTests {

        @Test
        @DisplayName("проходит валидацию для допустимого изображения")
        void validateImage_ValidImage_NoException() {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "image",
                    "test.jpg",
                    "image/jpeg",
                    new byte[1024] // 1 KB
            );

            // When & Then - не должно выбрасывать исключение
            storageService.validateImage(file);
        }

        @Test
        @DisplayName("выбрасывает исключение для пустого файла")
        void validateImage_EmptyFile_ThrowsException() {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "image",
                    "test.jpg",
                    "image/jpeg",
                    new byte[0]
            );

            // When & Then
            assertThatThrownBy(() -> storageService.validateImage(file))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("пустым");
        }

        @Test
        @DisplayName("выбрасывает исключение для null файла")
        void validateImage_NullFile_ThrowsException() {
            // When & Then
            assertThatThrownBy(() -> storageService.validateImage(null))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("пустым");
        }

        @Test
        @DisplayName("выбрасывает исключение для неподдерживаемого типа")
        void validateImage_UnsupportedType_ThrowsException() {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "document.pdf",
                    "application/pdf",
                    new byte[1024]
            );

            // When & Then
            assertThatThrownBy(() -> storageService.validateImage(file))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("тип файла");
        }

        @Test
        @DisplayName("выбрасывает исключение для слишком большого файла")
        void validateImage_TooLarge_ThrowsException() {
            // Given - файл больше 16MB
            MockMultipartFile file = new MockMultipartFile(
                    "image",
                    "large.jpg",
                    "image/jpeg",
                    new byte[17 * 1024 * 1024] // 17 MB
            );

            // When & Then
            assertThatThrownBy(() -> storageService.validateImage(file))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("превышает");
        }

        @Test
        @DisplayName("принимает все допустимые типы изображений")
        void validateImage_AllAllowedTypes_NoException() {
            // Given
            String[] allowedTypes = {"image/jpeg", "image/png", "image/gif", "image/webp"};

            for (String type : allowedTypes) {
                MockMultipartFile file = new MockMultipartFile(
                        "image",
                        "test.img",
                        type,
                        new byte[1024]
                );

                // When & Then - не должно выбрасывать исключение
                storageService.validateImage(file);
            }
        }
    }
}
