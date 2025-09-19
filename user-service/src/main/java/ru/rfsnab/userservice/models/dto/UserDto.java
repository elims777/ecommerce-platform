package ru.rfsnab.userservice.models.dto;

import lombok.*;
import ru.rfsnab.userservice.models.RoleEntity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String email;
    private String password;
    private String firstname;
    private String lastname;
    private String surname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Builder.Default
    private Set<RoleEntity> roles = new HashSet<>();
}
