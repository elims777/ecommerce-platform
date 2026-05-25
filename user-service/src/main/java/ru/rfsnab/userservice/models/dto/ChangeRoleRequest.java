package ru.rfsnab.userservice.models.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeRoleRequest(@NotBlank String role) {}
