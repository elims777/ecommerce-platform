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

        @NotBlank @Size(min = 13, max = 15)
        @Pattern(regexp = "\\d{13}|\\d{15}", message = "ОГРН должен содержать 13 или 15 цифр")
        String ogrn,

        @NotBlank String fullName,
        @NotBlank String director,

        @NotBlank @Pattern(regexp = "\\+?[0-9]{11}", message = "Некорректный формат телефона")
        String phone,

        @NotBlank @Email String email,

        @NotBlank @Size(min = 8, max = 100) String password,

        @NotBlank String legalCity,
        @NotBlank String legalStreet,
        @NotBlank String legalBuilding,
        String legalPostalCode
) {}
