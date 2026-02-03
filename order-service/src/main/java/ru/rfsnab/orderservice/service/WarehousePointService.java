package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.repository.WarehousePointRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehousePointService extends RuntimeException{
    private final WarehousePointRepository warehousePointRepository;

    /**
     * Список активных складов для выбора покупателем при самовывозе.
     */
    @Transactional(readOnly = true)
    public List<WarehousePoint> getActivePoints(){
        return warehousePointRepository.findAllByActiveTrueOrderByNameAsc();
    }
}
