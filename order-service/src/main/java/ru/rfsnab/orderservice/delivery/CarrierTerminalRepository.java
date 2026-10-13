package ru.rfsnab.orderservice.delivery;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Точки выдачи ТК.
 */
public interface CarrierTerminalRepository extends JpaRepository<CarrierTerminal, Long> {

    /** Все точки ТК, включая неактивные — для сверки при синхронизации. */
    List<CarrierTerminal> findAllByCarrierCode(CarrierCode carrierCode);

    /** Число активных точек ТК. */
    long countByCarrierCodeAndActiveTrue(CarrierCode carrierCode);

    /** Есть ли у ТК хоть одна точка (в том числе неактивная). */
    boolean existsByCarrierCode(CarrierCode carrierCode);

    /** Активные точки города одного типа — один SELECT по плоской таблице. */
    List<CarrierTerminal> findAllByCarrierCodeAndCityAndPointTypeAndActiveTrueOrderByNameAsc(
            CarrierCode carrierCode, String city, PointType pointType);

    /** Города с активными точками, название которых начинается с prefix (prefix уже в нижнем регистре). */
    @Query("""
            select distinct t.city from CarrierTerminal t
            where t.carrierCode = :carrier and t.pointType = :type and t.active = true
              and lower(t.city) like concat(:prefix, '%') escape '\\'
            order by t.city
            """)
    List<String> findCities(@Param("carrier") CarrierCode carrier, @Param("type") PointType type,
                            @Param("prefix") String prefix, Pageable pageable);
}
