package ru.rfsnab.orderservice.models.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Точка склада / пункт выдачи (Warehouse Point).
 * Используется как для самовывоза покупателем, так и как точка отгрузки для курьера.
 */
@Entity
@Table(name = "warehouse_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehousePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 150)
    private String street;

    @Column(nullable = false, length = 20)
    private String building;

    @Column(nullable = false, length = 10)
    private String postalCode;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Column(length = 200)
    private String workingHours;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
