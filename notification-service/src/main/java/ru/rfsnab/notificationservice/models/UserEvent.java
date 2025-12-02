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
    private Long id;
    private String email;
    private String firsName;
    private String lastName;
    private String verificationToken;
    private LocalDateTime timestamp;
}
