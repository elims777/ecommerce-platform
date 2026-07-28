package ru.rfsnab.orderservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.orderservice.mapper.OrderDocumentMapper;
import ru.rfsnab.orderservice.mapper.OrderMapper;
import ru.rfsnab.orderservice.models.dto.order.OrderDocumentDto;
import ru.rfsnab.orderservice.models.dto.order.OrderDto;
import ru.rfsnab.orderservice.models.dto.order.OrderSummaryDto;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.OrderDocument;
import ru.rfsnab.orderservice.models.entity.enums.OrderDocumentType;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.service.OrderDocumentService;
import ru.rfsnab.orderservice.service.OrderService;
import ru.rfsnab.orderservice.service.WarehousePointService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;
    private final WarehousePointService warehousePointService;
    private final OrderDocumentService orderDocumentService;

    @GetMapping
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

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getAdminOrder(@PathVariable UUID orderId) {
        Order order = orderService.getOrder(orderId);
        return ResponseEntity.ok(enrichAndMap(order));
    }

    @GetMapping("/active-count")
    public ResponseEntity<Map<String, Long>> activeCount(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String inn) {
        if ((userId == null) == (inn == null)) {
            return ResponseEntity.badRequest().build();
        }
        long count = userId != null
                ? orderService.countActiveByUserId(userId)
                : orderService.countActiveByInn(inn);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderDto> changeOrderStatus(
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> body) {
        String raw = body.get("status");
        if (raw == null || raw.isBlank()) {
            return ResponseEntity.<OrderDto>badRequest().build();
        }
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.<OrderDto>badRequest().build();
        }
        Order order = orderService.updateStatus(orderId, newStatus);
        return ResponseEntity.ok(enrichAndMap(order));
    }

    // ==================== Документы заказа ====================

    @PostMapping("/{orderId}/documents")
    public ResponseEntity<OrderDocumentDto> uploadDocument(
            @PathVariable UUID orderId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") OrderDocumentType type,
            Authentication authentication) {
        OrderDocument document = orderDocumentService.upload(orderId, type, file, getCurrentUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderDocumentMapper.toDto(document));
    }

    @GetMapping("/{orderId}/documents")
    public ResponseEntity<List<OrderDocumentDto>> listDocuments(@PathVariable UUID orderId) {
        List<OrderDocumentDto> documents = orderDocumentService.listForOrder(orderId).stream()
                .map(OrderDocumentMapper::toDto)
                .toList();
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{orderId}/documents/{docId}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable UUID orderId,
            @PathVariable UUID docId) {
        OrderDocumentService.DownloadResult result = orderDocumentService.download(orderId, docId);
        return buildDownloadResponse(result);
    }

    @DeleteMapping("/{orderId}/documents/{docId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID orderId,
            @PathVariable UUID docId) {
        orderDocumentService.delete(orderId, docId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<InputStreamResource> buildDownloadResponse(OrderDocumentService.DownloadResult result) {
        OrderDocument document = result.document();
        MediaType mediaType = document.getContentType() != null
                ? MediaType.parseMediaType(document.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(document.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(document.getOriginalFileName(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(new InputStreamResource(result.stream()));
    }

    private Long getCurrentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }

    private OrderDto enrichAndMap(Order order) {
        var warehousePoint = order.getWarehousePointId() != null
                ? warehousePointService.getActivePoint(order.getWarehousePointId())
                : null;
        return OrderMapper.toDto(order, warehousePoint);
    }
}
