package ru.rfsnab.integrationservice.service.ftk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.integrationservice.config.IntegrationProperties;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("FtkImageDownloader")
@ExtendWith(MockitoExtension.class)
class FtkImageDownloaderTest {

    @Mock private RestTemplate productServiceRestTemplate;

    private IntegrationProperties properties;
    private FtkImageDownloader downloader;

    @BeforeEach
    void setUp() {
        properties = new IntegrationProperties();
        properties.getProductService().setUrl("http://product-service:8083");
        downloader = new FtkImageDownloader(productServiceRestTemplate, properties);
    }

    @Nested
    @DisplayName("predictFileKey")
    class PredictFileKeyTests {

        @Test
        @DisplayName("формат: products/ftk/{externalId}/{fileName}")
        void shouldBuildDeterministicFileKey() {
            String fileKey = downloader.predictFileKey("ftp://host/GoodsPictures/photo1.jpg", "FTK-12345");
            assertThat(fileKey).isEqualTo("products/ftk/FTK-12345/ftk-photo1.webp");
        }
    }

    @Nested
    @DisplayName("getExistingFileKeysBatch")
    class GetExistingFileKeysBatchTests {

        @Test
        @DisplayName("возвращает Map externalId -> Set из ответа product-service")
        void shouldReturnKeysFromResponse() {
            Map<String, List<String>> response = Map.of(
                    "FTK-12345", List.of("products/ftk/FTK-12345/photo1.jpg", "products/ftk/FTK-12345/photo2.jpg"));
            when(productServiceRestTemplate.exchange(
                    anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                    .thenReturn(ResponseEntity.ok(response));

            Map<String, Set<String>> keys = downloader.getExistingFileKeysBatch(List.of("FTK-12345"));

            assertThat(keys.get("FTK-12345")).containsExactlyInAnyOrder(
                    "products/ftk/FTK-12345/photo1.jpg", "products/ftk/FTK-12345/photo2.jpg");
        }

        @Test
        @DisplayName("при ошибке запроса возвращает пустую Map, не бросает исключение")
        void shouldReturnEmptyMapOnError() {
            when(productServiceRestTemplate.exchange(
                    anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            Map<String, Set<String>> keys = downloader.getExistingFileKeysBatch(List.of("FTK-12345"));

            assertThat(keys).isEmpty();
        }

        @Test
        @DisplayName("пустой список externalId — не делает запрос, возвращает пустую Map")
        void shouldReturnEmptyMapForEmptyInput() {
            Map<String, Set<String>> keys = downloader.getExistingFileKeysBatch(List.of());

            assertThat(keys).isEmpty();
        }
    }

    @Nested
    @DisplayName("convertToWebP")
    class ConvertToWebPTests {

        @Test
        @DisplayName("конвертирует grayscale JPEG (1 канал) в WebP без исключения")
        void shouldConvertGrayscaleImage() throws Exception {
            BufferedImage grayscale = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(grayscale, "jpg", baos);

            byte[] webpBytes = downloader.convertToWebP(baos.toByteArray());

            assertThat(webpBytes).isNotEmpty();
        }
    }
}
