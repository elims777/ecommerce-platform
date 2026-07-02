package ru.rfsnab.integrationservice.service.ftk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.config.IntegrationProperties.FtpProperties;

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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Скачивает изображение с FTP/HTTP URL ФТК, конвертирует в WebP и загружает в product-service.
 *
 * URL изображений ФТК содержит только путь к файлу — хост, порт и учётные данные
 * берутся из конфигурации (env-переменные FTK_FTP_*).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FtkImageDownloader {

    private static final String UPLOAD_URI = "/api/v1/products/external/{externalId}/images";
    private static final String EXISTING_KEYS_BATCH_URI = "/api/v1/products/external/images/keys/batch";
    private static final String WEBP_FORMAT = "webp";

    private final RestTemplate productServiceRestTemplate;
    private final IntegrationProperties properties;

    /**
     * Уже загруженные fileKey картинок для набора товаров (один batch-запрос вместо N).
     * Используется для сверки перед перекачкой картинок ФТК.
     * При любой ошибке (сеть, таймаут, 5xx) — не блокирует импорт, возвращает пустую Map.
     * @return externalId -> Set загруженных fileKey (товары без картинок отсутствуют в карте)
     */
    public Map<String, Set<String>> getExistingFileKeysBatch(List<String> externalIds) {
        if (externalIds.isEmpty()) return Map.of();

        String url = properties.getProductService().getUrl() + EXISTING_KEYS_BATCH_URI;
        try {
            HttpEntity<List<String>> request = new HttpEntity<>(externalIds);
            Map<String, List<String>> response = productServiceRestTemplate.exchange(
                    url, org.springframework.http.HttpMethod.POST, request,
                    new ParameterizedTypeReference<Map<String, List<String>>>() {}
            ).getBody();

            if (response == null) return Map.of();
            return response.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
        } catch (RestClientException e) {
            log.warn("Не удалось получить существующие fileKey изображений (batch, {} товаров): {}",
                    externalIds.size(), e.getMessage());
            return Map.of();
        }
    }

    /** Предсказывает fileKey, который получит картинка в product-service, до её скачивания. */
    public String predictFileKey(String imageUrl, String externalId) {
        return "products/ftk/" + externalId + "/" + buildFileName(imageUrl, externalId);
    }

    public boolean downloadAndUpload(String imageUrl, String externalId) {
        if (imageUrl == null || imageUrl.isBlank()) return false;

        try {
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("Пустое изображение: externalId={}", externalId);
                return false;
            }

            byte[] webpBytes = convertToWebP(imageBytes);
            String fileName = buildFileName(imageUrl, externalId);
            uploadToProductService(externalId, webpBytes, fileName);

            log.debug("Изображение загружено: externalId={}, size={}KB", externalId, webpBytes.length / 1024);
            return true;

        } catch (Exception e) {
            log.warn("Ошибка загрузки изображения: externalId={}: {}", externalId, e.getMessage());
            return false;
        }
    }

    private byte[] downloadImage(String imageUrl) throws IOException, InterruptedException {
        if (imageUrl.startsWith("ftp://")) {
            return downloadViaFtp(extractFtpPath(imageUrl));
        }

        int timeoutSec = properties.getFtk().getImageDownloadTimeoutSec();
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
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Извлекает путь из FTP URL, отбрасывая схему, учётные данные и хост.
     * ftp://user:pass@host/GoodsPictures/UUID.jpg → /GoodsPictures/UUID.jpg
     */
    private String extractFtpPath(String ftpUrl) {
        try {
            URI uri = URI.create(ftpUrl);
            return uri.getPath();
        } catch (Exception e) {
            // fallback: всё после третьего слеша
            int idx = ftpUrl.indexOf('/', ftpUrl.indexOf("//") + 2);
            return idx >= 0 ? ftpUrl.substring(idx) : ftpUrl;
        }
    }

    private byte[] downloadViaFtp(String remotePath) throws IOException {
        FtpProperties cfg = properties.getFtk().getFtp();
        int timeoutMs = properties.getFtk().getImageDownloadTimeoutSec() * 1000;

        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout(timeoutMs);
        ftp.setDefaultTimeout(timeoutMs);
        ftp.setDataTimeout(Duration.ofMillis(timeoutMs));

        try {
            ftp.connect(cfg.getHost(), cfg.getPort());
            ftp.login(cfg.getUsername(), cfg.getPassword());
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);

            try (InputStream is = ftp.retrieveFileStream(remotePath)) {
                if (is == null) {
                    throw new IOException("Файл не найден на FTP: " + remotePath);
                }
                byte[] data = is.readAllBytes();
                ftp.completePendingCommand();
                return data;
            }
        } finally {
            if (ftp.isConnected()) {
                try { ftp.logout(); } catch (Exception ignored) {}
                try { ftp.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    byte[] convertToWebP(byte[] imageBytes) throws IOException {
        IntegrationProperties.ImageProcessingProperties cfg = properties.getImageProcessing();

        BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        if (original == null) {
            throw new IOException("Не удалось декодировать изображение");
        }

        // webp-imageio не умеет кодировать grayscale (например, часть JPEG от ФТК) — падает
        // с ArrayIndexOutOfBoundsException внутри кодека, поэтому приводим к RGB заранее.
        if (original.getType() != BufferedImage.TYPE_INT_RGB && original.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage rgb = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.createGraphics().drawImage(original, 0, 0, null);
            original = rgb;
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
        String[] parts = imageUrl.split("/");
        String lastPart = parts[parts.length - 1];
        int dot = lastPart.lastIndexOf('.');
        String base = (dot > 0) ? lastPart.substring(0, dot) : lastPart;
        if (base.isEmpty()) base = externalId;
        return "ftk-" + base + ".webp";
    }
}
