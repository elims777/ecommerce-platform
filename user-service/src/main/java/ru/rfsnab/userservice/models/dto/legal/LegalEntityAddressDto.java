package ru.rfsnab.userservice.models.dto.legal;

/**
 * DTO адреса доставки юридического лица.
 */
public record LegalEntityAddressDto(
        Long id,
        String city,
        String street,
        String building,
        String apartment,
        String postalCode,
        boolean primary
) {}
