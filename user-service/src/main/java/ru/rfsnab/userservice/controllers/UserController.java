package ru.rfsnab.userservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.userservice.exceptions.UserAlreadyExistsException;
import ru.rfsnab.userservice.mappers.RegistrationMapper;
import ru.rfsnab.userservice.mappers.UserMapper;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.dto.ChangeRoleRequest;
import ru.rfsnab.userservice.models.dto.ChangeStatusRequest;
import ru.rfsnab.userservice.models.dto.InactiveUserDto;
import ru.rfsnab.userservice.models.dto.RegAuthResponse;
import ru.rfsnab.userservice.models.dto.ProfileCompletenessDto;
import ru.rfsnab.userservice.models.dto.RegistrationRequest;
import ru.rfsnab.userservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.userservice.models.dto.UpdateUserAdminRequest;
import ru.rfsnab.userservice.models.dto.UserDto;
import ru.rfsnab.userservice.services.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    /**
     * OAuth2 login/register endpoint
     * Если пользователь существует - возвращает его данные
     * Если не существует - создаёт нового с временным паролем
     */
    @PostMapping("/oauth2-login")
    public ResponseEntity<RegAuthResponse> oauth2LoginOrRegister(@Valid @RequestBody RegistrationRequest request) {
        Optional<UserEntity> existingUser = userService.findUserByEmail(request.getEmail());

        if (existingUser.isPresent()) {
            // Пользователь уже существует - возвращаем его данные (login)
            return ResponseEntity.ok(RegistrationMapper.mapToResponse(existingUser.get()));
        } else {
            // Пользователь не существует - создаём нового (register)
            UserEntity user = RegistrationMapper.mapToUserEntity(request);
            user = userService.registerUser(user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(RegistrationMapper.mapToResponse(user));
        }
    }

    /**
     * Регистрация пользователей
     */
    @PostMapping("/signup")
    public ResponseEntity<RegAuthResponse> registerUser(@Valid @RequestBody RegistrationRequest userDto){
        if(userService.findUserByEmail(userDto.getEmail()).isPresent()){
            throw new UserAlreadyExistsException(userDto.getEmail());
        } else {
            UserEntity user = RegistrationMapper.mapToUserEntity(userDto);

            user = userService.registerUser(user);

            RegAuthResponse response = RegistrationMapper.mapToResponse(user);

            return ResponseEntity.status(HttpStatus.CREATED)
                            .body(response);
        }
    }

    /***
     * Аутентификация пользователей через логин, пароль
     */
    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticate(@Valid @RequestBody SimpleAuthRequest authRequest){
        UserEntity user = userService.findUserByEmail(authRequest.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Неверный email или пароль"));

        if (!passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Неверный email или пароль");
        }

        if (!user.isActive()) {
            throw new DisabledException("Аккаунт заблокирован");
        }

        userService.recordLogin(user);

        return ResponseEntity.ok(UserMapper.mapToUserDto(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable("id") Long id, @RequestBody UserDto userDto){
        return ResponseEntity.ok(
                UserMapper.mapToUserDto(
                        userService.updateUser(id, UserMapper.mapToUserEntity(userDto))));
    }

    @GetMapping("/all")
    public ResponseEntity<List<UserDto>> getAllUsers(){
        return ResponseEntity.ok(
                userService.findAllUsers()
                        .stream()
                        .map(UserMapper::mapToUserDto)
                        .toList());
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        UserEntity user = userService.findUserById(userId)
                .orElseThrow(()->new UsernameNotFoundException("Пользователь не найден"));

        return ResponseEntity.ok(UserMapper.mapToUserDto(user));
    }

    @PutMapping("/{userId}/verify")
    public ResponseEntity<String> verifyUser(@PathVariable Long userId) {
        userService.verifyUser(userId);
        return ResponseEntity.ok("User verified successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(UserMapper.mapToUserDto(userService.findById(id)));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserDto> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(UserMapper.mapToUserDto(userService.changeRole(id, request.role())));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserDto> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeStatusRequest request) {
        return ResponseEntity.ok(UserMapper.mapToUserDto(userService.setActive(id, request.active())));
    }

    @GetMapping("/inactive")
    public ResponseEntity<List<InactiveUserDto>> getInactiveUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(14);
        List<InactiveUserDto> result = userService.findInactiveUsers(threshold, threshold).stream()
                .map(u -> new InactiveUserDto(u.getId(), u.getEmail(), u.getFirstname(), u.getUnsubscribeToken()))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/inactivity-email-sent")
    public ResponseEntity<Void> markInactivityEmailSent(@PathVariable Long id) {
        userService.markInactivityEmailSent(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unsubscribe")
    public ResponseEntity<String> unsubscribe(@RequestParam String token) {
        userService.unsubscribeByToken(token);
        return ResponseEntity.ok("Вы успешно отписались от рассылки");
    }

    @PatchMapping("/{id}/admin")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserDto> updateUserAdmin(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserAdminRequest request) {
        return ResponseEntity.ok(UserMapper.mapToUserDto(
            userService.updateUserAdmin(id, request.firstname(), request.lastname(), request.phone())));
    }

    @PostMapping("/me/resend-verification")
    public ResponseEntity<Void> resendVerification(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        userService.resendVerification(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/profile-completeness")
    public ResponseEntity<ProfileCompletenessDto> getProfileCompleteness(@PathVariable Long id) {
        List<String> missing = userService.getMissingProfileFields(id);
        return ResponseEntity.ok(new ProfileCompletenessDto(missing.isEmpty(), missing));
    }
}
