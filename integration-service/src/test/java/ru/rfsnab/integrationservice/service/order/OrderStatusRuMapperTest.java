package ru.rfsnab.integrationservice.service.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusRuMapperTest {

    @ParameterizedTest
    @CsvSource({
            "'Заказ создан',            CREATED",
            "'В работе',                PROCESSING",
            "'Счёт выставлен',          INVOICE_SENT",
            "'Счет выставлен',          INVOICE_SENT",
            "'Ожидает оплаты',          PENDING_PAYMENT",
            "'Ожидает подтверждения',   AWAITING_CONFIRMATION",
            "'Оплачен',                 PAID",
            "'Оплачен частично',        PARTIALLY_PAID",
            "'Ошибка оплаты',           PAYMENT_FAILED",
            "'Отгружен',                SHIPPED",
            "'В пути',                  IN_TRANSIT",
            "'Доставлен',               DELIVERED",
            "'Отменён',                 CANCELLED",
            "'Отменен',                 CANCELLED",
            "'Возврат средств',         REFUNDED",
            "'Завершён',                COMPLETED",
            "'Завершен',                COMPLETED"
    })
    void shouldMapAllUnfStatusesToCode(String russian, String expectedCode) {
        assertThat(OrderStatusRuMapper.toCode(russian)).isEqualTo(expectedCode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  оплачен  ", "ОПЛАЧЕН", "Оплачен", "оплачен"})
    void shouldBeCaseInsensitiveAndTrim(String input) {
        assertThat(OrderStatusRuMapper.toCode(input)).isEqualTo("PAID");
    }

    @Test
    void shouldThrowForUnknownStatus() {
        assertThatThrownBy(() -> OrderStatusRuMapper.toCode("Принят"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неизвестный статус")
                .hasMessageContaining("Принят");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldThrowForNullOrBlank(String input) {
        assertThatThrownBy(() -> OrderStatusRuMapper.toCode(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Пустое значение");
    }
}
