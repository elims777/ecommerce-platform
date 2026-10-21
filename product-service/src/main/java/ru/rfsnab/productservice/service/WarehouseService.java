package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.configuration.CacheConfig;
import ru.rfsnab.productservice.dto.WarehouseRequest;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.WarehouseNotFoundException;
import ru.rfsnab.productservice.mapper.WarehouseMapper;
import ru.rfsnab.productservice.model.Warehouse;
import ru.rfsnab.productservice.repository.WarehouseRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final ProductStockService stockService;

    @Transactional(readOnly = true)
    public List<Warehouse> getActive() {
        return warehouseRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Warehouse> getAll() {
        return warehouseRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional
    public Warehouse create(WarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.code())) {
            throw new BusinessException("Склад с кодом " + request.code() + " уже существует");
        }
        return warehouseRepository.save(WarehouseMapper.toEntity(request));
    }

    /** Код неизменяем. При смене active пересчитывается суммарный остаток всех товаров. */
    @Transactional
    @CacheEvict(value = CacheConfig.PRODUCT_STATS_CACHE, allEntries = true)
    public Warehouse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = requireById(id);
        boolean activeChanged = !warehouse.getActive().equals(request.active());
        WarehouseMapper.updateEntity(warehouse, request);
        Warehouse saved = warehouseRepository.save(warehouse);
        if (activeChanged) {
            stockService.recalcAll();
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Warehouse requireById(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new WarehouseNotFoundException("Склад id=" + id + " не найден"));
    }

    @Transactional(readOnly = true)
    public Warehouse requireActiveByCode(String code) {
        Warehouse warehouse = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new WarehouseNotFoundException("Склад " + code + " не найден"));
        if (!warehouse.getActive()) {
            throw new BusinessException("Склад " + code + " выключен");
        }
        return warehouse;
    }
}
