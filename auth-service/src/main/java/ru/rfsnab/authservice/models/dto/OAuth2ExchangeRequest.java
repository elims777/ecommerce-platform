package ru.rfsnab.authservice.models.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuth2ExchangeRequest(
        @NotBlank String code
) {}
