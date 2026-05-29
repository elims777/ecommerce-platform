package ru.rfsnab.userservice.models.dto.legal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Запрос на регистрацию юридического лица.
 */
public record RegisterLegalEntityRequest(
        @NotBlank @Size(min = 10, max = 12, message = "ИНН должен содержать 10 или 12 цифр")
        @Pattern(regexp = "\\d{10}|\\d{12}", message = "ИНН должен содержать только цифры")
        String inn,

        String ogrn,

        @NotBlank String fullName,
        String director,
        String phone,

        @NotBlank @Email String email,

        @NotBlank @Size(min = 8, max = 100) String password,

        String legalCity,
        String legalStreet,
        String legalBuilding,
        String legalPostalCode
) {}
