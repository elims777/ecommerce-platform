package ru.rfsnab.authservice.models.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Builder
@Getter
@Setter
public class UserDto {
    private Long id;
    private String email;
    private String firstname;
    private String lastname;
    private String surname;
    @Builder.Default
    private Set<RoleEntity> roles = new HashSet<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
