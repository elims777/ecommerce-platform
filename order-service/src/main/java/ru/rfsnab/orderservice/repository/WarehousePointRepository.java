package ru.rfsnab.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;

import java.util.List;

@Repository
public interface WarehousePointRepository extends JpaRepository<WarehousePoint, Long> {

    List<WarehousePoint> findAllByActiveTrueOrderByNameAsc();
}
