package ru.rfsnab.productservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.rfsnab.productservice.configuration.JwtProperties;

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
     * Извлечение ролей из токена
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = extractClaims(token);
        return claims.get("roles", List.class);
    }

    /**
     * Комплексная проверка токен, валиден и не истек
     */
    public boolean isTokenValid(String token){
        boolean valid = validateToken(token);
        boolean notExpired = !isTokenExpired(token);
        return valid && notExpired;
    }
}
