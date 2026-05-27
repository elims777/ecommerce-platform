package ru.rfsnab.userservice.mappers;

import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.dto.UserDto;

public class UserMapper {

    private UserMapper() {}

    public static UserDto mapToUserDto(UserEntity user){
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstname(user.getFirstname())
                .lastname(user.getLastname())
                .surname(user.getSurname())
                .phone(user.getPhone())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .emailVerified(user.isEmailVerified())
                .active(user.isActive())
                .roles(user.getRoles())
                .build();
    }

    public static UserEntity mapToUserEntity(UserDto userDto){
        return UserEntity.builder()
                .id(userDto.getId())
                .email(userDto.getEmail())
                .password(userDto.getPassword())
                .firstname(userDto.getFirstname())
                .lastname(userDto.getLastname())
                .surname(userDto.getSurname())
                .phone(userDto.getPhone())
                .createdAt(userDto.getCreatedAt())
                .updatedAt(userDto.getUpdatedAt())
                .emailVerified(userDto.isEmailVerified())
                .active(userDto.isActive())
                .roles(userDto.getRoles())
                .build();
    }

}
