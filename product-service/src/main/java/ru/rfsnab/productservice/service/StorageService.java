package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.productservice.exception.InvalidFileException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageService {
    private final S3Client s3Client;

    @Value("${yandex.storage.bucket-name}")
    private String bucketName;

    @Value("${yandex.storage.endpoint}")
    private String endpoint;

    // Разрешенные MIME типы для изображений
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    // Максимальный размер файла (16 MB)
    private static final long MAX_FILE_SIZE = 16 * 1024 * 1024;

    /**
     * Загрузить файл в Yandex Object Storage
     * @param file файл для загрузки
     * @param fileKey путь в bucket (например: products/123/image.jpg)
     * @return публичный URL файла
     */
    public String uploadFile(MultipartFile file, String fileKey){
        try{
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return getPublicUrl(fileKey);
        } catch (IOException e){
            throw new InvalidFileException("Ошибка при загрузке файла: " + e.getMessage());
        } catch (S3Exception e) {
            throw new InvalidFileException("Ошибка S3: " + e.getMessage());
        }
    }

    /**
     * Удалить файл из Yandex Object Storage
     * @param fileKey путь к файлу в bucket
     */
    public void deleteFile(String fileKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);

        } catch (S3Exception e) {
            throw new InvalidFileException("Ошибка при удалении файла: " + e.getMessage());
        }
    }

    /**
     * Получить публичный URL файла
     * @param fileKey путь к файлу в bucket
     * @return публичный URL
     */
    public String getPublicUrl(String fileKey) {
        return String.format("%s/%s/%s", endpoint, bucketName, fileKey);
    }

    /**
     * Проверить существование файла
     * @param fileKey путь к файлу в bucket
     * @return true если файл существует
     */
    public boolean fileExists(String fileKey) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            throw new InvalidFileException("Ошибка при проверке файла: " + e.getMessage());
        }
    }

    /**
     * Валидация изображения
     * Проверяет MIME type и размер файла
     * @param file файл для проверки
     */
    public void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Файл не может быть пустым");
        }

        // Проверка MIME типа
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidFileException(
                    "Неподдерживаемый тип файла: " + contentType + ". Разрешены: " + ALLOWED_IMAGE_TYPES
            );
        }

        // Проверка размера
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException(
                    "Размер файла превышает максимально допустимый: " + (MAX_FILE_SIZE / 1024 / 1024) + " MB"
            );
        }
    }

    /**
     * Валидация размера файла
     * @param size размер в байтах
     * @param maxSize максимальный размер в байтах
     */
    public void validateFileSize(long size, long maxSize) {
        if (size > maxSize) {
            throw new InvalidFileException(
                    "Размер файла превышает максимально допустимый: " + (maxSize / 1024 / 1024) + " MB"
            );
        }
    }
}
