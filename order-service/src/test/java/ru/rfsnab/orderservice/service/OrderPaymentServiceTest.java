package ru.rfsnab.orderservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rfsnab.orderservice.BaseServiceIntegrationTest;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;
import ru.rfsnab.orderservice.models.dto.order.AddressDto;
import ru.rfsnab.orderservice.models.dto.order.CreateOrderRequest;
import ru.rfsnab.orderservice.models.dto.payment.PaymentInitiationResponse;
import ru.rfsnab.orderservice.models.dto.payment.PaymentLinkResponse;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;
import ru.rfsnab.orderservice.models.entity.PaymentMethodSettings;
import ru.rfsnab.orderservice.repository.OrderRepository;
import ru.rfsnab.orderservice.repository.PaymentMethodSettingsRepository;
import ru.rfsnab.orderservice.service.client.PaymentServiceClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("OrderService — payment flow tests")
class OrderPaymentServiceTest extends BaseServiceIntegrationTest {

    @Autowired OrderService orderService;
    @Autowired CartService cartService;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentMethodSettingsRepository paymentMethodSettingsRepository;

    @MockitoBean
    PaymentServiceClient paymentServiceClient;

    private static final Long USER_ID = 200L;
    private static final Long PRODUCT_ID = 5L;
    private static final String USER_EMAIL = "pay@test.com";

    private static final ProductDto PRODUCT = new ProductDto(
            PRODUCT_ID, "Болт М8", new BigDecimal("50.00"),
            new BigDecimal("40.00"), 500, true, "ext-005");

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartService.clearCart(USER_ID);

        PaymentMethodSettings settings = paymentMethodSettingsRepository.findById(1L)
                .orElseGet(() -> {
                    PaymentMethodSettings s = new PaymentMethodSettings();
                    s.setId(1L);
                    return s;
                });
        settings.setCardEnabled(true);
        settings.setSbpEnabled(true);
        paymentMethodSettingsRepository.save(settings);

