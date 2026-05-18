package ru.rfsnab.authservice.models.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LegalEntityDto {
    private Long id;
    private String email;
    private String inn;
    private String fullName;
}
