package ru.rfsnab.authservice.models.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationResponse {
    private boolean valid;
    private Long userId;
    private String email;
    private String message;
}
