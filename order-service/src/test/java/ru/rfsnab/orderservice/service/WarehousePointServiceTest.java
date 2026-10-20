package ru.rfsnab.orderservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.orderservice.exception.DuplicateWarehousePointException;
import ru.rfsnab.orderservice.exception.WarehousePointNotFoundException;
import ru.rfsnab.orderservice.models.dto.order.WarehousePointRequest;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.repository.WarehousePointRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehousePointService — уникальность названия и поиск точки")
class WarehousePointServiceTest {

    private static final String NAME = "Склад";

    @Mock
    private WarehousePointRepository warehousePointRepository;

    @InjectMocks
    private WarehousePointService service;

    private WarehousePointRequest request() {
        return new WarehousePointRequest(NAME, "Новосибирск", "Ленина", "1", null, "+79990000000",
                null, null, null, null, true);
    }

    private WarehousePoint point(Long id) {
        return WarehousePoint.builder().id(id).name(NAME).build();
    }

    @Test
    @DisplayName("create с занятым названием — DuplicateWarehousePointException")
    void createWithTakenNameRejected() {
        when(warehousePointRepository.findByName(NAME)).thenReturn(Optional.of(point(1L)));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(DuplicateWarehousePointException.class);
        verify(warehousePointRepository, never()).save(any());
    }

    @Test
    @DisplayName("update со своим же названием — сохраняется без ошибки")
    void updateKeepingOwnNameSaved() {
        WarehousePoint existing = point(1L);
        when(warehousePointRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(warehousePointRepository.findByName(NAME)).thenReturn(Optional.of(existing));
        when(warehousePointRepository.save(existing)).thenReturn(existing);

        WarehousePoint result = service.update(1L, request());

        assertThat(result.getCity()).isEqualTo("Новосибирск");
        verify(warehousePointRepository).save(existing);
    }

    @Test
    @DisplayName("update на название другой точки — DuplicateWarehousePointException")
    void updateToNameOfOtherPointRejected() {
        when(warehousePointRepository.findById(1L)).thenReturn(Optional.of(point(1L)));
        when(warehousePointRepository.findByName(NAME)).thenReturn(Optional.of(point(2L)));

        assertThatThrownBy(() -> service.update(1L, request()))
                .isInstanceOf(DuplicateWarehousePointException.class);
        verify(warehousePointRepository, never()).save(any());
    }

    @Test
    @DisplayName("getPoint с неизвестным id — WarehousePointNotFoundException")
    void getPointUnknownIdRejected() {
        when(warehousePointRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPoint(99L))
                .isInstanceOf(WarehousePointNotFoundException.class);
    }
}
