package ru.rfsnab.userservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Сохранённый адрес пользователя (Address Book).
 * Покупатель может хранить несколько адресов с метками ("Дом", "Работа" и т.д.)
 * и выбирать нужный при оформлении заказа.
 */
@Entity
@Table(name = "user_addresses",
        uniqueConstraints = @UniqueConstraint(
                name = "user_addresses_label",
                columnNames = {"user_id", "lable"}
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false, length = 100)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 150)
    private String street;

    @Column(nullable = false, length = 20)
    private String building;

    @Column(length = 20)
    private String apartment;

    @Column(length = 20)
    private String entrance;

    @Column(length = 10)
    private String floor;

    @Column(length = 50)
    private String intercomCode;

    @Column(length = 10)
    private String postalCode;

    @Column(length = 500)
    private String deliveryInstructions;

    @Column(nullable = false)
    @Builder.Default
    private boolean defaultAddress = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
