package ru.rfsnab.integrationservice.service.ftk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.integrationservice.config.IntegrationProperties;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Скачивает изображение с FTP/HTTP URL ФТК, конвертирует в WebP и загружает в product-service.
 *
 * URL изображений ФТК: ftp://fakelftp:poiPOI098@195.133.242.197/GoodsPictures/UUID.jpg
 * Java HttpClient умеет работать с ftp:// через JDK URL handler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FtkImageDownloader {

    private static final String UPLOAD_URI = "/api/v1/products/external/{externalId}/images";
    private static final String WEBP_FORMAT = "webp";

    private final RestTemplate productServiceRestTemplate;
    private final IntegrationProperties properties;

    /**
     * Скачивает изображение по URL, конвертирует в WebP, загружает в product-service.
     *
     * @param imageUrl   URL изображения (ftp:// или http://)
     * @param externalId externalId товара в product-service
     * @return true если успешно
     */
    public boolean downloadAndUpload(String imageUrl, String externalId) {
        if (imageUrl == null || imageUrl.isBlank()) return false;

        try {
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("Пустое изображение: {}", imageUrl);
                return false;
            }

            byte[] webpBytes = convertToWebP(imageBytes);
            String fileName = buildFileName(imageUrl, externalId);
            uploadToProductService(externalId, webpBytes, fileName);

            log.debug("Изображение загружено: externalId={}, url={}, size={}KB",
                    externalId, imageUrl, webpBytes.length / 1024);
            return true;

        } catch (Exception e) {
            log.warn("Ошибка загрузки изображения: externalId={}, url={}: {}",
                    externalId, imageUrl, e.getMessage());
            return false;
        }
    }

    private byte[] downloadImage(String imageUrl) throws IOException, InterruptedException {
        int timeoutSec = properties.getFtk().getImageDownloadTimeoutSec();

        // java.net.http.HttpClient поддерживает ftp:// через URL handler JDK
        // Для FTP с логином/паролем в URL используем java.net.URL как fallback
        if (imageUrl.startsWith("ftp://")) {
            return downloadViaUrl(imageUrl, timeoutSec);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(timeoutSec))
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " при скачивании " + imageUrl);
        }
        return response.body();
    }

    @SuppressWarnings("deprecation")
    private byte[] downloadViaUrl(String ftpUrl, int timeoutSec) throws IOException {
        java.net.URL url = new java.net.URL(ftpUrl);
        java.net.URLConnection conn = url.openConnection();
        conn.setConnectTimeout(timeoutSec * 1000);
        conn.setReadTimeout(timeoutSec * 1000);
        try (InputStream is = conn.getInputStream()) {
            return is.readAllBytes();
        }
    }

    private byte[] convertToWebP(byte[] imageBytes) throws IOException {
        IntegrationProperties.ImageProcessingProperties cfg = properties.getImageProcessing();

        BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        if (original == null) {
            throw new IOException("Не удалось декодировать изображение");
        }

        int maxDim = cfg.getMaxImageWidth();
        int w = Math.min(original.getWidth(), maxDim);
        int h = Math.min(original.getHeight(), maxDim);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Thumbnails.of(original)
                    .size(w, h)
                    .outputFormat(WEBP_FORMAT)
                    .outputQuality(cfg.getWebpQuality())
                    .toOutputStream(baos);
            return baos.toByteArray();
        }
    }

    private void uploadToProductService(String externalId, byte[] webpBytes, String fileName) {
        String url = properties.getProductService().getUrl() + UPLOAD_URI;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(webpBytes) {
            @Override
            public String getFilename() { return fileName; }
        });

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        productServiceRestTemplate.postForEntity(url, entity, Void.class, externalId);
    }

    private String buildFileName(String imageUrl, String externalId) {
        // "ftp://.../.../UUID.jpg" → "ftk-UUID.webp"
        String[] parts = imageUrl.split("/");
        String lastPart = parts[parts.length - 1];
        int dot = lastPart.lastIndexOf('.');
        String base = (dot > 0) ? lastPart.substring(0, dot) : lastPart;
        if (base.isEmpty()) base = externalId;
        return "ftk-" + base + ".webp";
    }
}
