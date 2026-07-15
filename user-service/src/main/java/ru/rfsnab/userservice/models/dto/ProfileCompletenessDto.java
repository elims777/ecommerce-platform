package ru.rfsnab.userservice.models.dto;

import java.util.List;

public record ProfileCompletenessDto(boolean complete, List<String> missing) {}
