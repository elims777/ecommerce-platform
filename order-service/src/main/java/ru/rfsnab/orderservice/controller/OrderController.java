package ru.rfsnab.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.orderservice.mapper.OrderMapper;
import ru.rfsnab.orderservice.models.dto.order.CreateOrderRequest;
import ru.rfsnab.orderservice.models.dto.order.OrderDto;
import ru.rfsnab.orderservice.models.dto.order.OrderSummaryDto;
import ru.rfsnab.orderservice.models.dto.order.UpdateOrderRequest;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.service.OrderService;
import ru.rfsnab.orderservice.service.WarehousePointService;

import java.util.Map;
import java.util.UUID;

/**
 * REST контроллер для управления заказами.
 * userId извлекается из JWT токена (subject).
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Управление заказами")
@SecurityRequirement(name = "Bearer Authentication")
public class OrderController {

    private final OrderService orderService;
    private final WarehousePointService warehousePointService;

    @PostMapping
    @Operation(summary = "Создать заказ из корзины")
    public ResponseEntity<OrderDto> createOrder(
            Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(
                getCurrentUserId(authentication),
                getCurrentUserEmail(authentication),
                request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enrichAndMap(order));
    }

    @GetMapping
    @Operation(summary = "Список заказов пользователя")
    public ResponseEntity<Page<OrderSummaryDto>> getUserOrders(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt")
            @Parameter(hidden = true) Pageable pageable) {
        Page<OrderSummaryDto> page = orderService.getUserOrders(getCurrentUserId(authentication), pageable)
                .map(OrderMapper::toSummaryDto);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Получить заказ по ID")
    public ResponseEntity<OrderDto> getOrder(
            Authentication authentication,
            @PathVariable UUID orderId) {
        Order order = orderService.getOrderByIdAndUser(orderId, getCurrentUserId(authentication));
        return ResponseEntity.ok(enrichAndMap(order));
    }

    @PutMapping("/{orderId}")
    @Operation(summary = "Обновить заказ (только в статусе CREATED)")
    public ResponseEntity<OrderDto> updateOrder(
            Authentication authentication,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderRequest request) {
        Order order = orderService.updateOrder(orderId, getCurrentUserId(authentication), request);
        return ResponseEntity.ok(enrichAndMap(order));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Отменить заказ")
    public ResponseEntity<OrderDto> cancelOrder(
            Authentication authentication,
            @PathVariable UUID orderId) {
        Order order = orderService.cancelOrder(orderId, getCurrentUserId(authentication));
        return ResponseEntity.ok(enrichAndMap(order));
    }

    @PostMapping("/{orderId}/pay")
    @Operation(summary = "Инициировать оплату заказа")
    public ResponseEntity<OrderDto> initiatePayment(
            Authentication authentication,
            @PathVariable UUID orderId) {
        Order order = orderService.initiatePayment(orderId, getCurrentUserId(authentication));
        return ResponseEntity.ok(enrichAndMap(order));
    }

    @PostMapping("/{orderId}/repeat")
    @Operation(summary = "Повторить заказ (создаёт новый заказ на основе существующего)")
    public ResponseEntity<OrderDto> repeatOrder(
            Authentication authentication,
            @PathVariable UUID orderId) {
        Order order = orderService.repeatOrder(
                orderId,
                getCurrentUserId(authentication),
                getCurrentUserEmail(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enrichAndMap(order));
    }

    /**
     * Обогащение Order данными WarehousePoint и маппинг в DTO.
     * Для PICKUP заказов подгружает информацию о точке самовывоза.
     */
    private OrderDto enrichAndMap(Order order) {
        WarehousePoint warehousePoint = null;
        if (order.getWarehousePointId() != null) {
            warehousePoint = warehousePointService.getActivePoint(order.getWarehousePointId());
        }
        return OrderMapper.toDto(order, warehousePoint);
    }

    /**
     * Извлечение userId из JWT токена.
     */
    private Long getCurrentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }

    /**
     * Извлечение userEmail из JWT токена.
     */
    private String getCurrentUserEmail(Authentication authentication) {
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        return (String) details.get("email");
    }
}