package ru.rfsnab.userservice.mappers;

import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.dto.RegistrationRequest;
import ru.rfsnab.userservice.models.dto.RegAuthResponse;

public class RegistrationMapper {

    public static UserEntity mapToUserEntity(RegistrationRequest request){
        return UserEntity.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .surname(request.getSurname())
                .build();
    }

    public static RegAuthResponse mapToResponse (UserEntity user){
        return RegAuthResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstname(user.getFirstname())
                .lastname(user.getLastname())
                .surname(user.getSurname())
                .roles(user.getRoles())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

}
