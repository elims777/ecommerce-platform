package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.exception.DuplicateWarehousePointException;
import ru.rfsnab.orderservice.exception.WarehousePointNotFoundException;
import ru.rfsnab.orderservice.models.dto.order.WarehousePointRequest;
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

    /**
     * Точка по ID без проверки активности — для показа уже оформленного заказа.
     *
     * @throws WarehousePointNotFoundException если точки нет
     */
    public WarehousePoint getPoint(Long id) {
        return warehousePointRepository.findById(id)
                .orElseThrow(() -> new WarehousePointNotFoundException("Точка самовывоза не найдена: " + id));
    }

    /** Все точки (включая неактивные) — для админки. */
    @Transactional(readOnly = true)
    public List<WarehousePoint> getAllPoints() {
        return warehousePointRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public WarehousePoint create(WarehousePointRequest request) {
        WarehousePoint point = new WarehousePoint();
        apply(point, request);
        return warehousePointRepository.save(point);
    }

    @Transactional
    public WarehousePoint update(Long id, WarehousePointRequest request) {
        WarehousePoint point = getPoint(id);
        apply(point, request);
        return warehousePointRepository.save(point);
    }

    // name — UNIQUE в БД: без проверки дубль дал бы 500 вместо понятного 409
    private void apply(WarehousePoint point, WarehousePointRequest request) {
        warehousePointRepository.findByName(request.name())
                .filter(other -> !other.getId().equals(point.getId()))
                .ifPresent(other -> {
                    throw new DuplicateWarehousePointException("Точка с названием «" + request.name() + "» уже есть");
                });
        point.setName(request.name());
        point.setCity(request.city());
        point.setStreet(request.street());
        point.setBuilding(request.building());
        point.setPostalCode(request.postalCode());
        point.setPhoneNumber(request.phone());
        point.setWorkingHours(request.workingHours());
        point.setDescription(request.description());
        point.setLatitude(request.latitude());
        point.setLongitude(request.longitude());
        point.setActive(request.active());
    }
}
