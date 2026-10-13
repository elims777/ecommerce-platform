package ru.rfsnab.orderservice.delivery;

/**
 * Точка выдачи ТК для клиента (карта и список при оформлении заказа).
 */
public record CarrierTerminalDto(
        Long id,
        CarrierCode carrierCode,
        PointType pointType,
        String city,
        String name,
        String address,
        double latitude,
        double longitude,
        String workingHours,
        String phone
) {
    /** Собирает DTO из сущности. */
    public static CarrierTerminalDto from(CarrierTerminal t) {
        return new CarrierTerminalDto(t.getId(), t.getCarrierCode(), t.getPointType(), t.getCity(), t.getName(),
                t.getAddress(), t.getLatitude().doubleValue(), t.getLongitude().doubleValue(),
                t.getWorkingHours(), t.getPhone());
    }
}
