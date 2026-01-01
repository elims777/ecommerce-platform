package ru.rfsnab.authservice.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rfsnab.authservice.configuration.JWTProperties;
import ru.rfsnab.authservice.models.dto.RoleEntity;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class JWTService {

    private final JWTProperties jwtProperties;

    /**
     * Создает секретный ключ для подписи токена
     */
    private SecretKey getSignedKey(){
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Генерирует аксес-токен
     */
    public String generateToken(String email, List<String> roles){
        return Jwts.builder()
                .subject(email)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+jwtProperties.getExpirationMs()))
                .signWith(getSignedKey())
                .compact();
    }

    /**
     * Генерирует рефреш-токен
     */
    public String generateRefreshToken(String email, List<String> roles){
        return Jwts.builder()
                .subject(email)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+jwtProperties.getRefreshExpirationMs()))
                .signWith(getSignedKey())
                .compact();
    }

    /**
     * Проверка токена на валидность
     */
    public boolean validateToken(String token){
        try{
            Jwts.parser()
                    .verifyWith(getSignedKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e){
            log.error("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Извлечение всех claims из токена
     */
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignedKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Извлечение email из токена
     */
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Извлечение ролей из токена
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRolesFromToken(String token) {
        return extractClaims(token).get("roles", List.class);
    }

    /**
     * Проверка истёк ли токен
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }
}
