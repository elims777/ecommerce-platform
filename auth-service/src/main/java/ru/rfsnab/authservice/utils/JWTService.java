package ru.rfsnab.authservice.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rfsnab.authservice.configuration.JWTProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class JWTService {

    private static final String CLAIM_COMPANY_NAME = "companyName";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_CLIENT_TYPE = "clientType";
    private static final String ROLE_USER = "ROLE_USER";
    private static final String CLAIM_EMAIL = "email";

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
    public String generateToken(Long userId, String email, List<String> roles){
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_CLIENT_TYPE, "B2C")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+jwtProperties.getExpirationMs()))
                .signWith(getSignedKey())
                .compact();
    }

    /**
     * Генерирует рефреш-токен
     */
    public String generateRefreshToken(Long userId, String email, List<String> roles){
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_CLIENT_TYPE, "B2C")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+jwtProperties.getRefreshExpirationMs()))
                .signWith(getSignedKey())
                .compact();
    }

    public String generateToken(Long id, String email, String clientType) {
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_CLIENT_TYPE, clientType)
                .claim(CLAIM_ROLES, List.of(ROLE_USER))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpirationMs()))
                .signWith(getSignedKey())
                .compact();
    }

    public String generateRefreshToken(Long id, String email, String clientType) {
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_CLIENT_TYPE, clientType)
                .claim(CLAIM_ROLES, List.of(ROLE_USER))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpirationMs()))
                .signWith(getSignedKey())
                .compact();
    }

    public String generateToken(Long id, String email, String clientType,
                                String companyName, String inn) {
        var builder = Jwts.builder()
                .subject(String.valueOf(id))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_CLIENT_TYPE, clientType)
                .claim(CLAIM_ROLES, List.of(ROLE_USER))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpirationMs()));
        if (companyName != null) builder.claim(CLAIM_COMPANY_NAME, companyName);
        if (inn != null) builder.claim("inn", inn);
        return builder.signWith(getSignedKey()).compact();
    }

    public String generateRefreshToken(Long id, String email, String clientType,
                                       String companyName, String inn) {
        var builder = Jwts.builder()
                .subject(String.valueOf(id))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_CLIENT_TYPE, clientType)
                .claim(CLAIM_ROLES, List.of(ROLE_USER))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpirationMs()));
        if (companyName != null) builder.claim(CLAIM_COMPANY_NAME, companyName);
        if (inn != null) builder.claim("inn", inn);
        return builder.signWith(getSignedKey()).compact();
    }

    public String extractClientType(String token) {
        return extractClaims(token).get(CLAIM_CLIENT_TYPE, String.class);
    }

    public String extractCompanyName(String token) {
        return extractClaims(token).get(CLAIM_COMPANY_NAME, String.class);
    }

    public String extractInn(String token) {
        return extractClaims(token).get("inn", String.class);
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

        return extractClaims(token).get(CLAIM_EMAIL, String.class);
    }

    /**
     * Извлечение ролей из токена
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRolesFromToken(String token) {
        return extractClaims(token).get(CLAIM_ROLES, List.class);
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
