package ru.rfsnab.userservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rfsnab.userservice.repository.UserRepository;

import java.util.List;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final UserRepository userRepository;

    @GetMapping("/by-email/{email}")
    public ResponseEntity<AuthUserDto> findByEmail(@PathVariable String email) {
        return userRepository.findByEmail(email)
                .map(u -> ResponseEntity.ok(new AuthUserDto(u.getEmail(), u.getPassword(),
                        u.getRoles().stream().map(r -> r.getName()).toList())))
                .orElse(ResponseEntity.notFound().build());
    }

    public record AuthUserDto(String email, String passwordHash, List<String> roles) {}
}
