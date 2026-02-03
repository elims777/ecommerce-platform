package ru.rfsnab.orderservice.models.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAddress {
    @Column(name = "delivery_city")
    private String city;

    @Column(name = "delivery_street")
    private String street;

    @Column(name = "delivery_building")
    private String building;

    @Column(name = "delivery_apartment")
    private String apartment;

    @Column(name = "delivery_postal_code")
    private String postalCode;

    @Column(name = "delivery_phone")
    private String phone;

    @Column(name = "delivery_recipient_name")
    private String recipientName;
}
