package ru.rfsnab.orderservice.delivery.models.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Настройки ТК из админки. terminalDeliveryEnabled необязателен: null — флаг не меняется.
 * trackingUrlTemplate: null — не менять, пустая строка — очистить, иначе новый шаблон со вставкой {number}.
 */
public record CarrierUpdateRequest(
        @NotNull Boolean enabled,
        @NotNull Boolean addressDeliveryEnabled,
        @NotNull @Min(0) Integer sortOrder,
        Boolean terminalDeliveryEnabled,
        @Size(max = 500)
        @Pattern(regexp = "^$|https?://.*[{]number[}].*", message = "Ссылка должна начинаться с http(s):// и содержать подстановку номера накладной")
        String trackingUrlTemplate
) {
}
