package ru.rfsnab.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.orderservice.mapper.OrderMapper;
import ru.rfsnab.orderservice.models.dto.order.*;
import ru.rfsnab.orderservice.models.dto.payment.PaymentInitiationResponse;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.service.OrderService;
import ru.rfsnab.orderservice.service.WarehousePointService;

import java.time.LocalDateTime;
import java.util.List;
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
            @RequestHeader(value = "X-Client-Type", defaultValue = "B2C") String clientType,
            @Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(
                getCurrentUserId(authentication),
                getCurrentUserEmail(authentication),
                clientType,
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
//        Page<OrderSummaryDto> page = orderService.getUserOrders(getCurrentUserId(authentication), pageable)
//                .map(OrderMapper::toSummaryDto);
//        return ResponseEntity.ok(page);
        Page<Order> orders = orderService.getUserOrders(getCurrentUserId(authentication), pageable);
        List<OrderSummaryDto> content = orders.getContent().stream()
                .map(OrderMapper::toSummaryDto)
                .toList();
        Page<OrderSummaryDto> page = new PageImpl<>(content, pageable, orders.getTotalElements());
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

    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "Подтвердить заказ (CREATED → PROCESSING, отправляет в 1С)")
    public ResponseEntity<OrderDto> confirmOrder(
            Authentication authentication,
            @PathVariable UUID orderId) {
        Order order = orderService.confirmOrder(orderId, getCurrentUserId(authentication));
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
    @Operation(summary = "Инициировать оплату — роутится по paymentMethod заказа (CARD/SBP/CASH_ON_DELIVERY)")
    public ResponseEntity<PaymentInitiationResponse> pay(
            Authentication authentication,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.pay(orderId, getCurrentUserId(authentication)));
    }

    @GetMapping("/{orderId}/pay/status")
    @Operation(summary = "Получить статус платежа (фронт вызывает при возврате с redirectUrl)")
    public ResponseEntity<Map<String, String>> getPaymentStatus(
            Authentication authentication,
            @PathVariable UUID orderId) {
        String status = orderService.getPaymentStatus(orderId, getCurrentUserId(authentication));
        return ResponseEntity.ok(Map.of("status", status));
    }

    @PostMapping("/{orderId}/pay/cash/confirm")
    @Operation(summary = "Зафиксировать получение наличных при доставке (менеджер)")
    public ResponseEntity<OrderDto> confirmCashPayment(@PathVariable UUID orderId) {
        Order order = orderService.confirmCashPayment(orderId);
        return ResponseEntity.ok(enrichAndMap(order));
    }

    @PostMapping("/{orderId}/refund")
    @Operation(summary = "Инициировать возврат (менеджер)")
    public ResponseEntity<OrderDto> refund(@PathVariable UUID orderId) {
        Order order = orderService.refund(orderId);
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

    @PatchMapping("/{orderId}/1c-sync")
    @Operation(summary = "Обновление статуса заказа от 1С")
    public ResponseEntity<OrderDto> syncFrom1C(@PathVariable(name = "orderId") UUID orderId,
                                               @Valid @RequestBody OrderSyncRequest request){
        return ResponseEntity.ok(OrderMapper.toDto(
                orderService.syncFrom1C(orderId, request.externalId(), request.newStatus())));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Список заказов для админа с фильтрами")
    public ResponseEntity<Page<OrderSummaryDto>> getAdminOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Order> orders = orderService.getAdminOrders(status, userId, dateFrom, dateTo, pageable);
        List<OrderSummaryDto> content = orders.getContent().stream()
                .map(OrderMapper::toSummaryDto)
                .toList();
        return ResponseEntity.ok(new PageImpl<>(content, pageable, orders.getTotalElements()));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Изменить статус заказа (только для админа)")
    public ResponseEntity<OrderDto> changeOrderStatus(
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> body) {
        OrderStatus newStatus = OrderStatus.valueOf(body.get("status"));
        Order order = orderService.updateStatus(orderId, newStatus);
        return ResponseEntity.ok(enrichAndMap(order));
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