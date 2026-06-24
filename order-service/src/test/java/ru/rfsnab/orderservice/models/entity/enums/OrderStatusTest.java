package ru.rfsnab.orderservice.models.entity.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderStatus.isFinal()")
class OrderStatusTest {

    private static final Set<OrderStatus> FINAL = Set.of(
            OrderStatus.DELIVERED,
            OrderStatus.CANCELLED,
            OrderStatus.REFUNDED,
            OrderStatus.COMPLETED
    );

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED", "REFUNDED", "COMPLETED"})
    void finalStatusesAreFinal(OrderStatus status) {
        assertThat(status.isFinal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = Mode.EXCLUDE,
            names = {"DELIVERED", "CANCELLED", "REFUNDED", "COMPLETED"})
    void otherStatusesAreNotFinal(OrderStatus status) {
        assertThat(status.isFinal()).isFalse();
    }

    @org.junit.jupiter.api.Test
    void finalStatusesListContainsExactlyFinalValues() {
        List<OrderStatus> result = OrderStatus.finalStatuses();
        assertThat(result).containsExactlyInAnyOrderElementsOf(FINAL);
    }
}
