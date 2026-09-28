package ru.rfsnab.userservice.models.dto;

/**
 * Сводка по пользователям для админки. Администраторы (ROLE_ADMIN) исключены из всех счётчиков.
 *
 * @param total          все пользователи, кроме администраторов
 * @param emailVerified  с подтверждённым email
 * @param withLegalEntity с подтверждённой привязкой юрлица (linkStatus = CONFIRMED)
 * @param active         активные
 * @param blocked        заблокированные (active = false)
 * @param newLast30Days  зарегистрированные за последние 30 дней
 */
public record UserStatsResponse(
        long total,
        long emailVerified,
        long withLegalEntity,
        long active,
        long blocked,
        long newLast30Days
) {}
