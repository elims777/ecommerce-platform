package ru.rfsnab.userservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleService roleService;

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
                .build();
    }

    @Test
    void findAllUsersReturnList() {
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<UserEntity> result = userRepository.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEmail()).isEqualTo("test@test.com");
    }

    @Test
    void findUserByIdReturnUserEntity() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Optional<UserEntity> result = userRepository.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
    }

    @Test
    void findUserByEmailReturnUserEntity() {
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        Optional<UserEntity> result = userRepository.findByEmail("test@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@test.com");
    }

    @Test
    void setDefaultRole() {
        RoleEntity role = RoleEntity.builder()
                .id(1L)
                .name("ROLE_USER")
                .build();

        when(roleService.findRoleByName("ROLE_USER")).thenReturn(role);

        UserEntity result = userService.setDefaultRole(user);

        assertThat(result).isNotNull();
        assertThat(result.getRoles())
                .isNotEmpty()
                .extracting(RoleEntity::getName)
                .contains("ROLE_USER");
    }

    @Test
    void registerUserSetRolesAndSaveUser() {
        RoleEntity role = new RoleEntity(1L, "ROLE_USER");
        when(roleService.findRoleByName("ROLE_USER")).thenReturn(role);
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity result = userService.registerUser(user);

        assertThat(result.getRoles()).extracting(RoleEntity::getName).contains("ROLE_USER");
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    void updateUser() {
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

        UserEntity result = userService.updateUser(1L, updatedUser);

        assertThat(result.getEmail()).isEqualTo("newTest@test.com");
        assertThat(result.getPassword()).isEqualTo("encodepassword");
        assertThat(result.getLastname()).isEqualTo("Test2");
        assertThat(result.getRoles()).hasSize(1);
    }

    @Test
    void updateUser_whenNotFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(99L, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User with id = 99 not found");
    }

    @Test
    void deleteUser() {
        userService.deleteUser(1L);

        verify(userRepository, times(1)).deleteById(1L);
    }
}