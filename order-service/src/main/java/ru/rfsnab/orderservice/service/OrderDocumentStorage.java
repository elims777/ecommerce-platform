package ru.rfsnab.orderservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.orderservice.exception.DocumentStorageException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

/**
 * Тонкая обёртка над S3Client для хранения документов заказов
 * (счета, УПД, коммерческие предложения, сертификаты) в Yandex Object Storage.
 */
@Service
public class OrderDocumentStorage {

    private final S3Client s3Client;
    private final String bucketName;

    public OrderDocumentStorage(S3Client s3Client,
                                @Value("${yandex.storage.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public void upload(MultipartFile file, String fileKey) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new DocumentStorageException("Ошибка при загрузке документа: " + e.getMessage(), e);
        } catch (S3Exception e) {
            throw new DocumentStorageException("Ошибка S3 при загрузке документа: " + e.getMessage(), e);
        }
    }

    public ResponseInputStream<GetObjectResponse> downloadStream(String fileKey) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            return s3Client.getObject(request);
        } catch (S3Exception e) {
            throw new DocumentStorageException("Ошибка S3 при скачивании документа: " + e.getMessage(), e);
        }
    }

    public void delete(String fileKey) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(request);
        } catch (S3Exception e) {
            throw new DocumentStorageException("Ошибка S3 при удалении документа: " + e.getMessage(), e);
        }
    }
}
