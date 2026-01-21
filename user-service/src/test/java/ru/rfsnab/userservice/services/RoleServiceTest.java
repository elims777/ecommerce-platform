package ru.rfsnab.userservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.repository.RoleRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для RoleService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService tests")
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleService roleService;

    private RoleEntity roleUser;
    private RoleEntity roleAdmin;

    @BeforeEach
    void setUp(){
        roleUser = RoleEntity.builder()
                .id(1L)
                .name("ROLE_USER")
                .build();
        roleAdmin = RoleEntity.builder()
                .id(2L)
                .name("ROLE_ADMIN")
                .build();
    }

    @Test
    @DisplayName("Find role by name ROLE_USER - success")
    void findRoleByName_RoleUser_Success() {
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(roleUser));

        RoleEntity result = roleService.findRoleByName("ROLE_USER");

        assertThat(result).isNotNull();
        assertThat(result.getName().equals("ROLE_USER"));
        assertThat(result.getId().equals(1L));
        verify(roleRepository, times(1)).findByName("ROLE_USER");
    }

    @Test
    @DisplayName("Find role by name ROLE_ADMIN - success")
    void findRoleByName_RoleAdmin_Success() {
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(roleAdmin));

        RoleEntity result = roleService.findRoleByName("ROLE_ADMIN");

        assertThat(result).isNotNull();
        assertThat(result.getName().equals("ROLE_ADMIN"));
        assertThat(result.getId().equals(2L));
        verify(roleRepository, times(1)).findByName("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Find role by non-existing name - throws UserNotFoundException")
    void findRoleByName_NotFound_ThrowsException(){
        String nonExistingRole = "ROLE_NONEXISTING";

        when(roleRepository.findByName(nonExistingRole)).thenReturn(Optional.empty());

        assertThatThrownBy(()-> roleService.findRoleByName(nonExistingRole))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Пользователь с email " + nonExistingRole + " не найден");

        verify(roleRepository, times(1)).findByName(nonExistingRole);
    }

    @Test
    @DisplayName("Find role by null - throws UsernameNotFoundException")
    void findRoleByName_Null_ThrowsException(){
        when(roleRepository.findByName(null)).thenReturn(Optional.empty());

        assertThatThrownBy(()-> roleService.findRoleByName(null))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(roleRepository, times(1)).findByName(null);
    }
}