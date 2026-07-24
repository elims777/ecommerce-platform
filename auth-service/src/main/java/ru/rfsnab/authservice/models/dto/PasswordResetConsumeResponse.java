package ru.rfsnab.authservice.models.dto;

public record PasswordResetConsumeResponse(boolean valid, Long accountId, String accountType) {}
