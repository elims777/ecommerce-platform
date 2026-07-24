package ru.rfsnab.userservice.models.dto;

public record AccountByEmailResponse(Long accountId, String accountType, String email, String firstName) {}
