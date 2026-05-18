package ru.rfsnab.userservice.mappers;

import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.LegalEntityAddress;
import ru.rfsnab.userservice.models.LegalEntityBankAccount;
import ru.rfsnab.userservice.models.dto.legal.*;

/**
 * Маппер для преобразования сущностей юридического лица в DTO.
 */
public class LegalEntityMapper {

    public static LegalEntityDto toDto(LegalEntity entity) {
        return new LegalEntityDto(
                entity.getId(),
                entity.getInn(),
                entity.getOgrn(),
                entity.getFullName(),
                entity.getDirector(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getLegalCity(),
                entity.getLegalStreet(),
                entity.getLegalBuilding(),
                entity.getLegalPostalCode(),
                entity.getVerificationStatus(),
                entity.getVerifiedAt(),
                entity.getBankAccounts().stream().map(LegalEntityMapper::toBankAccountDto).toList(),
                entity.getAddresses().stream().map(LegalEntityMapper::toAddressDto).toList(),
                entity.getCreatedAt()
        );
    }

    public static BankAccountDto toBankAccountDto(LegalEntityBankAccount account) {
        return new BankAccountDto(
                account.getId(),
                account.getBankName(),
                account.getBik(),
                account.getCorrespondentAccount(),
                account.getSettlementAccount(),
                account.isPrimary()
        );
    }

    public static LegalEntityAddressDto toAddressDto(LegalEntityAddress address) {
        return new LegalEntityAddressDto(
                address.getId(),
                address.getCity(),
                address.getStreet(),
                address.getBuilding(),
                address.getApartment(),
                address.getPostalCode(),
                address.isPrimary()
        );
    }
}
