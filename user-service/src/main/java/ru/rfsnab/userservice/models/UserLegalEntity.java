package ru.rfsnab.userservice.models;

import jakarta.persistence.*;
import lombok.*;
import ru.rfsnab.userservice.models.enums.LinkStatus;

import java.time.LocalDateTime;

/**
 * Связь физического пользователя с юридическим лицом (many-to-many).
 * После запроса привязки отправляется письмо на email юрлица для подтверждения.
 */
@Entity
@Table(name = "user_legal_entities")
@IdClass(UserLegalEntityId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserLegalEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LinkStatus linkStatus = LinkStatus.PENDING;

    @Column(length = 36, unique = true)
    private String linkToken;

    private LocalDateTime linkedAt;
    private LocalDateTime linkRequestedAt;
}
