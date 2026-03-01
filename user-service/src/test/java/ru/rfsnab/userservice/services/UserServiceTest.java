package ru.rfsnab.userservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.kafka.UserEvent;
import ru.rfsnab.userservice.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)  // ← УДАЛИЛ @ActiveProfiles
@DisplayName("UserService tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleService roleService;

    @Mock
    private KafkaProducerService kafkaProducerService;  // ← ДОБАВИЛ

    @InjectMocks
    private UserService userService;

    private UserEntity user;

    @BeforeEach
    void setup(){
        user = UserEntity.builder()
                .id(1L)
                .email("test@test.com")
                .password("password")
                .firstname("Test")
                .lastname("Testo")
                .emailVerified(false)  // ← ДОБАВИЛ
                .build();
    }

    @Test
    @DisplayName("Find all users returns list")
    void findAllUsersReturnList() {
        // Given
        when(userRepository.findAll()).thenReturn(List.of(user));

        // When
        List<UserEntity> result = userService.findAllUsers();  // ← ИСПРАВИЛ: было userRepository

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("test@test.com");
        verify(userRepository, times(1)).findAll();  // ← ДОБАВИЛ verify
    }

    @Test
    @DisplayName("Find user by ID returns user entity")
    void findUserByIdReturnUserEntity() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // When
        Optional<UserEntity> result = userService.findUserById(1L);  // ← ИСПРАВИЛ: было userRepository

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        verify(userRepository, times(1)).findById(1L);  // ← ДОБАВИЛ verify
    }

    @Test
    @DisplayName("Find user by email returns user entity")
    void findUserByEmailReturnUserEntity() {
        // Given
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        // When
        Optional<UserEntity> result = userService.findUserByEmail("test@test.com");  // ← ИСПРАВИЛ

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@test.com");
        verify(userRepository, times(1)).findByEmail("test@test.com");  // ← ДОБАВИЛ verify
    }

    @Test
    @DisplayName("Set default role adds ROLE_USER")
    void setDefaultRole() {
        // Given
        RoleEntity role = RoleEntity.builder()
                .id(1L)
                .name("ROLE_USER")
                .build();
        when(roleService.findRoleByName("ROLE_USER")).thenReturn(role);

        // When
        UserEntity result = userService.setDefaultRole(user);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRoles())
                .isNotEmpty()
                .extracting(RoleEntity::getName)
                .contains("ROLE_USER");
        verify(roleService, times(1)).findRoleByName("ROLE_USER");
    }

    @Test
    @DisplayName("Register user sets roles, saves user and sends Kafka event")
    void registerUserSetRolesAndSaveUser() {
        // Given
        RoleEntity role = new RoleEntity(1L, "ROLE_USER");
        when(roleService.findRoleByName("ROLE_USER")).thenReturn(role);
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(kafkaProducerService).sendUserRegisteredEvent(any(UserEvent.class));  // ← ДОБАВИЛ

        // When
        UserEntity result = userService.registerUser(user);

        // Then
        assertThat(result.getRoles()).extracting(RoleEntity::getName).contains("ROLE_USER");
        verify(userRepository, times(1)).save(any(UserEntity.class));
        verify(kafkaProducerService, times(1)).sendUserRegisteredEvent(any(UserEvent.class));  // ← ДОБАВИЛ
    }

    @Test
    @DisplayName("Update user updates all fields")
    void updateUser() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword")).thenReturn("encodepassword");
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity updatedUser = UserEntity.builder()
                .email("newTest@test.com")
                .password("newpassword")
                .lastname("Test2")
                .roles(Set.of(RoleEntity.builder().id(2L).name("ROLE_ADMIN").build()))
                .build();

        // When
        UserEntity result = userService.updateUser(1L, updatedUser);

        // Then
        assertThat(result.getEmail()).isEqualTo("newTest@test.com");
        assertThat(result.getPassword()).isEqualTo("encodepassword");
        assertThat(result.getLastname()).isEqualTo("Test2");
        assertThat(result.getRoles()).hasSize(1);
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Update user when not found throws exception")
    void updateUser_whenNotFound_throwsException() {
        // Given
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.updateUser(99L, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User with id = 99 not found");
        verify(userRepository, times(1)).findById(99L);
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Delete user calls repository deleteById")
    void deleteUser() {
        // Given
        doNothing().when(userRepository).deleteById(1L);

        // When
        userService.deleteUser(1L);

        // Then
        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Verify user sets emailVerified to true")
    void verifyUser_success() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenReturn(user);

        // When
        userService.verifyUser(1L);

        // Then
        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Verify user when not found throws exception")
    void verifyUser_notFound_throwsException() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.verifyUser(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
        verify(userRepository, times(1)).findById(999L);
        verify(userRepository, never()).save(any(UserEntity.class));
    }
}