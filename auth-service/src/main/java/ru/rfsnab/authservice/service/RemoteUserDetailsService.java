package ru.rfsnab.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RemoteUserDetailsService implements UserDetailsService {

    private final RestTemplate restTemplate;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//        AuthUserResponse response = webClientBuilder.build()
//                .get()
//                .uri("http://localhost:8081/internal/auth/by-email/{email}", username)
//                .accept(MediaType.APPLICATION_JSON)
//                .retrieve()
//                // ✅ современная форма обработки статусов
//                .onStatus(
//                        status -> status.value() == 404,  // если 404
//                        clientResponse -> Mono.error(new UsernameNotFoundException("User not found: " + username))
//                )
//                .bodyToMono(AuthUserResponse.class)
//                // ✅ доп. защита от сетевых и прочих ошибок
//                .onErrorResume(WebClientResponseException.class, ex ->
//                        ex.getStatusCode() == HttpStatus.NOT_FOUND
//                                ? Mono.error(new UsernameNotFoundException("User not found: " + username))
//                                : Mono.error(ex)
//                )
//                .block();

//        if (response == null) {
//            throw new UsernameNotFoundException("User not found: " + username);
//        }
//
//        List<GrantedAuthority> authorities = response.roles().stream()
//                .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
//                .collect(Collectors.toList());
//
//        return org.springframework.security.core.userdetails.User.withUsername(response.email())
//                .password(response.passwordHash())  // важно: BCrypt-хэш из user-service
//                .authorities(authorities)
//                .build();
        return null;
    }

    public record AuthUserResponse(String email, String passwordHash, List<String> roles) {}
}

