package ru.rfsnab.authservice.models.dto;

public record SwitchContextRequest(
        String targetType,
        String password
) {}
