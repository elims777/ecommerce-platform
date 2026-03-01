package ru.rfsnab.authservice.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.rfsnab.authservice.models.dto.RoleEntity;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoleExtractor unit tests")
class RoleExtractorTest {

    private RoleExtractor roleExtractor;

    @BeforeEach
    void setUp(){
        roleExtractor = new RoleExtractor();
    }

    @Test
    @DisplayName("extractRoles() - возвращает роли пользователя")
    void extractRoles_WithRoles_ReturnsRoleNames(){
        RoleEntity adminRole = new RoleEntity(1L, "ROLE_ADMIN");
        RoleEntity userRole = new RoleEntity(2L, "ROLE_USER");

        UserDtoResponse user = new UserDtoResponse();
        user.setEmail("test@example.com");
        user.setRoles(Set.of(adminRole,userRole));

        List<String> roles = roleExtractor.extractRoles(user);

        assertThat(roles).hasSize(2);
        assertThat(roles).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    @DisplayName("extractRoles() - null user → ROLE_USER по умолчанию")
    void extractRoles_NullUser_ReturnsDefaultRole(){
        List<String> roles = roleExtractor.extractRoles(null);

        assertThat(roles).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("extractRoles() - null roles → ROLE_USER по умолчанию")
    void extractRoles_NullRoles_ReturnDefaultRole(){
        UserDtoResponse user = new UserDtoResponse();
        user.setEmail("test@example.com");
        user.setRoles(null);

        List<String> roles = roleExtractor.extractRoles(user);

        assertThat(roles).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("extractRoles() - пустой Set ролей → ROLE_USER по умолчанию")
    void extractRoles_EmptyRoles_ReturnDefaultRole(){
        UserDtoResponse user = new UserDtoResponse();
        user.setEmail("test@example.com");
        user.setRoles(Collections.emptySet());

        List<String> roles = roleExtractor.extractRoles(user);

        assertThat(roles).containsExactly("ROLE_USER");
    }
}