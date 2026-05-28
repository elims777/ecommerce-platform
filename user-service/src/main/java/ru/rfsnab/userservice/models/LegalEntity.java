package ru.rfsnab.userservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.rfsnab.userservice.models.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Юридическое лицо — самостоятельная сущность с собственными учётными данными.
 * Проходит верификацию менеджером перед использованием.
 * Физические пользователи могут привязывать несколько юрлиц через {@link UserLegalEntity}.
 */
@Entity
@Table(name = "legal_entities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LegalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String inn;

    @Column(nullable = false, length = 15)
    private String ogrn;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String director;

    @Column(length = 100)
    private String directorTitle;

    @Column(length = 200)
    private String basisOfAuthority;

    @Column(length = 50)
    private String office;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 150)
    private String legalCity;

    @Column(nullable = false, length = 150)
    private String legalStreet;

    @Column(nullable = false, length = 20)
    private String legalBuilding;

    @Column(length = 10)
    private String legalPostalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    private LocalDateTime verifiedAt;
    private String verifiedBy;

    @Column(length = 36, unique = true)
    private String emailConfirmToken;

    @Column(nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @OneToMany(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LegalEntityBankAccount> bankAccounts = new ArrayList<>();

    @OneToMany(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LegalEntityAddress> addresses = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
