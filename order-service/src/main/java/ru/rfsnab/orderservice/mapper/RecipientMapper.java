package ru.rfsnab.orderservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientRequest;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientResponse;
import ru.rfsnab.orderservice.models.entity.Recipient;

@Mapper(componentModel = "spring")
public interface RecipientMapper {
    @Mapping(source = "default", target = "isDefault")
    RecipientResponse toResponse(Recipient recipient);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "addresses", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Recipient toEntity(RecipientRequest request);
}
