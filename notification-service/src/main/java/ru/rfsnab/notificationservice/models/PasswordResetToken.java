package ru.rfsnab.notificationservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Long accountId;
    @Column(nullable = false, length = 10)
    private String accountType;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;
}
