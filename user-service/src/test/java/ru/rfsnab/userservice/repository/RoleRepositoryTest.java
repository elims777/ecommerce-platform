package ru.rfsnab.userservice.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import ru.rfsnab.userservice.models.RoleEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class RoleRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void saveRoleAndExistId() {
        RoleEntity roleAdmin = new RoleEntity();
        roleAdmin.setName("ROLE_USER");
        roleRepository.save(roleAdmin);

        Optional<RoleEntity> role = roleRepository.findByName("ROLE_USER");

        assertThat(role).isPresent();
        assertThat(role.get().getId()).isNotNull();
        assertThat(role.get().getName()).isEqualTo("ROLE_USER");
    }

    @Test
    void findByNameIfExistReturnRole() {
        RoleEntity roleAdmin = new RoleEntity();
        roleAdmin.setName("ROLE_ADMIN");
        roleRepository.save(roleAdmin);

        Optional<RoleEntity> role = roleRepository.findByName("ROLE_ADMIN");

        assertThat(role).isPresent();
        assertThat(role.get().getName()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void findByNameIfNotExistReturnEmpty(){
        Optional<RoleEntity> role = roleRepository.findByName("USER");

        assertThat(role).isEmpty();
    }

    @Test
    void saveRoleWhenDuplicateThrowsException(){
        roleRepository.save(new RoleEntity().builder().name("ROLE_ADMIN").build());

        RoleEntity duplicateRole = new RoleEntity();
        duplicateRole.setName("ROLE_ADMIN");

        assertThatThrownBy(()->roleRepository.saveAndFlush(duplicateRole))
                .isInstanceOf(Exception.class);
    }

    @Test
    void deleteRoleById(){
        RoleEntity role = roleRepository.save(new RoleEntity().builder().name("ROLE_ADMIN").build());

        roleRepository.deleteById(role.getId());

        Optional<RoleEntity> foundRole = roleRepository.findById(role.getId());
        assertThat(foundRole).isEmpty();
    }

    @Test
    void findAllReturnAllRoles(){
        roleRepository.save(new RoleEntity().builder().name("ROLE_ADMIN").build());
        roleRepository.save(new RoleEntity().builder().name("ROLE_USER").build());

        List<RoleEntity> list = roleRepository.findAll();

        assertThat(list).hasSize(2);
        assertThat(list).extracting(RoleEntity::getName)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");

    }
}