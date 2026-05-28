package ru.rfsnab.userservice.models.dto.legal;

import ru.rfsnab.userservice.models.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO юридического лица — используется в ответах API.
 */
public record LegalEntityDto(
        Long id,
        String inn,
        String ogrn,
        String fullName,
        String director,
        String directorTitle,
        String basisOfAuthority,
        String office,
        String phone,
        String email,
        String legalCity,
        String legalStreet,
        String legalBuilding,
        String legalPostalCode,
        VerificationStatus verificationStatus,
        LocalDateTime verifiedAt,
        List<BankAccountDto> bankAccounts,
        List<LegalEntityAddressDto> addresses,
        LocalDateTime createdAt
) {}
