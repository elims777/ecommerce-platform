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
    @DisplayName("определение формата изображения")
    class FormatDetectionTests {

        @Test
        @DisplayName("isWebP: распознаёт WebP по сигнатуре RIFF....WEBP (VP8 и VP8X)")
        void shouldDetectWebP() {
            // реальные заголовки файлов ФТК, на которых JVM падала с SIGSEGV
            byte[] vp8x = {'R', 'I', 'F', 'F', 0x78, (byte) 0xC4, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', 'X'};
            byte[] vp8 = {'R', 'I', 'F', 'F', (byte) 0xD2, (byte) 0xE2, 1, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

            assertThat(FtkImageDownloader.isWebP(vp8x)).isTrue();
            assertThat(FtkImageDownloader.isWebP(vp8)).isTrue();
        }

        @Test
        @DisplayName("isWebP: false для JPEG и коротких массивов")
        void shouldRejectNonWebP() {
            byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};

            assertThat(FtkImageDownloader.isWebP(jpeg)).isFalse();
            assertThat(FtkImageDownloader.isWebP(new byte[5])).isFalse();
        }

        @Test
        @DisplayName("isWebP: true для результата convertToWebP (согласованность с кодеком)")
        void shouldDetectEncodedWebP() throws Exception {
            BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);

            byte[] webp = downloader.convertToWebP(baos.toByteArray());

            assertThat(FtkImageDownloader.isWebP(webp)).isTrue();
        }

        @Test
        @DisplayName("isSafeToDecode: true для JPEG, PNG, GIF, BMP, TIFF")
        void shouldAllowSafeFormats() {
            byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
            byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0};
            byte[] bmp = {'B', 'M', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            byte[] tiffLe = {'I', 'I', 42, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            byte[] tiffBe = {'M', 'M', 0, 42, 0, 0, 0, 0, 0, 0, 0, 0};

            assertThat(FtkImageDownloader.isSafeToDecode(jpeg)).isTrue();
            assertThat(FtkImageDownloader.isSafeToDecode(png)).isTrue();
            assertThat(FtkImageDownloader.isSafeToDecode(gif)).isTrue();
            assertThat(FtkImageDownloader.isSafeToDecode(bmp)).isTrue();
            assertThat(FtkImageDownloader.isSafeToDecode(tiffLe)).isTrue();
            assertThat(FtkImageDownloader.isSafeToDecode(tiffBe)).isTrue();
        }

        @Test
        @DisplayName("isSafeToDecode: false для WebP и неизвестных форматов")
        void shouldRejectUnknownFormats() {
            byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
            byte[] unknown = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B};

            assertThat(FtkImageDownloader.isSafeToDecode(webp)).isFalse();
            assertThat(FtkImageDownloader.isSafeToDecode(unknown)).isFalse();
            assertThat(FtkImageDownloader.isSafeToDecode(new byte[3])).isFalse();
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
