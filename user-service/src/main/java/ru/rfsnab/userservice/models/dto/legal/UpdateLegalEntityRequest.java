package ru.rfsnab.userservice.models.dto.legal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateLegalEntityRequest(
        @NotBlank String fullName,
        @NotBlank String director,
        String directorTitle,
        String basisOfAuthority,
        String office,

        @NotBlank @Pattern(regexp = "\\+?[0-9]{11}", message = "Некорректный формат телефона")
        String phone,

        @NotBlank String legalCity,
        @NotBlank String legalStreet,
        @NotBlank String legalBuilding,

        @Size(max = 10) String legalPostalCode
) {}
