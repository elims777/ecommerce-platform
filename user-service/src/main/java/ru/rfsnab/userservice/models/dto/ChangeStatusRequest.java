package ru.rfsnab.userservice.models.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull Boolean active) {}
