package ru.rfsnab.userservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Фактический адрес доставки юридического лица.
 * Юрлицо может хранить несколько адресов; один помечается как основной (primary).
 */
@Entity
@Table(name = "legal_entity_addresses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LegalEntityAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @Column(nullable = false, length = 150)
    private String city;

    @Column(nullable = false, length = 150)
    private String street;

    @Column(nullable = false, length = 20)
    private String building;

    @Column(length = 20)
    private String apartment;

    @Column(length = 10)
    private String postalCode;

    @Column(nullable = false)
    @Builder.Default
    private boolean primary = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
