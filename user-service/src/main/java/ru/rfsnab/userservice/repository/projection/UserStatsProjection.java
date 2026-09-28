package ru.rfsnab.userservice.repository.projection;

/**
 * Сырые агрегаты по таблице users для сводки админки — один проход по таблице.
 * Администраторы (ROLE_ADMIN) исключены из подсчёта. Производная величина
 * (заблокированные = total - active) считается в сервисном слое.
 */
public interface UserStatsProjection {
    long getTotal();

    long getEmailVerified();

    long getWithLegalEntity();

    long getActive();

    long getNewLast30Days();
}
