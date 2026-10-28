package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductVideo;
import ru.rfsnab.productservice.repository.ProductVideoRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductVideoService Unit Tests")
class ProductVideoServiceTest {

    private static final Long VIDEO_ID = 5L;

    @Mock
    private ProductVideoRepository videoRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductVideoService videoService;

    @Test
    @DisplayName("updateVideoInfo: обновляет поля, товар остаётся прежним (не затирается null из запроса)")
    void updateVideoInfo_keepsProduct() {
        Product product = Product.builder().id(1L).name("Огнетушитель").build();
        ProductVideo existing = ProductVideo.builder().id(VIDEO_ID).product(product).videoUrl("old")
                .isPrimary(true).displayOrder(3).build();
        ProductVideo update = ProductVideo.builder()
                .videoUrl("https://example.com/new")
                .title("Новое название")
                .provider("youtube")
                .build();
        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(existing));
        when(videoRepository.save(any(ProductVideo.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductVideo saved = videoService.updateVideoInfo(VIDEO_ID, update);

        assertThat(saved.getProduct()).isSameAs(product);
        assertThat(saved.getVideoUrl()).isEqualTo("https://example.com/new");
        assertThat(saved.getTitle()).isEqualTo("Новое название");
        assertThat(saved.getProvider()).isEqualTo("youtube");
        assertThat(saved.getIsPrimary()).isTrue();
        assertThat(saved.getDisplayOrder()).isEqualTo(3);
    }
}
