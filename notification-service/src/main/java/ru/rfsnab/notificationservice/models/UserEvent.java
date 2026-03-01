package ru.rfsnab.notificationservice.models;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEvent {
    private String eventType;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String verificationToken;
    private LocalDateTime timestamp;
}
