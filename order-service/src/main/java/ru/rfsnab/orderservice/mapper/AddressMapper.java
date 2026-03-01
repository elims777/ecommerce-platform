package ru.rfsnab.orderservice.mapper;

import ru.rfsnab.orderservice.models.dto.order.AddressDto;
import ru.rfsnab.orderservice.models.entity.DeliveryAddress;

public class AddressMapper {
    public static AddressDto mapToAddressDto (DeliveryAddress address){
        return AddressDto.builder()
                .city(address.getCity())
                .street(address.getStreet())
                .building(address.getBuilding())
                .apartment(address.getApartment())
                .postalCode(address.getPostalCode())
                .phone(address.getPhone())
                .recipientName(address.getRecipientName())
                .build();
    }

    public static DeliveryAddress mapToDeliveryAddress (AddressDto dto){
        return DeliveryAddress.builder()
                .city(dto.city())
                .street(dto.street())
                .building(dto.building())
                .apartment(dto.apartment())
                .postalCode(dto.postalCode())
                .phone(dto.phone())
                .recipientName(dto.recipientName())
                .build();
    }
}
