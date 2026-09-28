package ru.rfsnab.userservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.UserLegalEntity;
import ru.rfsnab.userservice.models.dto.UserStatsResponse;
import ru.rfsnab.userservice.models.enums.LinkStatus;
import ru.rfsnab.userservice.repository.LegalEntityRepository;
import ru.rfsnab.userservice.repository.RoleRepository;
import ru.rfsnab.userservice.repository.UserLegalEntityRepository;
import ru.rfsnab.userservice.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сводка по пользователям (stats) на реальном H2. Администраторы должны быть
 * исключены из всех счётчиков.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Сводка по пользователям (stats) Integration")
class UserStatsIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Autowired
    private UserLegalEntityRepository userLegalEntityRepository;

    private RoleEntity userRole;
    private RoleEntity adminRole;

    @BeforeEach
    void setUp() {
        userLegalEntityRepository.deleteAll();
        userRepository.deleteAll();
        legalEntityRepository.deleteAll();
        roleRepository.deleteAll();

        userRole = roleRepository.save(RoleEntity.builder().name("ROLE_USER").build());
        adminRole = roleRepository.save(RoleEntity.builder().name("ROLE_ADMIN").build());
    }

    private UserEntity saveUser(String email, boolean emailVerified, boolean active, Set<RoleEntity> roles) {
        return userRepository.save(UserEntity.builder()
                .email(email)
                .password("password")
                .firstname("Test")
                .lastname("User")
                .emailVerified(emailVerified)
                .active(active)
                .roles(roles)
                .build());
    }

    private void backdateCreatedAt(UserEntity user, LocalDateTime createdAt) {
        user.setCreatedAt(createdAt);
        userRepository.save(user);
    }

    private void linkLegalEntity(UserEntity user, String inn, String email, LinkStatus status) {
        LegalEntity legalEntity = legalEntityRepository.save(LegalEntity.builder()
                .inn(inn)
                .fullName("ООО Тест " + inn)
                .email(email)
                .password("password")
                .build());
        userLegalEntityRepository.save(UserLegalEntity.builder()
                .user(user)
                .legalEntity(legalEntity)
                .linkStatus(status)
                .build());
    }

    @Test
    @DisplayName("считает всех, кроме администраторов, по подтверждению, юрлицу, активности и новизне")
    void shouldCountUserStatsExcludingAdmins() {
        saveUser("admin@test.ru", true, true, new HashSet<>(Set.of(adminRole)));

        saveUser("verified-active@test.ru", true, true, new HashSet<>(Set.of(userRole)));
        saveUser("unverified-blocked@test.ru", false, false, new HashSet<>(Set.of(userRole)));

        UserEntity withConfirmedLink = saveUser("confirmed-legal@test.ru", true, true, new HashSet<>(Set.of(userRole)));
        linkLegalEntity(withConfirmedLink, "1111111111", "legal-confirmed@test.ru", LinkStatus.CONFIRMED);

        UserEntity withPendingLink = saveUser("pending-legal@test.ru", true, true, new HashSet<>(Set.of(userRole)));
        linkLegalEntity(withPendingLink, "2222222222", "legal-pending@test.ru", LinkStatus.PENDING);

        UserEntity oldUser = saveUser("old-user@test.ru", true, true, new HashSet<>(Set.of(userRole)));
        backdateCreatedAt(oldUser, LocalDateTime.now().minusDays(40));

        UserStatsResponse stats = userService.getUserStats();

        assertThat(stats.total()).isEqualTo(5);
        assertThat(stats.emailVerified()).isEqualTo(4);
        assertThat(stats.withLegalEntity()).isEqualTo(1);
        assertThat(stats.active()).isEqualTo(4);
        assertThat(stats.blocked()).isEqualTo(1);
        assertThat(stats.newLast30Days()).isEqualTo(4);
    }
}
