package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.userservice.exceptions.EmailAlreadyVerifiedException;
import ru.rfsnab.userservice.exceptions.UserAlreadyExistsException;
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.kafka.UserEvent;
import ru.rfsnab.userservice.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;


@Slf4j
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
        if (user.getPhone() != null && userRepository.existsByPhone(user.getPhone())) {
            throw new UserAlreadyExistsException("Пользователь с таким номером телефона уже существует");
        }
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
                            if (user.getPassword() != null && !user.getPassword().isBlank()) {
                                userEntity.setPassword(passwordEncoder.encode(user.getPassword()));
                            }
                            userEntity.setFirstname(user.getFirstname());
                            userEntity.setLastname(user.getLastname());
                            userEntity.setSurname(user.getSurname());
                            userEntity.setPhone(user.getPhone());
                            userEntity.setRoles(user.getRoles());
                            return userRepository.save(userEntity);
                        }
                ).orElseThrow(()->new RuntimeException(String.format("User with id = %s not found", id)));
    }

    public void deleteUser(Long id){

        userRepository.deleteById(id);
    }

    public long countAdmins() {
        return userRepository.countByRoles_Name("ROLE_ADMIN");
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

    @Transactional
    public UserEntity changeRole(Long id, String roleName) {
        UserEntity user = findById(id);
        RoleEntity role = roleService.findRoleByName(roleName);
        user.getRoles().clear();
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    @Transactional
    public UserEntity setActive(Long id, boolean active) {
        UserEntity user = findById(id);
        user.setActive(active);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserEntity> findInactiveUsers(LocalDateTime loginThreshold, LocalDateTime emailThreshold) {
        return userRepository
                .findAllByLastLoginAtBeforeAndLastInactivityEmailAtIsNullOrLastInactivityEmailAtBefore(
                        loginThreshold, emailThreshold);
    }

    @Transactional
    public void markInactivityEmailSent(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastInactivityEmailAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    @Transactional
    public void unsubscribeByToken(String token) {
        UserEntity user = userRepository.findByUnsubscribeToken(token)
                .orElseThrow(() -> new RuntimeException("Недействительная ссылка отписки"));
        user.setNewsletterConsent(false);
        userRepository.save(user);
    }

    @Transactional
    public void recordLogin(UserEntity user) {
        user.setLastLoginAt(LocalDateTime.now());
        if (user.getUnsubscribeToken() == null) {
            user.setUnsubscribeToken(UUID.randomUUID().toString());
        }
        userRepository.save(user);
    }

    @Transactional
    public UserEntity updateUserAdmin(Long id, String firstname, String lastname, String phone) {
        UserEntity user = findById(id);
        if (firstname != null) user.setFirstname(firstname);
        if (lastname != null) user.setLastname(lastname);
        if (phone != null) user.setPhone(phone);
        return userRepository.save(user);
    }

    @Transactional
    public void resendVerification(Long userId) {
        UserEntity user = findById(userId);
        if (user.isEmailVerified()) {
            throw new EmailAlreadyVerifiedException("Email уже подтверждён");
        }

        String verificationToken = UUID.randomUUID().toString();

        UserEvent event = UserEvent.builder()
                .eventType("USER_REGISTERED")
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstname())
                .lastName(user.getLastname())
                .verificationToken(verificationToken)
                .timestamp(LocalDateTime.now())
                .build();

        kafkaProducerService.sendUserRegisteredEvent(event);
        log.info("Resend verification requested: userId={}", userId);
    }
}
