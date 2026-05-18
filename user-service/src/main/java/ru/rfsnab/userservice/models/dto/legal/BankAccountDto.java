package ru.rfsnab.userservice.models.dto.legal;

/**
 * DTO банковского счёта юридического лица.
 */
public record BankAccountDto(
        Long id,
        String bankName,
        String bik,
        String correspondentAccount,
        String settlementAccount,
        boolean primary
) {}
