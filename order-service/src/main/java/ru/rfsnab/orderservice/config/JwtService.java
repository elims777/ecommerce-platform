package ru.rfsnab.orderservice.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtProperties jwtProperties;

    /**
     * Создает секретный ключ для проверки подписи токена.
     * Ключ должен совпадать с ключом в auth-service.
     */
    public SecretKey getSignedKey(){
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
            System.out.println("JWT validation failed: "+ e.getMessage());
            return false;
        }
    }

    /**
     * Извлечение всех Claims из токена
     */
    private Claims extractClaims(String token) {
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
     * Извлечение email из токена
     */
    public String extractEmail(String token) {
        return extractClaims(token).get("email", String.class);
    }

    /**
     * Извлечение ролей из токена
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRolesFromToken(String token) {
        return extractClaims(token).get("roles", List.class);
    }

    /**
     * Проверка, истек ли токен
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    /**
     * Комплексная проверка — токен валиден и не истек
     */
    public boolean isTokenValid(String token) {
        boolean valid = validateToken(token);
        boolean notExpired = !isTokenExpired(token);
        return valid && notExpired;
    }
}
