package ru.rfsnab.orderservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientAddressRequest;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientAddressResponse;
import ru.rfsnab.orderservice.models.entity.RecipientAddress;

@Mapper(componentModel = "spring")
public interface RecipientAddressMapper {

    @Mapping(source = "recipient.id", target = "recipientId")
    @Mapping(source = "default", target = "isDefault")
    RecipientAddressResponse toResponse(RecipientAddress address);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "recipient", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RecipientAddress toEntity(RecipientAddressRequest request);
}