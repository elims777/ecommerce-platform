package ru.rfsnab.orderservice.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.rfsnab.orderservice.models.dto.order.AddressDto;
import ru.rfsnab.orderservice.models.dto.order.CreateOrderRequest;
import ru.rfsnab.orderservice.models.dto.order.OrderDto;
import ru.rfsnab.orderservice.models.dto.order.OrderSummaryDto;
import ru.rfsnab.orderservice.models.entity.DeliveryAddress;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.OrderItem;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderMapper")
class OrderMapperTest {

    @Nested
    @DisplayName("toDto")
    class ToDtoTests {

        @Test
        @DisplayName("маппит Order с DeliveryAddress в OrderDto")
        void shouldMapOrderWithAddressToDto() {
            Order order = buildDeliveryOrder();

            OrderDto dto = OrderMapper.toDto(order);

            assertThat(dto.id()).isEqualTo(order.getId());
            assertThat(dto.userId()).isEqualTo(100L);
            assertThat(dto.orderNumber()).isEqualTo("ABC-00001");
            assertThat(dto.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(dto.paymentMethod()).isEqualTo(PaymentMethod.CARD);
            assertThat(dto.deliveryMethod()).isEqualTo(DeliveryMethod.SUPPLIER_DELIVERY);
            assertThat(dto.totalAmount()).isEqualByComparingTo("15000.00");
            assertThat(dto.items()).hasSize(1);
            assertThat(dto.deliveryAddress()).isNotNull();
            assertThat(dto.deliveryAddress().city()).isEqualTo("Сыктывкар");
            assertThat(dto.warehousePoint()).isNull();
            assertThat(dto.trackingNumber()).isEqualTo("TRACK-123");
        }

        @Test
        @DisplayName("маппит Order с WarehousePoint в OrderDto")
        void shouldMapOrderWithWarehousePointToDto() {
            Order order = buildPickupOrder();
            WarehousePoint point = buildWarehousePoint();

            OrderDto dto = OrderMapper.toDto(order, point);

            assertThat(dto.deliveryAddress()).isNull();
            assertThat(dto.warehousePoint()).isNotNull();
            assertThat(dto.warehousePoint().name()).isEqualTo("Склад РФСнаб");
        }

        @Test
        @DisplayName("overload без WarehousePoint — warehousePoint = null")
        void shouldMapWithoutWarehousePoint() {
            Order order = buildPickupOrder();

            OrderDto dto = OrderMapper.toDto(order);

            assertThat(dto.warehousePoint()).isNull();
        }

        @Test
        @DisplayName("маппит пустой список items")
        void shouldMapEmptyItems() {
            Order order = buildDeliveryOrder();
            order.setItems(new ArrayList<>());

            OrderDto dto = OrderMapper.toDto(order);

            assertThat(dto.items()).isEmpty();
        }
    }

    @Nested
    @DisplayName("toSummaryDto")
    class ToSummaryDtoTests {

        @Test
        @DisplayName("маппит Order в OrderSummaryDto")
        void shouldMapToSummaryDto() {
            Order order = buildDeliveryOrder();

            OrderSummaryDto dto = OrderMapper.toSummaryDto(order);

            assertThat(dto.id()).isEqualTo(order.getId());
            assertThat(dto.orderNumber()).isEqualTo("ABC-00001");
            assertThat(dto.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(dto.itemsCount()).isEqualTo(1);
            assertThat(dto.totalAmount()).isEqualByComparingTo("15000.00");
        }
    }

    @Nested
    @DisplayName("toEntity")
    class ToEntityTests {

        @Test
        @DisplayName("маппит CreateOrderRequest для DELIVERY в Order")
        void shouldMapDeliveryRequestToEntity() {
            AddressDto address = AddressDto.builder()
                    .city("Сыктывкар").street("Октябрьский проспект")
                    .building("1").phone("+79001234567")
                    .recipientName("Иванов Иван").build();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    address, null, "Комментарий");

            Order order = OrderMapper.toEntity(100L, request);

            assertThat(order.getUserId()).isEqualTo(100L);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
            assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.SUPPLIER_DELIVERY);
            assertThat(order.getDeliveryAddress()).isNotNull();
            assertThat(order.getDeliveryAddress().getCity()).isEqualTo("Сыктывкар");
            assertThat(order.getWarehousePointId()).isNull();
            assertThat(order.getComment()).isEqualTo("Комментарий");
        }

        @Test
        @DisplayName("маппит CreateOrderRequest для PICKUP в Order")
        void shouldMapPickupRequestToEntity() {
            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CASH_ON_DELIVERY, DeliveryMethod.PICKUP,
                    null, 1L, null);

            Order order = OrderMapper.toEntity(100L, request);

            assertThat(order.getWarehousePointId()).isEqualTo(1L);
            assertThat(order.getDeliveryAddress()).isNull();
            assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        }

        @Test
        @DisplayName("items и totalAmount не заполняются маппером")
        void shouldNotPopulateItemsAndTotal() {
            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.SBP, DeliveryMethod.PICKUP,
                    null, 1L, null);

            Order order = OrderMapper.toEntity(100L, request);

            assertThat(order.getItems()).isEmpty();
            assertThat(order.getTotalAmount()).isNull();
            assertThat(order.getOrderNumber()).isNull();
        }
    }

    // ==================== Test data builders ====================

    private Order buildDeliveryOrder() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(100L)
                .orderNumber("ABC-00001")
                .status(OrderStatus.CREATED)
                .paymentMethod(PaymentMethod.CARD)
                .deliveryMethod(DeliveryMethod.SUPPLIER_DELIVERY)
                .totalAmount(new BigDecimal("15000.00"))
                .deliveryAddress(DeliveryAddress.builder()
                        .city("Сыктывкар").street("Октябрьский проспект")
                        .building("1").phone("+79001234567")
                        .recipientName("Иванов Иван").build())
                .trackingNumber("TRACK-123")
                .comment("Тестовый заказ")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        OrderItem item = OrderItem.builder()
                .productId(1L).productName("Доска обрезная")
                .quantity(10).price(new BigDecimal("1500.00"))
                .order(order).build();
        order.setItems(new ArrayList<>(List.of(item)));

        return order;
    }

    private Order buildPickupOrder() {
        return Order.builder()
                .id(UUID.randomUUID())
                .userId(100L)
                .orderNumber("ABC-00002")
                .status(OrderStatus.CREATED)
                .paymentMethod(PaymentMethod.CASH_ON_DELIVERY)
                .deliveryMethod(DeliveryMethod.PICKUP)
                .totalAmount(new BigDecimal("5000.00"))
                .warehousePointId(1L)
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private WarehousePoint buildWarehousePoint() {
        return WarehousePoint.builder()
                .id(1L).name("Склад РФСнаб")
                .city("Сыктывкар").street("Октябрьский проспект")
                .building("1").postalCode("167000")
                .phoneNumber("+7 (8212) 00-00-00")
                .workingHours("Пн-Пт: 9:00-18:00")
                .active(true).build();
    }
}