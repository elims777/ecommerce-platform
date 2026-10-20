package ru.rfsnab.orderservice.delivery.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierDto;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierTerminalDto;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;
import ru.rfsnab.orderservice.delivery.service.CarrierService;
import ru.rfsnab.orderservice.delivery.service.DeliveryService;

import java.util.List;

/**
 * Справочники доставки для оформления заказа.
 */
@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
@Tag(name = "Delivery", description = "Транспортные компании для оформления заказа")
@SecurityRequirement(name = "Bearer Authentication")
public class DeliveryController {

    private final CarrierService carrierService;
    private final DeliveryService deliveryService;

    @GetMapping("/carriers")
    @Operation(summary = "Включённые транспортные компании")
    public ResponseEntity<List<CarrierDto>> getCarriers() {
        return ResponseEntity.ok(carrierService.getEnabled());
    }

    /** Поиск города по префиксу среди активных точек ТК. */
    @GetMapping("/cities")
    @Operation(summary = "Поиск города среди активных точек ТК (от 2 символов, до 20 городов)")
    public ResponseEntity<List<String>> getCities(@RequestParam CarrierCode carrier,
                                                  @RequestParam PointType type,
                                                  @RequestParam String q) {
        return ResponseEntity.ok(deliveryService.findCities(carrier, type, q));
    }

    /** Активные точки ТК в городе одного типа. */
    @GetMapping("/terminals")
    @Operation(summary = "Активные точки ТК в городе одного типа")
    public ResponseEntity<List<CarrierTerminalDto>> getTerminals(@RequestParam CarrierCode carrier,
                                                                 @RequestParam String city,
                                                                 @RequestParam PointType type) {
        return ResponseEntity.ok(deliveryService.getTerminals(carrier, city, type));
    }
}
