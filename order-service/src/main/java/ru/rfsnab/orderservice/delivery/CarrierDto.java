package ru.rfsnab.orderservice.delivery;

public record CarrierDto(
        CarrierCode code,
        String name,
        boolean enabled,
        boolean addressDeliveryEnabled,
        int sortOrder
) {
    public static CarrierDto from(Carrier carrier) {
        return new CarrierDto(carrier.getCode(), carrier.getName(),
                carrier.isEnabled(), carrier.isAddressDeliveryEnabled(), carrier.getSortOrder());
    }
}
