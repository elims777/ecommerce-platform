package ru.rfsnab.orderservice.delivery;

import jakarta.validation.constraints.NotBlank;

/** Привязка отправления к заказу сайта по номеру заказа. */
public record LinkShipmentRequest(@NotBlank String orderNumber) {
}
