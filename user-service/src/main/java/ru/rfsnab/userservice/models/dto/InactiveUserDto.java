package ru.rfsnab.userservice.models.dto;

public record InactiveUserDto(
        Long id,
        String email,
        String firstname,
        String unsubscribeToken
) {}
