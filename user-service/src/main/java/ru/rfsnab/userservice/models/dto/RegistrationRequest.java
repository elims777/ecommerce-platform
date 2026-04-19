package ru.rfsnab.userservice.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank @Size(min = 8, max = 100)
    private String password;

    @NotBlank
    private String firstname;

    @NotBlank
    private String lastname;

    private String surname;

    @NotBlank(message = "Телефон обязателен")
    @Pattern(regexp = "^\\+?[0-9]{11}$", message = "Телефон должен содержать 11 цифр")
    private String phone;

    @Builder.Default
    private boolean emailVerified = false;
}