        when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(PRODUCT_ID, PRODUCT));
        when(productServiceClient.getProduct(PRODUCT_ID)).thenReturn(PRODUCT);
    }

    // ============================================================
    // pay() — CARD
    // ============================================================

    @Nested
    @DisplayName("pay() — CARD")
    class PayCard {

        @Test
        @DisplayName("B2C CARD из PROCESSING → статус PENDING_PAYMENT, возвращает paymentLink")
        void b2c_card_processing_returnsLink() {
            Order order = createAndConfirmOrder(PaymentMethod.CARD, "B2C");

            when(paymentServiceClient.createPayment(any()))
                    .thenReturn(new PaymentLinkResponse("https://pay.link/card", "op-1", "PENDING"));

            PaymentInitiationResponse resp = orderService.pay(order.getId(), USER_ID);

            assertThat(resp.paymentLink()).isEqualTo("https://pay.link/card");
            assertThat(resp.paymentMode()).isEqualTo(PaymentMethod.CARD);
            assertThat(resp.orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

            Order saved = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("CARD передаёт order с paymentMethod=CARD в PaymentServiceClient")
        void b2c_card_sendsCorrectPaymentMode() {
            Order order = createAndConfirmOrder(PaymentMethod.CARD, "B2C");
            when(paymentServiceClient.createPayment(any()))
                    .thenReturn(new PaymentLinkResponse("https://pay.link/card", "op-1", "PENDING"));

            orderService.pay(order.getId(), USER_ID);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(paymentServiceClient).createPayment(captor.capture());
            assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        }

        @Test
        @DisplayName("CARD повторный вызов из PENDING_PAYMENT — не дублирует смену статуса")
        void b2c_card_idempotent_fromPendingPayment() {
            Order order = createAndConfirmOrder(PaymentMethod.CARD, "B2C");
            when(paymentServiceClient.createPayment(any()))
                    .thenReturn(new PaymentLinkResponse("https://pay.link/card", "op-1", "PENDING"));

            orderService.pay(order.getId(), USER_ID); // PROCESSING → PENDING_PAYMENT
            orderService.pay(order.getId(), USER_ID); // уже PENDING_PAYMENT — только createPayment

            verify(paymentServiceClient, times(2)).createPayment(any());
            Order saved = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }
    }

    // ============================================================
    // pay() — SBP
    // ============================================================

    @Nested
    @DisplayName("pay() — SBP")
    class PaySbp {

        @Test
        @DisplayName("B2C SBP из PROCESSING → вызывает payment-service с SBP mode")
        void b2c_sbp_processing_callsPaymentServiceWithSbpMode() {
            Order order = createAndConfirmOrder(PaymentMethod.SBP, "B2C");
            when(paymentServiceClient.createPayment(any()))
                    .thenReturn(new PaymentLinkResponse("https://pay.link/sbp", "op-sbp", "PENDING"));

            PaymentInitiationResponse resp = orderService.pay(order.getId(), USER_ID);

            assertThat(resp.paymentLink()).isEqualTo("https://pay.link/sbp");
            assertThat(resp.paymentMode()).isEqualTo(PaymentMethod.SBP);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(paymentServiceClient).createPayment(captor.capture());
            assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.SBP);
        }
    }

    // ============================================================
    // pay() — CASH_ON_DELIVERY
    // ============================================================

    @Nested
    @DisplayName("pay() — CASH_ON_DELIVERY")
    class PayCashOnDelivery {

        @Test
        @DisplayName("CASH_ON_DELIVERY не вызывает payment-service, paymentLink == null")
        void cod_doesNotCallPaymentService_paymentLinkNull() {
            Order order = createAndConfirmOrder(PaymentMethod.CASH_ON_DELIVERY, "B2C");

            PaymentInitiationResponse resp = orderService.pay(order.getId(), USER_ID);

            verifyNoInteractions(paymentServiceClient);
            assertThat(resp.paymentLink()).isNull();
            assertThat(resp.paymentMode()).isEqualTo(PaymentMethod.CASH_ON_DELIVERY);
            assertThat(resp.orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("CASH_ON_DELIVERY меняет статус заказа на PENDING_PAYMENT")
        void cod_setsOrderStatusToPendingPayment() {
            Order order = createAndConfirmOrder(PaymentMethod.CASH_ON_DELIVERY, "B2C");

            orderService.pay(order.getId(), USER_ID);

            Order saved = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }
    }

    // ============================================================
    // pay() — статусная валидация
    // ============================================================

    @Nested
    @DisplayName("pay() — status validation")
    class PayStatusValidation {

        @Test
        @DisplayName("B2C из CREATED выбрасывает исключение — нужен confirmOrder")
        void b2c_fromCreated_throws() {
            Order order = createOrder(PaymentMethod.CARD, "B2C");

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.pay(orderId, USER_ID))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("невозможна");
        }

        @Test
        @DisplayName("B2B из CREATED выбрасывает исключение")
        void b2b_fromCreated_throws() {
            Order order = createOrder(PaymentMethod.CARD, "B2B");

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.pay(orderId, USER_ID))
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("B2B CARD из INVOICE_SENT разрешено")
        void b2b_card_fromInvoiceSent_succeeds() {
            Order order = createAndConfirmOrder(PaymentMethod.CARD, "B2B");
            orderService.updateStatus(order.getId(), OrderStatus.INVOICE_SENT);

            when(paymentServiceClient.createPayment(any()))
                    .thenReturn(new PaymentLinkResponse("https://pay.link/b2b", "op-b2b", "PENDING"));

            PaymentInitiationResponse resp = orderService.pay(order.getId(), USER_ID);
            assertThat(resp.paymentMode()).isEqualTo(PaymentMethod.CARD);
        }

        @Test
        @DisplayName("B2C из INVOICE_SENT выбрасывает исключение")
        void b2c_fromInvoiceSent_throws() {
            Order order = createAndConfirmOrder(PaymentMethod.CARD, "B2C");
            orderService.updateStatus(order.getId(), OrderStatus.INVOICE_SENT);

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.pay(orderId, USER_ID))
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("Retry после PAYMENT_FAILED разрешён для B2C")
        void b2c_retryAfterPaymentFailed_succeeds() {
            Order order = createAndConfirmOrder(PaymentMethod.CARD, "B2C");
            orderService.updateStatus(order.getId(), OrderStatus.PENDING_PAYMENT);
            orderService.failPayment(order.getId());

            when(paymentServiceClient.createPayment(any()))
                    .thenReturn(new PaymentLinkResponse("https://pay.link/retry", "op-retry", "PENDING"));

            PaymentInitiationResponse resp = orderService.pay(order.getId(), USER_ID);
            assertThat(resp.paymentLink()).isEqualTo("https://pay.link/retry");
        }

        @Test
        @DisplayName("Чужой userId не может инициировать оплату")
        void wrongUser_throws() {
            Order order = createAndConfirmOrder(PaymentMethod.CARD, "B2C");

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.pay(orderId, 999L))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("Нет доступа");
        }
    }

    // ============================================================
    // confirmCashPayment()
    // ============================================================

    @Nested
    @DisplayName("confirmCashPayment()")
    class ConfirmCashPayment {

        @Test
        @DisplayName("CASH_ON_DELIVERY PENDING_PAYMENT → PAID, вызывает recordCashPayment")
        void cod_pendingPayment_setsPaid() {
            Order order = createAndConfirmOrder(PaymentMethod.CASH_ON_DELIVERY, "B2C");
            orderService.pay(order.getId(), USER_ID);

            Order confirmed = orderService.confirmCashPayment(order.getId());

            assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(paymentServiceClient).recordCashPayment(eq(order.getId()), any(BigDecimal.class));
        }

        @Test
        @DisplayName("CARD заказ выбрасывает исключение на confirmCashPayment")
        void cardOrder_throws() {
            Order order = createAndConfirmOrder(PaymentMethod.CARD, "B2C");
            orderService.updateStatus(order.getId(), OrderStatus.PENDING_PAYMENT);

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.confirmCashPayment(orderId))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("cash-on-delivery");
        }
    }

    // ============================================================
    // refund()
    // ============================================================

    @Nested
    @DisplayName("refund()")
    class Refund {

        @Test
        @DisplayName("PAID заказ → REFUNDED, вызывает refundPayment")
        void paidOrder_setsRefunded() {
            Order order = createAndConfirmOrder(PaymentMethod.CARD, "B2C");
            orderService.updateStatus(order.getId(), OrderStatus.PENDING_PAYMENT);
            orderService.confirmPayment(order.getId());

            Order refunded = orderService.refund(order.getId());

            assertThat(refunded.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            verify(paymentServiceClient).refundPayment(order.getId());
        }

        @Test
        @DisplayName("PROCESSING заказ → выбрасывает исключение")
        void processingOrder_throws() {
            Order order = createAndConfirmOrder(PaymentMethod.CARD, "B2C");

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.refund(orderId))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("Возврат");
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Order createOrder(PaymentMethod paymentMethod, String clientType) {
        cartService.addItemToCart(USER_ID, PRODUCT_ID, 2);
        CreateOrderRequest request = new CreateOrderRequest(
                paymentMethod, DeliveryMethod.SUPPLIER_DELIVERY,
                AddressDto.builder()
                        .city("Москва").street("Тверская").building("1")
                        .phone("+79001234567").recipientName("Тест").build(),
                null, null, null, null,
                "B2B".equals(clientType) ? "ООО Тест" : null,
                "B2B".equals(clientType) ? "1234567890" : null);
        return orderService.createOrder(USER_ID, USER_EMAIL, clientType, request);
    }

    private Order createAndConfirmOrder(PaymentMethod paymentMethod, String clientType) {
        Order order = createOrder(paymentMethod, clientType);
        return orderService.confirmOrder(order.getId(), USER_ID);
    }
}
