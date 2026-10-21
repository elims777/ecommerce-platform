package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductMergeService Unit Tests")
class ProductMergeServiceTest {

    private static final String SKU = "5004";

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductStockService stockService;

    @InjectMocks
    private ProductMergeService mergeService;

    private Product ftk(Long id) {
        return Product.builder().id(id).name("Факел " + id).source("FTK").externalId("FTK-" + id).sku(SKU).build();
    }

    private Product oneC(Long id) {
        return Product.builder().id(id).name("1С " + id).source("INTERNAL").externalId("uuid-" + id).sku(SKU).build();
    }

    @Nested
    @DisplayName("autoMergeOneC()")
    class AutoMergeOneC {

        @Test
        @DisplayName("одна пара Факела — склеивает, скрывает и пересчитывает главный")
        void onePair_Merges() {
            Product ftk = ftk(10L);
            Product oneC = oneC(2L);
            when(productRepository.findMergeableFtkBySku(SKU)).thenReturn(List.of(ftk));

            mergeService.autoMergeOneC(oneC);

            assertThat(oneC.getMergedIntoProductId()).isEqualTo(10L);
            assertThat(oneC.getIsActive()).isFalse();
            verify(productRepository).save(oneC);
            verify(stockService).recalcTotal(10L);
        }

        @Test
        @DisplayName("0 пар — ничего не сохраняется")
        void noPairs_DoesNothing() {
            when(productRepository.findMergeableFtkBySku(SKU)).thenReturn(List.of());

            mergeService.autoMergeOneC(oneC(2L));

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("2 пары — неоднозначно, ничего не сохраняется")
        void twoPairs_DoesNothing() {
            when(productRepository.findMergeableFtkBySku(SKU)).thenReturn(List.of(ftk(10L), ftk(11L)));

            mergeService.autoMergeOneC(oneC(2L));

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("уже склеен — репозиторий не вызывается")
        void alreadyMerged_Skipped() {
            Product oneC = oneC(2L);
            oneC.setMergedIntoProductId(10L);

            mergeService.autoMergeOneC(oneC);

            verifyNoInteractions(productRepository);
        }

        @Test
        @DisplayName("mergeLocked — репозиторий не вызывается")
        void locked_Skipped() {
            Product oneC = oneC(2L);
            oneC.setMergeLocked(true);

            mergeService.autoMergeOneC(oneC);

            verifyNoInteractions(productRepository);
        }

        @Test
        @DisplayName("пустой или null sku — репозиторий не вызывается")
        void blankSku_Skipped() {
            Product blank = oneC(2L);
            blank.setSku(" ");
            Product nullSku = oneC(3L);
            nullSku.setSku(null);

            mergeService.autoMergeOneC(blank);
            mergeService.autoMergeOneC(nullSku);

            verifyNoInteractions(productRepository);
        }
    }

    @Nested
    @DisplayName("autoMergeFtk()")
    class AutoMergeFtk {

        @Test
        @DisplayName("одна пара 1С — склеивает")
        void onePair_Merges() {
            Product ftk = ftk(10L);
            Product oneC = oneC(2L);
            when(productRepository.findMergeableOneCBySku(SKU)).thenReturn(List.of(oneC));

            mergeService.autoMergeFtk(ftk);

            assertThat(oneC.getMergedIntoProductId()).isEqualTo(10L);
            assertThat(oneC.getIsActive()).isFalse();
            verify(stockService).recalcTotal(10L);
        }

        @Test
        @DisplayName("2 пары — ничего не сохраняется")
        void twoPairs_DoesNothing() {
            when(productRepository.findMergeableOneCBySku(SKU)).thenReturn(List.of(oneC(2L), oneC(3L)));

            mergeService.autoMergeFtk(ftk(10L));

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("merge()")
    class Merge {

        @Test
        @DisplayName("главный не Факел — BusinessException")
        void mainNotFtk_Throws() {
            when(productRepository.findById(10L)).thenReturn(Optional.of(oneC(10L)));
            when(productRepository.findById(2L)).thenReturn(Optional.of(oneC(2L)));

            assertThatThrownBy(() -> mergeService.merge(10L, 2L)).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("главный с вариантами — BusinessException")
        void mainHasVariants_Throws() {
            when(productRepository.findById(10L)).thenReturn(Optional.of(ftk(10L)));
            when(productRepository.findById(2L)).thenReturn(Optional.of(oneC(2L)));
            when(productRepository.existsByParentProductId(10L)).thenReturn(true);

            assertThatThrownBy(() -> mergeService.merge(10L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Нельзя привязать к товару с вариантами — привяжите к варианту");
        }

        @Test
        @DisplayName("привязываемый не 1С — BusinessException")
        void oneCNotOneC_Throws() {
            when(productRepository.findById(10L)).thenReturn(Optional.of(ftk(10L)));
            when(productRepository.findById(11L)).thenReturn(Optional.of(ftk(11L)));

            assertThatThrownBy(() -> mergeService.merge(10L, 11L)).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("1С без externalId (ручной товар) — BusinessException")
        void oneCWithoutExternalId_Throws() {
            Product manual = oneC(2L);
            manual.setExternalId(null);
            when(productRepository.findById(10L)).thenReturn(Optional.of(ftk(10L)));
            when(productRepository.findById(2L)).thenReturn(Optional.of(manual));

            assertThatThrownBy(() -> mergeService.merge(10L, 2L)).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("1С уже склеена с другим — BusinessException")
        void oneCAlreadyMerged_Throws() {
            Product oneC = oneC(2L);
            oneC.setMergedIntoProductId(99L);
            when(productRepository.findById(10L)).thenReturn(Optional.of(ftk(10L)));
            when(productRepository.findById(2L)).thenReturn(Optional.of(oneC));

            assertThatThrownBy(() -> mergeService.merge(10L, 2L)).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("успех: связь, isActive=false, mergeLocked=false, пересчёт главного")
        void success_Links() {
            Product oneC = oneC(2L);
            oneC.setMergeLocked(true);
            when(productRepository.findById(10L)).thenReturn(Optional.of(ftk(10L)));
            when(productRepository.findById(2L)).thenReturn(Optional.of(oneC));

            mergeService.merge(10L, 2L);

            assertThat(oneC.getMergedIntoProductId()).isEqualTo(10L);
            assertThat(oneC.getIsActive()).isFalse();
            assertThat(oneC.getMergeLocked()).isFalse();
            verify(stockService).recalcTotal(10L);
        }
    }

    @Nested
    @DisplayName("unmerge()")
    class Unmerge {

        @Test
        @DisplayName("снимает связь, ставит mergeLocked, оставляет товар скрытым и пересчитывает бывший главный")
        void success_Unlinks() {
            Product oneC = oneC(2L);
            oneC.setMergedIntoProductId(10L);
            oneC.setIsActive(false);
            when(productRepository.findById(2L)).thenReturn(Optional.of(oneC));

            mergeService.unmerge(2L);

            assertThat(oneC.getMergedIntoProductId()).isNull();
            assertThat(oneC.getMergeLocked()).isTrue();
            assertThat(oneC.getIsActive()).isFalse();
            verify(productRepository).save(oneC);
            verify(stockService).recalcTotal(10L);
        }

        @Test
        @DisplayName("товар не склеен — BusinessException")
        void notMerged_Throws() {
            when(productRepository.findById(2L)).thenReturn(Optional.of(oneC(2L)));

            assertThatThrownBy(() -> mergeService.unmerge(2L)).isInstanceOf(BusinessException.class);
        }
    }
}
