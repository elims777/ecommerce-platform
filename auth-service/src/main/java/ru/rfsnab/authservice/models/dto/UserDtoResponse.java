package ru.rfsnab.authservice.models.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDtoResponse {
    private Long id;
    private String email;
    private String firstname;
    private String lastname;
    private String surname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean emailVerified;
    @Builder.Default
    private Set<RoleEntity> roles = new HashSet<>();
}
