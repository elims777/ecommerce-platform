package ru.rfsnab.orderservice.models.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum DeliveryMethod {
    PICKUP("Самовывоз"),
    CARRIER_ADDRESS("Доставка ТК до двери"),
    CARRIER_TERMINAL("До терминала ТК"),
    SUPPLIER_DELIVERY("Доставка поставщиком");

    private final String displayName;

    @JsonProperty("code")
    public String getCode() {
        return name();
    }

    @JsonCreator
    public static DeliveryMethod fromValue(String value) {
        return valueOf(value.toUpperCase());
    }
}
