package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.exception.WarehousePointNotFoundException;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.repository.WarehousePointRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehousePointService{
    private final WarehousePointRepository warehousePointRepository;

    /**
     * Список активных складов для выбора покупателем при самовывозе.
     */
    @Transactional(readOnly = true)
    public List<WarehousePoint> getActivePoints(){
        return warehousePointRepository.findAllByActiveTrueOrderByNameAsc();
    }

    /**
     * Получение активной точки по ID.
     * Используется при создании заказа с самовывозом.
     *
     * @throws WarehousePointNotFoundException если точка не найдена или неактивна
     */
    @Transactional(readOnly = true)
    public WarehousePoint getActivePoint(Long id) {
        WarehousePoint point = warehousePointRepository.findById(id)
                .orElseThrow(() -> new WarehousePointNotFoundException(
                        "Точка самовывоза не найдена: " + id));

        if (!point.isActive()) {
            throw new WarehousePointNotFoundException(
                    "Точка самовывоза недоступна: " + point.getName());
        }

        return point;
    }
}
