package ru.rfsnab.orderservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderService.isTransitionAllowed — переходы через ARRIVED_AT_TERMINAL")
class OrderTransitionsTest {

    @Test
    @DisplayName("SHIPPED и IN_TRANSIT → ARRIVED_AT_TERMINAL, ARRIVED_AT_TERMINAL → DELIVERED")
    void arrivedAtTerminalTransitions() {
        assertThat(OrderService.isTransitionAllowed(OrderStatus.SHIPPED, OrderStatus.ARRIVED_AT_TERMINAL)).isTrue();
        assertThat(OrderService.isTransitionAllowed(OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED_AT_TERMINAL)).isTrue();
        assertThat(OrderService.isTransitionAllowed(OrderStatus.ARRIVED_AT_TERMINAL, OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    @DisplayName("назад и в сторону из ARRIVED_AT_TERMINAL нельзя; старые переходы на месте")
    void noBackwardsAndOldTransitionsIntact() {
        assertThat(OrderService.isTransitionAllowed(OrderStatus.ARRIVED_AT_TERMINAL, OrderStatus.IN_TRANSIT)).isFalse();
        assertThat(OrderService.isTransitionAllowed(OrderStatus.ARRIVED_AT_TERMINAL, OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderService.isTransitionAllowed(OrderStatus.ARRIVED_AT_TERMINAL, OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderService.isTransitionAllowed(OrderStatus.PAID, OrderStatus.ARRIVED_AT_TERMINAL)).isFalse();
        assertThat(OrderService.isTransitionAllowed(OrderStatus.SHIPPED, OrderStatus.IN_TRANSIT)).isTrue();
        assertThat(OrderService.isTransitionAllowed(OrderStatus.SHIPPED, OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderService.isTransitionAllowed(OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderService.isTransitionAllowed(OrderStatus.PROCESSING, OrderStatus.SHIPPED)).isFalse();
    }

    @Test
    @DisplayName("ARRIVED_AT_TERMINAL имеет подпись «Прибыл на терминал» и не финальный")
    void displayNameAndNotFinal() {
        assertThat(OrderStatus.ARRIVED_AT_TERMINAL.getDisplayName()).isEqualTo("Прибыл на терминал");
        assertThat(OrderStatus.ARRIVED_AT_TERMINAL.isFinal()).isFalse();
    }
}
