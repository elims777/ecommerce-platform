package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.productservice.dto.WarehouseRequest;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.WarehouseNotFoundException;
import ru.rfsnab.productservice.model.Warehouse;
import ru.rfsnab.productservice.repository.WarehouseRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseService Unit Tests")
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductStockService stockService;

    @InjectMocks
    private WarehouseService warehouseService;

    private Warehouse warehouse() {
        return Warehouse.builder().id(1L).code("PUSHKIN").name("Склад Пушкин").city("Пушкин").active(true).build();
    }

    @Test
    @DisplayName("create с занятым кодом — BusinessException")
    void create_DuplicateCode_Throws() {
        when(warehouseRepository.existsByCode("PUSHKIN")).thenReturn(true);
        WarehouseRequest request = new WarehouseRequest("PUSHKIN", "Склад", "Пушкин", false, true, 2);

        assertThatThrownBy(() -> warehouseService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Склад с кодом PUSHKIN уже существует");
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    @DisplayName("update не меняет code и при смене active пересчитывает остатки")
    void update_ActiveChanged_RecalcsAndKeepsCode() {
        Warehouse existing = warehouse();
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(warehouseRepository.save(existing)).thenReturn(existing);
        WarehouseRequest request = new WarehouseRequest("OTHER", "Новое имя", "Пушкин", true, false, 5);

        Warehouse result = warehouseService.update(1L, request);

        assertThat(result.getCode()).isEqualTo("PUSHKIN");
        assertThat(result.getName()).isEqualTo("Новое имя");
        assertThat(result.getActive()).isFalse();
        verify(stockService).recalcAll();
    }

    @Test
    @DisplayName("update без смены active не пересчитывает остатки")
    void update_ActiveSame_NoRecalc() {
        Warehouse existing = warehouse();
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(warehouseRepository.save(existing)).thenReturn(existing);
        WarehouseRequest request = new WarehouseRequest("PUSHKIN", "Новое имя", "Пушкин", false, true, 2);

        warehouseService.update(1L, request);

        verify(stockService, never()).recalcAll();
    }

    @Test
    @DisplayName("requireActiveByCode для выключенного склада — BusinessException")
    void requireActiveByCode_Inactive_Throws() {
        Warehouse inactive = warehouse();
        inactive.setActive(false);
        when(warehouseRepository.findByCode("PUSHKIN")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> warehouseService.requireActiveByCode("PUSHKIN"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Склад PUSHKIN выключен");
    }

    @Test
    @DisplayName("requireActiveByCode для отсутствующего склада — WarehouseNotFoundException")
    void requireActiveByCode_Missing_Throws() {
        when(warehouseRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> warehouseService.requireActiveByCode("NOPE"))
                .isInstanceOf(WarehouseNotFoundException.class);
    }
}
