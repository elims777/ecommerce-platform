package ru.rfsnab.userservice.models.dto.legal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateLegalEntityRequest(
        @NotBlank @Size(max = 255, message = "Наименование не должно превышать 255 символов") String fullName,
        @NotBlank @Size(max = 255, message = "ФИО директора не должно превышать 255 символов") String director,
        @Size(max = 100, message = "Должность директора не должна превышать 100 символов") String directorTitle,
        @Size(max = 200, message = "Основание полномочий не должно превышать 200 символов") String basisOfAuthority,
        @Size(max = 50, message = "Офис не должен превышать 50 символов") String office,

        @NotBlank @Pattern(regexp = "\\+?\\d{11}", message = "Некорректный формат телефона")
        String phone,

        @NotBlank @Size(max = 150, message = "Город не должен превышать 150 символов") String legalCity,
        @NotBlank @Size(max = 150, message = "Улица не должна превышать 150 символов") String legalStreet,
        @NotBlank @Size(max = 20, message = "Номер дома не должен превышать 20 символов") String legalBuilding,

        @Size(max = 10, message = "Почтовый индекс не должен превышать 10 символов") String legalPostalCode
) {}
