package ru.rfsnab.userservice.configuration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@Slf4j
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
            log.debug("Token signature valid");
            return true;
        } catch (JwtException | IllegalArgumentException e){
            log.error("JWT validation failed: {}", e.getMessage());
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
     * Извлечение email (subject) из токена
     */
    public String extractEmail(String token){
        return extractClaims(token).getSubject();
    }

    /**
     * Проверка, истек ли токен
     */
    public boolean isTokenExpired(String token){
        try{
            Date expiration = extractClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (JwtException e){
            return true;
        }
    }

    /**
     * Комплексная проверка токен, валиден и не истек
     */
    public boolean isTokenValid(String token){
        boolean valid = validateToken(token);
        boolean notExpired = !isTokenExpired(token);
        log.debug("Token validation: valid={}, notExpired={}", valid, notExpired);
        return valid && notExpired;
    }
}
