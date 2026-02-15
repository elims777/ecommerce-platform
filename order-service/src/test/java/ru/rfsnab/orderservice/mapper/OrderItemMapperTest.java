package ru.rfsnab.orderservice.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.rfsnab.orderservice.models.dto.order.OrderItemDto;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.OrderItem;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderItemMapper")
class OrderItemMapperTest {

    @Nested
    @DisplayName("toDto")
    class ToDtoTests {

        @Test
        @DisplayName("маппит OrderItem в OrderItemDto с расчётом subtotal")
        void shouldMapOrderItemToDto() {
            OrderItem item = OrderItem.builder()
                    .productId(1L)
                    .productName("Доска обрезная 50x150")
                    .quantity(10)
                    .price(new BigDecimal("1500.00"))
                    .build();

            OrderItemDto dto = OrderItemMapper.toDto(item);

            assertThat(dto.productId()).isEqualTo(1L);
            assertThat(dto.productName()).isEqualTo("Доска обрезная 50x150");
            assertThat(dto.quantity()).isEqualTo(10);
            assertThat(dto.price()).isEqualByComparingTo("1500.00");
            assertThat(dto.subtotal()).isEqualByComparingTo("15000.00");
        }

        @Test
        @DisplayName("subtotal корректен при quantity = 1")
        void shouldCalculateSubtotalForSingleItem() {
            OrderItem item = OrderItem.builder()
                    .productId(2L)
                    .productName("Брус 100x100")
                    .quantity(1)
                    .price(new BigDecimal("3200.50"))
                    .build();

            OrderItemDto dto = OrderItemMapper.toDto(item);

            assertThat(dto.subtotal()).isEqualByComparingTo("3200.50");
        }
    }

    @Nested
    @DisplayName("toDtoList")
    class ToDtoListTests {

        @Test
        @DisplayName("маппит список OrderItem в список OrderItemDto")
        void shouldMapListToDto() {
            List<OrderItem> items = List.of(
                    OrderItem.builder()
                            .productId(1L).productName("Товар 1")
                            .quantity(2).price(new BigDecimal("100.00")).build(),
                    OrderItem.builder()
                            .productId(2L).productName("Товар 2")
                            .quantity(5).price(new BigDecimal("200.00")).build()
            );

            List<OrderItemDto> dtos = OrderItemMapper.toDtoList(items);

            assertThat(dtos).hasSize(2);
            assertThat(dtos.get(0).subtotal()).isEqualByComparingTo("200.00");
            assertThat(dtos.get(1).subtotal()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("возвращает пустой список для null")
        void shouldReturnEmptyListForNull() {
            assertThat(OrderItemMapper.toDtoList(null)).isEmpty();
        }

        @Test
        @DisplayName("возвращает пустой список для пустого списка")
        void shouldReturnEmptyListForEmptyList() {
            assertThat(OrderItemMapper.toDtoList(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("toEntity")
    class ToEntityTests {

        @Test
        @DisplayName("маппит OrderItemDto в OrderItem с привязкой к Order")
        void shouldMapDtoToEntity() {
            Order order = Order.builder().build();
            OrderItemDto dto = new OrderItemDto(1L, "Доска", 10,
                    new BigDecimal("1500.00"), new BigDecimal("15000.00"));

            OrderItem entity = OrderItemMapper.toEntity(dto, order);

            assertThat(entity.getOrder()).isSameAs(order);
            assertThat(entity.getProductId()).isEqualTo(1L);
            assertThat(entity.getProductName()).isEqualTo("Доска");
            assertThat(entity.getQuantity()).isEqualTo(10);
            assertThat(entity.getPrice()).isEqualByComparingTo("1500.00");
        }
    }

    @Nested
    @DisplayName("toEntityList")
    class ToEntityListTests {

        @Test
        @DisplayName("маппит список DTO в список entity")
        void shouldMapDtoListToEntityList() {
            Order order = Order.builder().build();
            List<OrderItemDto> dtos = List.of(
                    new OrderItemDto(1L, "Товар 1", 2,
                            new BigDecimal("100.00"), new BigDecimal("200.00")),
                    new OrderItemDto(2L, "Товар 2", 3,
                            new BigDecimal("300.00"), new BigDecimal("900.00"))
            );

            List<OrderItem> entities = OrderItemMapper.toEntityList(dtos, order);

            assertThat(entities).hasSize(2);
            assertThat(entities).allSatisfy(item ->
                    assertThat(item.getOrder()).isSameAs(order));
        }

        @Test
        @DisplayName("возвращает пустой список для null")
        void shouldReturnEmptyListForNull() {
            assertThat(OrderItemMapper.toEntityList(null, Order.builder().build())).isEmpty();
        }
    }
}