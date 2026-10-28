package ru.rfsnab.userservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JWTService {

    private final JWTProperties jwtProperties;

    /**
     * Создает секретный ключ для проверки подписи токена.
     * Ключ должен совпадать с ключом в auth-service.
     */
    private SecretKey getSignedKey(){
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Проверка токена на валидность
     */
    private boolean validateToken(String token){
        try{
            Jwts.parser()
                    .verifyWith(getSignedKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e){
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Извлечение всех Claims из токена
     */
    private Claims extractClaims(String token){
        return Jwts.parser()
                .verifyWith(getSignedKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Извлечение userId из токена (из subject)
     */
    public Long extractUserId(String token) {
        String subject = extractClaims(token).getSubject();
        return Long.parseLong(subject);
    }

    /**
     * Извлечение email (subject) из токена
     */
    public String extractEmail(String token){
        return extractClaims(token).get("email", String.class);
    }

    /**
     * ДОБАВЛЕНО: Извлечение ролей из токена
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRolesFromToken(String token) {
        return extractClaims(token).get("roles", List.class);
    }

    /**
     * Тип клиента из токена (B2C/B2B); у старых токенов claim может отсутствовать
     */
    public String extractClientType(String token) {
        return extractClaims(token).get("clientType", String.class);
    }

    /**
     * Проверка, истек ли токен
     */
    public boolean isTokenExpired(String token){
        try{
            Instant expiration = extractClaims(token).getExpiration().toInstant();
            return expiration.isBefore(Instant.now());
        } catch (JwtException e){
            return true;
        }
    }

    /**
     * Комплексная проверка токен, валиден и не истек
     */
    public boolean isTokenValid(String token){
        return validateToken(token) && !isTokenExpired(token);
    }
}
