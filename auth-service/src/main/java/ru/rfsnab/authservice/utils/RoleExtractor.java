package ru.rfsnab.authservice.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.rfsnab.authservice.models.dto.RoleEntity;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RoleExtractor {

    /**
     * Извлекает список имен ролей из UserDtoResponse.
     * Если у пользователя нет ролей, возвращает ROLE_USER по умолчанию.
     *
     * @param user объект пользователя с ролями
     * @return список имен ролей (например, ["ROLE_ADMIN", "ROLE_USER"])
     */
    public List<String> extractRoles(UserDtoResponse user) {
        if (user == null) {
            log.warn("UserDtoResponse is null, используется ROLE_USER");
            return List.of("ROLE_USER");
        }

        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            log.warn("У пользователя {} нет ролей, используется ROLE_USER", user.getEmail());
            return List.of("ROLE_USER");
        }

        return user.getRoles().stream()
                .map(RoleEntity::getName)
                .collect(Collectors.toList());
    }
}
