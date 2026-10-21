package ru.rfsnab.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rfsnab.productservice.model.Warehouse;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    List<Warehouse> findByActiveTrueOrderBySortOrderAsc();

    List<Warehouse> findAllByOrderBySortOrderAsc();

    Optional<Warehouse> findByCode(String code);

    boolean existsByCode(String code);
}
