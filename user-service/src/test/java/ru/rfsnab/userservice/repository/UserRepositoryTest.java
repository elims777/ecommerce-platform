package ru.rfsnab.userservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import ru.rfsnab.userservice.models.UserEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void saveNewUserAndCheckIfUserExists() {
        UserEntity user = new UserEntity();
        user.setEmail("test@test.com");
        user.setPassword("password");
        user.setFirstname("Test");
        user.setLastname("Test");
        userRepository.save(user);

        Optional<UserEntity> userOptional = userRepository.findByEmail("test@test.com");
        assertTrue(userOptional.isPresent());
        assertNotNull(userOptional.get().getId());
        assertEquals(user, userOptional.get());
    }

    @Test
    void saveNewUserAndCheckIfUserDoesNotExist() {
        UserEntity user = new UserEntity();
        user.setEmail("test@test.com");
        user.setPassword("password");
        user.setFirstname("Test");
        user.setLastname("Test");
        userRepository.save(user);
        Optional<UserEntity> userOptional = userRepository.findByEmail("test2@test.com");
        assertFalse(userOptional.isPresent());
    }

    @Test
    void saveNewUserAndCheckIfUserExistsCheckPassword() {
        UserEntity user = new UserEntity();
        user.setEmail("test@test.com");
        user.setPassword("wrongpassword");
        user.setFirstname("Test");
        user.setLastname("Test");
        userRepository.save(user);
        Optional<UserEntity> userOptional = userRepository.findByEmail("test@test.com");
        assertTrue(userOptional.isPresent());
        assertEquals(userOptional.get().getPassword(), user.getPassword());
    }

    @Test
    void deleteUserByIdAndCheckUserExists() {
        UserEntity user = new UserEntity();
        user.setEmail("test@test.com");
        user.setPassword("password");
        user.setFirstname("Test");
        user.setLastname("Test");
        userRepository.save(user);
        Optional<UserEntity> userOptional = userRepository.findByEmail("test@test.com");
        assertTrue(userOptional.isPresent());
        userRepository.deleteById(userOptional.get().getId());
        Optional<UserEntity> userOptional2 = userRepository.findByEmail("test@test.com");
        assertFalse(userOptional2.isPresent());
    }

    @Test
    void saveDuplicateUserAndThrowException() {
        UserEntity user1 = new UserEntity();
        user1.setEmail("test@test.com");
        user1.setPassword("password");
        user1.setFirstname("Test");
        user1.setLastname("Test");
        userRepository.saveAndFlush(user1);

        UserEntity user2 = new UserEntity();
        user2.setEmail("test@test.com");
        user2.setPassword("password2");
        user2.setFirstname("Test2");
        user2.setLastname("Test2");

        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(user2);
        }).isInstanceOf(Exception.class);
    }

    @Test
    void saveUserUpdateUserAndCheckChanges(){
        UserEntity user = new UserEntity();
        user.setEmail("test@test.com");
        user.setPassword("password");
        user.setFirstname("Test");
        user.setLastname("Test");
        userRepository.save(user);
        Optional<UserEntity> userOptional = userRepository.findByEmail("test@test.com");
        assertTrue(userOptional.isPresent());
        assertThat(userOptional.get().getEmail()).isEqualTo(user.getEmail());
        assertThat(userOptional.get().getPassword()).isEqualTo(user.getPassword());
        assertThat(userOptional.get().getFirstname()).isEqualTo(user.getFirstname());
        assertThat(userOptional.get().getLastname()).isEqualTo(user.getLastname());
        userOptional.get().setEmail("test2@test.com");
        userOptional.get().setFirstname("Test2");
        userOptional.get().setLastname("Test2");
        userRepository.save(userOptional.get());
        Optional<UserEntity> userOptional2 = userRepository.findByEmail("test2@test.com");
        assertTrue(userOptional2.isPresent());
        assertEquals("test2@test.com", userOptional2.get().getEmail());
        assertEquals("Test2", userOptional2.get().getFirstname());
        assertEquals("Test2", userOptional2.get().getLastname());
    }
}