package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Картинки новостей в Yandex Object Storage.
 * <p>
 * Имя файла — UUID, а не оригинальное: в новостях нет привязки картинки к записи в БД,
 * и одноимённые файлы затирали бы друг друга.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsImageService {

    private static final String FOLDER = "news/";

    private final StorageService storageService;

    /**
     * @return публичный URL загруженной картинки
     */
    public String upload(MultipartFile file) {
        storageService.validateImage(file);

        String fileKey = FOLDER + UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        String url = storageService.uploadFile(file, fileKey);

        log.info("Загружена картинка новости: {}", fileKey);
        return url;
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot > 0 ? originalFilename.substring(dot).toLowerCase() : "";
    }
}
