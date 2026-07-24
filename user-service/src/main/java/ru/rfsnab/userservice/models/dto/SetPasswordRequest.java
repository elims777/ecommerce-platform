package ru.rfsnab.userservice.models.dto;

import jakarta.validation.constraints.NotBlank;

public record SetPasswordRequest(@NotBlank String passwordHash) {}
