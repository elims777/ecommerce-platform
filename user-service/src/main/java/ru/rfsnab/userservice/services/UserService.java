package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.kafka.UserEvent;
import ru.rfsnab.userservice.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;


@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final KafkaProducerService kafkaProducerService;

    public List<UserEntity> findAllUsers(){
        return userRepository.findAll();
    }

    public Optional<UserEntity> findUserById(Long id){
        return userRepository.findById(id);
    }

    public Optional<UserEntity> findUserByEmail(String email){
        return userRepository.findByEmail(email);
    }

    public UserEntity setDefaultRole(UserEntity user){
        Set<RoleEntity> roleEntities = new HashSet<>();
        roleEntities.add(roleService.findRoleByName("ROLE_USER"));
        user.setRoles(roleEntities);
        return user;
    }

    public UserEntity registerUser(UserEntity user){
        user=setDefaultRole(user);
        UserEntity savedUser = userRepository.save(user);
        String verificationToken = UUID.randomUUID().toString();

        UserEvent event = UserEvent.builder()
                .eventType("USER_REGISTERED")
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .firstName(savedUser.getFirstname())
                .lastName(savedUser.getLastname())
                .verificationToken(verificationToken)
                .timestamp(LocalDateTime.now())
                .build();

        kafkaProducerService.sendUserRegisteredEvent(event);

        return savedUser;
    }

    public UserEntity updateUser(Long id,UserEntity user){
        return userRepository.findById(id)
                .map(
                        userEntity -> {
                            userEntity.setEmail(user.getEmail());
                            userEntity.setPassword(passwordEncoder.encode(user.getPassword()));
                            userEntity.setFirstname(user.getFirstname());
                            userEntity.setLastname(user.getLastname());
                            userEntity.setSurname(user.getSurname());
                            userEntity.setRoles(user.getRoles());
                            return userRepository.save(userEntity);
                        }
                ).orElseThrow(()->new RuntimeException(String.format("User with id = %s not found", id)));
    }

    public void deleteUser(Long id){

        userRepository.deleteById(id);
    }

    @Transactional
    public void verifyUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setEmailVerified(true);
        userRepository.save(user);
    }

    public UserEntity findById(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
