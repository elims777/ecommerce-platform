package ru.rfsnab.userservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.userservice.exceptions.UserAlreadyExistsException;
import ru.rfsnab.userservice.mappers.RegistrationMapper;
import ru.rfsnab.userservice.mappers.UserMapper;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.dto.RegAuthResponse;
import ru.rfsnab.userservice.models.dto.RegistrationRequest;
import ru.rfsnab.userservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.userservice.models.dto.UserDto;
import ru.rfsnab.userservice.services.UserService;

import java.util.List;
import java.util.Optional;

@Slf4j
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
            log.info("OAuth2 login: пользователь {} уже существует", request.getEmail());
            return ResponseEntity.ok(RegistrationMapper.mapToResponse(existingUser.get()));
        } else {
            // Пользователь не существует - создаём нового (register)
            log.info("OAuth2 register: создаём нового пользователя {}", request.getEmail());
            UserEntity user = RegistrationMapper.mapToUserEntity(request);
            user = userService.registerUser(user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(RegistrationMapper.mapToResponse(user));
        }
    }

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
        UserDto userDto = UserMapper.mapToUserDto(userService.findUserByEmail(authRequest.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Неверный email или пароль")));
        if (!passwordEncoder.matches(authRequest.getPassword(), userDto.getPassword())) {
            throw new BadCredentialsException("Неверный email или пароль");
        }
        return ResponseEntity.status(HttpStatus.OK).body(userDto);
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

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable("id") Long id){
        UserEntity user = userService.findUserById(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Пользователь с id " + id + " не найден"));

        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted.");
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();

        UserEntity user = userService.findUserByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Пользователь " + email + " не найден"));

        return ResponseEntity.ok(UserMapper.mapToUserDto(user));
    }

    @PutMapping("/{userId}/verify")
    public ResponseEntity<String> verifyUser(@PathVariable Long userId) {
        log.info("Verifying user: {}", userId);

        userService.verifyUser(userId);

        return ResponseEntity.ok("User verified successfully");
    }
}
