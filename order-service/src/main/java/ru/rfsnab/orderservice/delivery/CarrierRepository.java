package ru.rfsnab.orderservice.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarrierRepository extends JpaRepository<Carrier, CarrierCode> {

    List<Carrier> findAllByOrderBySortOrderAsc();

    List<Carrier> findAllByEnabledTrueOrderBySortOrderAsc();
}
