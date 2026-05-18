package ru.rfsnab.userservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Банковский счёт юридического лица.
 * Юрлицо может иметь несколько счетов; один помечается как основной (primary).
 */
@Entity
@Table(name = "legal_entity_bank_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LegalEntityBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @Column(nullable = false)
    private String bankName;

    @Column(nullable = false, length = 9)
    private String bik;

    @Column(nullable = false, length = 20)
    private String correspondentAccount;

    @Column(nullable = false, length = 20)
    private String settlementAccount;

    @Column(nullable = false)
    @Builder.Default
    private boolean primary = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
