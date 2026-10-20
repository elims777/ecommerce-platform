package ru.rfsnab.orderservice.delivery.models.dto;

import jakarta.validation.constraints.NotBlank;

/** Привязка отправления к заказу сайта по номеру заказа. */
public record LinkShipmentRequest(@NotBlank String orderNumber) {
}
