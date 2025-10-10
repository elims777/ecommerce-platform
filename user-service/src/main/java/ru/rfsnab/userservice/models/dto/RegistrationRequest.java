package ru.rfsnab.userservice.models.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationRequest {
    private String email;
    private String password;
    private String firstname;
    private String lastname;
    private String surname;
}
