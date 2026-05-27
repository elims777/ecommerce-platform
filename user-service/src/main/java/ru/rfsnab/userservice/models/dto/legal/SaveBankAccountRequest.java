package ru.rfsnab.userservice.models.dto.legal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Запрос на добавление банковского счёта юридического лица.
 */
public record SaveBankAccountRequest(
        @NotBlank String bankName,
        @NotBlank @Size(min = 9, max = 9) @Pattern(regexp = "\\d{9}") String bik,
        @NotBlank @Size(min = 20, max = 20) String correspondentAccount,
        @NotBlank @Size(min = 20, max = 20) String settlementAccount,
        boolean primary
) {}
