package ru.rfsnab.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.paymentservice.models.dto.*;
import ru.rfsnab.paymentservice.service.PaymentService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Internal payment API — requires X-Internal-Token")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Create payment link via Tochka")
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(request));
    }

    @GetMapping("/{orderId}/status")
    @Operation(summary = "Get payment status (polls Tochka if PENDING)")
    public ResponseEntity<PaymentStatusResponse> getStatus(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getStatus(orderId));
    }

    @PostMapping("/{orderId}/cash")
    @Operation(summary = "Record cash payment (manager only)")
    public ResponseEntity<Void> recordCash(@PathVariable UUID orderId,
                                           @Valid @RequestBody CashPaymentRequest request) {
        paymentService.recordCashPayment(orderId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{orderId}/refund")
    @Operation(summary = "Initiate full refund via Tochka")
    public ResponseEntity<Void> refund(@PathVariable UUID orderId) {
        paymentService.refundPayment(orderId);
        return ResponseEntity.ok().build();
    }
}
