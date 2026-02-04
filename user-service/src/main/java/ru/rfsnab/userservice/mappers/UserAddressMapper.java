package ru.rfsnab.userservice.mappers;

import ru.rfsnab.userservice.models.UserAddress;
import ru.rfsnab.userservice.models.dto.SaveUserAddressRequest;
import ru.rfsnab.userservice.models.dto.UserAddressDto;

public class UserAddressMapper {
    public static UserAddressDto mapToDto(UserAddress entity){
        return UserAddressDto.builder()
                .id(entity.getId())
                .label(entity.getLabel())
                .recipientName(entity.getRecipientName())
                .phone(entity.getPhone())
                .city(entity.getCity())
                .street(entity.getStreet())
                .building(entity.getBuilding())
                .apartment(entity.getApartment())
                .entrance(entity.getEntrance())
                .floor(entity.getFloor())
                .intercomCode(entity.getIntercomCode())
                .postalCode(entity.getPostalCode())
                .deliveryInstructions(entity.getDeliveryInstructions())
                .isDefault(entity.isDefaultAddress())
                .build();
    }

    public static UserAddress mapToEntity(SaveUserAddressRequest request, Long userId){
        return UserAddress.builder()
                .userId(userId)
                .label(request.label())
                .recipientName(request.recipientName())
                .phone(request.phone())
                .city(request.city())
                .street(request.street())
                .building(request.building())
                .apartment(request.apartment())
                .entrance(request.entrance())
                .floor(request.floor())
                .intercomCode(request.intercomCode())
                .postalCode(request.postalCode())
                .deliveryInstructions(request.deliveryInstructions())
                .defaultAddress(request.defaultAddress())
                .build();
    }


}
