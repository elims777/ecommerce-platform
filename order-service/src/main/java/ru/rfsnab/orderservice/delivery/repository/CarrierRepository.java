package ru.rfsnab.orderservice.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.orderservice.delivery.models.entity.Carrier;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;

import java.util.List;

public interface CarrierRepository extends JpaRepository<Carrier, CarrierCode> {

    List<Carrier> findAllByOrderBySortOrderAsc();

    List<Carrier> findAllByEnabledTrueOrderBySortOrderAsc();
}
