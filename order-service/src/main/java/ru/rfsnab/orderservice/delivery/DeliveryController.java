package ru.rfsnab.orderservice.delivery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/carriers")
    @Operation(summary = "Включённые транспортные компании")
    public ResponseEntity<List<CarrierDto>> getCarriers() {
        return ResponseEntity.ok(carrierService.getEnabled().stream().map(CarrierDto::from).toList());
    }
}
