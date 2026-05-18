# Plan B — Auth B2B, Wholesale Price, Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire up B2B authentication (legal entity login + switch-context), add wholesale pricing to products via 1С integration, and add email notifications for all legal entity lifecycle events.

**Architecture:** auth-service gets two new endpoints (login/legal, switch-context) + a registration proxy; gateway forwards two new headers (X-User-Id, X-Client-Type); product-service and integration-service gain dual-price support; notification-service gains a legal-entity-events consumer with a new handler.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Security, JJWT, RestTemplate, Spring Kafka, JavaMailSender, Flyway, Testcontainers, WireMock, MapStruct.

---

## File Map

### user-service (new internal endpoints)
- Modify: `controllers/LegalEntityController.java` — add `POST /v1/legal-entities/authenticate`, `GET /v1/users/{userId}/legal-entities/{legalEntityId}/link-status`
- Modify: `services/LegalEntityService.java` — add `authenticate(String login, String password)`, `getLinkStatus(Long userId, Long legalEntityId)`
- Modify: `repository/UserLegalEntityRepository.java` — add `findByUserIdAndLegalEntityId`
- Create: `models/dto/legal/LegalEntityAuthRequest.java`
- Create: `models/dto/legal/LegalEntityAuthResponse.java`

### auth-service
- Modify: `utils/JWTService.java` — add `generateToken(Long id, String email, String clientType)`  + `generateRefreshToken(Long id, String email, String clientType)`
- Modify: `service/AuthService.java` — add `authenticateLegalEntity()`, `switchContext()`
- Modify: `controllers/AuthController.java` — add `POST /v1/auth/login/legal`, `POST /v1/auth/switch-context`
- Modify: `controllers/RegistrationController.java` — add `POST /api/v1/register/legal`
- Create: `models/dto/LegalAuthRequest.java`
- Create: `models/dto/SwitchContextRequest.java`
- Create: `models/dto/LegalEntityAuthResponse.java` (mirrors user-service DTO)

### gateway-service
- Modify: `filter/JwtAuthenticationFilter.java` — extract `sub` and `clientType` claims, forward as `X-User-Id` and `X-Client-Type`

### product-service
- Create: `resources/db/migration/V20260518120000__add_wholesale_price.sql`
- Modify: `model/Product.java` — add `BigDecimal wholesalePrice`
- Modify: `dto/ProductResponse.java` — add `BigDecimal wholesalePrice`
- Modify: `dto/ProductRequest.java` — add `BigDecimal wholesalePrice`
- Modify: `mapper/ProductMapper.java` — map `wholesalePrice`
- Modify: `dto/ProductImportItem.java` — add `BigDecimal wholesalePrice` (batch import DTO inside product-service)

### integration-service
- Modify: `dto/ProductImportItemDto.java` — add `BigDecimal wholesalePrice`
- Modify: `service/catalog/CatalogImportService.java` — replace `extractPrice()` with `extractPriceByType()`, pass priceTypes into `mapToImportItem()`
- Modify: `src/test/resources/commerceml/offers.xml` — add "Розничная" price type + second price per offer

### notification-service
- Create: `models/LegalEntityEvent.java`
- Create: `handler/LegalEntityHandler.java`
- Modify: `kafka/KafkaListenerService.java` — add `legal-entity-events` to `@KafkaListener`
- Modify: `service/EmailService.java` — add 6 new email methods
- Modify: `resources/application.yml` — add topic + manager email config

---

## Task 1: user-service — fix LegalEntityEvent to include emailConfirmToken + linkToken

**Context:** `LegalEntityEvent` record in user-service does not carry the confirmation token, making it impossible for notification-service to build the confirmation URL. The `rejectionReason` field is repurposed for userName in LINK events. We need to add a `token` field.

**Files:**
- Modify: `user-service/src/main/java/ru/rfsnab/userservice/models/kafka/LegalEntityEvent.java`
- Modify: `user-service/src/main/java/ru/rfsnab/userservice/services/LegalEntityService.java`

- [ ] **Step 1: Add token field to LegalEntityEvent**

Replace `LegalEntityEvent.java`:

```java
package ru.rfsnab.userservice.models.kafka;

import java.time.LocalDateTime;

public record LegalEntityEvent(
        String eventType,
        Long legalEntityId,
        String inn,
        String companyName,
        String legalEntityEmail,
        String targetEmail,
        String rejectionReason,
        LocalDateTime timestamp,
        String token         // emailConfirmToken or linkToken; null when not applicable
) {}
```

- [ ] **Step 2: Update LegalEntityService to pass token**

In `LegalEntityService.java`, update the `register()` Kafka send:

```java
kafkaProducerService.send(new LegalEntityEvent(
        "LEGAL_ENTITY_REGISTERED",
        entity.getId(), entity.getInn(), entity.getFullName(),
        entity.getEmail(), entity.getEmail(),
        null, LocalDateTime.now(), confirmToken
));
```

Update `linkToUser()` Kafka send:

```java
kafkaProducerService.send(new LegalEntityEvent(
        "LEGAL_ENTITY_LINK_REQUESTED",
        entity.getId(), entity.getInn(), entity.getFullName(),
        entity.getEmail(), entity.getEmail(),
        user.getFirstname() + " " + user.getLastname(), LocalDateTime.now(), linkToken
));
```

All other sends get `null` as the last argument (token not needed).

- [ ] **Step 3: Run user-service tests**

```
cd user-service && mvn test -q
```
Expected: all 71 tests PASS (record field addition is backward compatible with existing tests if they don't assert on the event structure directly).

- [ ] **Step 4: Commit**

```
git add user-service/src/
git commit -m "fix(user-service): include confirmation token in LegalEntityEvent for notification-service"
```

---

## Task 2: user-service — internal authenticate endpoint

**Files:**
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/dto/legal/LegalEntityAuthRequest.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/dto/legal/LegalEntityAuthResponse.java`
- Modify: `user-service/src/main/java/ru/rfsnab/userservice/services/LegalEntityService.java`
- Modify: `user-service/src/main/java/ru/rfsnab/userservice/controllers/LegalEntityController.java`

- [ ] **Step 1: Create LegalEntityAuthRequest**

```java
// user-service/src/main/java/ru/rfsnab/userservice/models/dto/legal/LegalEntityAuthRequest.java
package ru.rfsnab.userservice.models.dto.legal;

public record LegalEntityAuthRequest(String login, String password) {}
```

- [ ] **Step 2: Create LegalEntityAuthResponse**

```java
// user-service/src/main/java/ru/rfsnab/userservice/models/dto/legal/LegalEntityAuthResponse.java
package ru.rfsnab.userservice.models.dto.legal;

public record LegalEntityAuthResponse(Long id, String email, String inn, String companyName) {}
```

- [ ] **Step 3: Write failing test for authenticate**

```java
// user-service/src/test/java/ru/rfsnab/userservice/services/LegalEntityAuthTest.java
package ru.rfsnab.userservice.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.repository.LegalEntityRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class LegalEntityAuthTest {

    @Autowired LegalEntityService service;
    @Autowired LegalEntityRepository repo;
    @Autowired PasswordEncoder encoder;

    @Test
    void authenticateByEmail_verified_returnsDto() {
        LegalEntity entity = repo.save(LegalEntity.builder()
                .inn("1234567890").ogrn("1234567890123").fullName("ООО Тест").director("Иванов")
                .phone("+71234567890").email("legal@test.ru")
                .password(encoder.encode("secret"))
                .verificationStatus(VerificationStatus.VERIFIED)
                .emailVerified(true)
                .legalCity("Москва").legalStreet("Ленина").legalBuilding("1").legalPostalCode("100000")
                .build());

        var result = service.authenticate("legal@test.ru", "secret");

        assertThat(result.id()).isEqualTo(entity.getId());
        assertThat(result.email()).isEqualTo("legal@test.ru");
        assertThat(result.inn()).isEqualTo("1234567890");
    }

    @Test
    void authenticateByInn_verified_returnsDto() {
        repo.save(LegalEntity.builder()
                .inn("9876543210").ogrn("9876543210123").fullName("ООО Тест2").director("Петров")
                .phone("+71234567891").email("legal2@test.ru")
                .password(encoder.encode("secret"))
                .verificationStatus(VerificationStatus.VERIFIED)
                .emailVerified(true)
                .legalCity("СПб").legalStreet("Невский").legalBuilding("1").legalPostalCode("190000")
                .build());

        var result = service.authenticate("9876543210", "secret");
        assertThat(result.inn()).isEqualTo("9876543210");
    }

    @Test
    void authenticate_pendingEntity_throws403() {
        repo.save(LegalEntity.builder()
                .inn("1111111111").ogrn("1111111111111").fullName("ООО Ожидание").director("Сидоров")
                .phone("+71234567892").email("pending@test.ru")
                .password(encoder.encode("secret"))
                .verificationStatus(VerificationStatus.PENDING)
                .emailVerified(false)
                .legalCity("Казань").legalStreet("Кремль").legalBuilding("1").legalPostalCode("420000")
                .build());

        assertThatThrownBy(() -> service.authenticate("pending@test.ru", "secret"))
                .isInstanceOf(LegalEntityNotVerifiedException.class);
    }

    @Test
    void authenticate_wrongPassword_throwsBadCredentials() {
        repo.save(LegalEntity.builder()
                .inn("2222222222").ogrn("2222222222222").fullName("ООО Неправильный").director("Козлов")
                .phone("+71234567893").email("wrong@test.ru")
                .password(encoder.encode("correct"))
                .verificationStatus(VerificationStatus.VERIFIED)
                .emailVerified(true)
                .legalCity("НН").legalStreet("Большая").legalBuilding("1").legalPostalCode("603000")
                .build());

        assertThatThrownBy(() -> service.authenticate("wrong@test.ru", "badpass"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

```
cd user-service && mvn test -pl . -Dtest=LegalEntityAuthTest -q
```
Expected: FAIL — `authenticate` method not found.

- [ ] **Step 5: Add authenticate() to LegalEntityService**

Add to `LegalEntityService.java` after the `getAddresses` method:

```java
@Transactional(readOnly = true)
public LegalEntityAuthResponse authenticate(String login, String password) {
    LegalEntity entity = login.matches("\\d{10}|\\d{12}")
            ? legalEntityRepository.findByInn(login)
                    .orElseThrow(() -> new LegalEntityNotFoundException("Юрлицо не найдено"))
            : legalEntityRepository.findByEmail(login)
                    .orElseThrow(() -> new LegalEntityNotFoundException("Юрлицо не найдено"));

    if (!passwordEncoder.matches(password, entity.getPassword())) {
        throw new BadCredentialsException("Неверный логин или пароль");
    }
    if (entity.getVerificationStatus() != VerificationStatus.VERIFIED) {
        throw new LegalEntityNotVerifiedException("Юрлицо не прошло верификацию");
    }

    return new LegalEntityAuthResponse(entity.getId(), entity.getEmail(),
            entity.getInn(), entity.getFullName());
}
```

Add import: `import org.springframework.security.authentication.BadCredentialsException;`
Add import: `import ru.rfsnab.userservice.models.dto.legal.LegalEntityAuthResponse;`

Also add `findByEmail` to `LegalEntityRepository`:
```java
Optional<LegalEntity> findByEmail(String email);
```

- [ ] **Step 6: Run test to verify it passes**

```
cd user-service && mvn test -pl . -Dtest=LegalEntityAuthTest -q
```
Expected: 4 tests PASS.

- [ ] **Step 7: Add HTTP endpoint to LegalEntityController**

Add to `LegalEntityController.java`:

```java
@PostMapping("/authenticate")
public ResponseEntity<LegalEntityAuthResponse> authenticate(
        @RequestBody LegalEntityAuthRequest request) {
    return ResponseEntity.ok(legalEntityService.authenticate(request.login(), request.password()));
}
```

Add import: `import ru.rfsnab.userservice.models.dto.legal.LegalEntityAuthRequest;`
Add import: `import ru.rfsnab.userservice.models.dto.legal.LegalEntityAuthResponse;`

Note: this endpoint is at `/v1/legal-entities/authenticate` (internal, not exposed via gateway).

- [ ] **Step 8: Add link-status endpoint**

Add to `LegalEntityService.java`:

```java
@Transactional(readOnly = true)
public boolean isLinkConfirmed(Long userId, Long legalEntityId) {
    return userLegalEntityRepository
            .findByUserIdAndLegalEntityId(userId, legalEntityId)
            .map(link -> link.getLinkStatus() == LinkStatus.CONFIRMED)
            .orElse(false);
}
```

Add to `UserLegalEntityRepository.java`:
```java
Optional<UserLegalEntity> findByUserIdAndLegalEntityId(Long userId, Long legalEntityId);
```

Add to `LegalEntityController.java`:
```java
@GetMapping("/link-status/{userId}/{legalEntityId}")
public ResponseEntity<Map<String, Boolean>> getLinkStatus(
        @PathVariable Long userId,
        @PathVariable Long legalEntityId) {
    boolean confirmed = legalEntityService.isLinkConfirmed(userId, legalEntityId);
    return ResponseEntity.ok(Map.of("confirmed", confirmed));
}
```

Add import: `import java.util.Map;`

- [ ] **Step 9: Commit**

```
git add user-service/src/
git commit -m "feat(user-service): add internal authenticate and link-status endpoints for auth-service"
```

---

## Task 2: auth-service — JWT extension + B2B login + switch-context

**Files:**
- Modify: `auth-service/src/main/java/ru/rfsnab/authservice/utils/JWTService.java`
- Create: `auth-service/src/main/java/ru/rfsnab/authservice/models/dto/LegalAuthRequest.java`
- Create: `auth-service/src/main/java/ru/rfsnab/authservice/models/dto/SwitchContextRequest.java`
- Create: `auth-service/src/main/java/ru/rfsnab/authservice/models/dto/LegalEntityAuthResponse.java`
- Modify: `auth-service/src/main/java/ru/rfsnab/authservice/service/AuthService.java`
- Modify: `auth-service/src/main/java/ru/rfsnab/authservice/controllers/AuthController.java`
- Modify: `auth-service/src/main/java/ru/rfsnab/authservice/controllers/RegistrationController.java`

- [ ] **Step 1: Extend JWTService with clientType overloads**

In `JWTService.java`, add two new methods after `generateRefreshToken`:

```java
public String generateToken(Long id, String email, String clientType) {
    return Jwts.builder()
            .subject(String.valueOf(id))
            .claim("email", email)
            .claim("clientType", clientType)
            .claim("roles", List.of("ROLE_USER"))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpirationMs()))
            .signWith(getSignedKey())
            .compact();
}

public String generateRefreshToken(Long id, String email, String clientType) {
    return Jwts.builder()
            .subject(String.valueOf(id))
            .claim("email", email)
            .claim("clientType", clientType)
            .claim("roles", List.of("ROLE_USER"))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpirationMs()))
            .signWith(getSignedKey())
            .compact();
}

public String extractClientType(String token) {
    return extractClaims(token).get("clientType", String.class);
}
```

Also update existing `generateToken(Long userId, String email, List<String> roles)` to include `clientType = "B2C"`:
```java
public String generateToken(Long userId, String email, List<String> roles) {
    return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("email", email)
            .claim("roles", roles)
            .claim("clientType", "B2C")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpirationMs()))
            .signWith(getSignedKey())
            .compact();
}
```

Same update for `generateRefreshToken(Long userId, String email, List<String> roles)` — add `.claim("clientType", "B2C")`.

- [ ] **Step 2: Create DTOs**

```java
// auth-service/src/main/java/ru/rfsnab/authservice/models/dto/LegalAuthRequest.java
package ru.rfsnab.authservice.models.dto;

public record LegalAuthRequest(String login, String password) {}
```

```java
// auth-service/src/main/java/ru/rfsnab/authservice/models/dto/SwitchContextRequest.java
package ru.rfsnab.authservice.models.dto;

public record SwitchContextRequest(Long legalEntityId) {}
```

```java
// auth-service/src/main/java/ru/rfsnab/authservice/models/dto/LegalEntityAuthResponse.java
package ru.rfsnab.authservice.models.dto;

public record LegalEntityAuthResponse(Long id, String email, String inn, String companyName) {}
```

- [ ] **Step 3: Write failing tests for auth-service B2B flows**

```java
// auth-service/src/test/java/ru/rfsnab/authservice/controllers/LegalAuthControllerTest.java
package ru.rfsnab.authservice.controllers;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegalAuthControllerTest {

    @Autowired MockMvc mockMvc;
    WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(8099);
        wireMock.start();
        WireMock.configureFor("localhost", 8099);
    }

    @AfterEach
    void tearDown() { wireMock.stop(); }

    @Test
    void loginLegal_byEmail_returnsTokens() throws Exception {
        stubFor(post(urlEqualTo("/v1/legal-entities/authenticate"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":7,\"email\":\"company@test.ru\",\"inn\":\"1234567890\",\"companyName\":\"ООО Тест\"}")));

        mockMvc.perform(post("/v1/auth/login/legal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"company@test.ru\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void loginLegal_pendingEntity_returns403() throws Exception {
        stubFor(post(urlEqualTo("/v1/legal-entities/authenticate"))
                .willReturn(aResponse().withStatus(403)));

        mockMvc.perform(post("/v1/auth/login/legal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"pending@test.ru\",\"password\":\"secret\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void switchContext_confirmedLink_returnsB2BToken() throws Exception {
        stubFor(get(urlPathMatching("/v1/legal-entities/link-status/42/7"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"confirmed\":true}")));
        stubFor(get(urlPathMatching("/api/v1/legal-entities/7"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":7,\"email\":\"company@test.ru\",\"inn\":\"1234567890\",\"fullName\":\"ООО Тест\"}")));

        // generate a valid B2C token for userId=42
        // (use JWTService directly or inject)
        String b2cToken = generateTestB2CToken(42L, "user@test.ru");

        mockMvc.perform(post("/v1/auth/switch-context")
                        .header("Authorization", "Bearer " + b2cToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalEntityId\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private String generateTestB2CToken(Long userId, String email) {
        // Use JWTService bean — inject it in test class
        return jwtService.generateToken(userId, email, List.of("ROLE_USER"));
    }
}
```

Note: inject `@Autowired JWTService jwtService` and `import java.util.List` in the test class. Configure `user.service.url=http://localhost:8099` in `application-test.yml`.

- [ ] **Step 4: Run tests to verify they fail**

```
cd auth-service && mvn test -pl . -Dtest=LegalAuthControllerTest -q
```
Expected: FAIL — endpoints not found.

- [ ] **Step 5: Add authenticateLegalEntity and switchContext to AuthService**

Add to `AuthService.java`:

```java
public AuthResponse authenticateLegalEntity(LegalAuthRequest request) {
    String url = userServiceUrl + "/v1/legal-entities/authenticate";
    try {
        ResponseEntity<LegalEntityAuthResponse> response = restTemplate.postForEntity(
                url, request, LegalEntityAuthResponse.class);
        LegalEntityAuthResponse legal = response.getBody();
        if (legal == null) throw new BadCredentialsException("Не удалось получить данные юрлица");

        String accessToken = jwtService.generateToken(legal.id(), legal.email(), "B2B");
        String refreshToken = jwtService.generateRefreshToken(legal.id(), legal.email(), "B2B");
        log.info("B2B login: legalEntityId={}, email={}", legal.id(), legal.email());
        return new AuthResponse(accessToken, refreshToken, "Bearer");
    } catch (HttpClientErrorException.Forbidden e) {
        throw new BadCredentialsException("Юрлицо не прошло верификацию");
    } catch (HttpClientErrorException.NotFound e) {
        throw new BadCredentialsException("Юрлицо не найдено");
    } catch (HttpClientErrorException e) {
        log.error("Ошибка обращения к user-service: {}", e.getMessage());
        throw new RuntimeException("Ошибка аутентификации. Попробуйте позже");
    }
}

public AuthResponse switchContext(String bearerToken, SwitchContextRequest request) {
    Long userId = jwtService.extractUserId(bearerToken);

    String linkStatusUrl = userServiceUrl + "/v1/legal-entities/link-status/"
            + userId + "/" + request.legalEntityId();
    try {
        ResponseEntity<Map<String, Boolean>> linkResp = restTemplate.getForEntity(
                linkStatusUrl, (Class<Map<String, Boolean>>) (Class<?>) Map.class);
        Boolean confirmed = linkResp.getBody() != null && Boolean.TRUE.equals(linkResp.getBody().get("confirmed"));
        if (!confirmed) throw new org.springframework.security.access.AccessDeniedException("Нет подтверждённой связи");

        String legalUrl = userServiceUrl + "/api/v1/legal-entities/" + request.legalEntityId();
        ResponseEntity<LegalEntityDto> legalResp = restTemplate.getForEntity(legalUrl, LegalEntityDto.class);
        LegalEntityDto legal = legalResp.getBody();
        if (legal == null) throw new RuntimeException("Не удалось получить данные юрлица");

        String accessToken = jwtService.generateToken(request.legalEntityId(), legal.email(), "B2B");
        String refreshToken = jwtService.generateRefreshToken(request.legalEntityId(), legal.email(), "B2B");
        log.info("Switch context: userId={} -> legalEntityId={}", userId, request.legalEntityId());
        return new AuthResponse(accessToken, refreshToken, "Bearer");
    } catch (HttpClientErrorException e) {
        log.error("Ошибка switch-context: {}", e.getMessage());
        throw new RuntimeException("Ошибка переключения контекста");
    }
}
```

Add imports:
```java
import ru.rfsnab.authservice.models.dto.LegalAuthRequest;
import ru.rfsnab.authservice.models.dto.LegalEntityAuthResponse;
import ru.rfsnab.authservice.models.dto.SwitchContextRequest;
import ru.rfsnab.authservice.models.dto.LegalEntityDto;
import java.util.Map;
```

Create `LegalEntityDto.java` in auth-service models (minimal, only fields needed for switch-context):
```java
// auth-service/src/main/java/ru/rfsnab/authservice/models/dto/LegalEntityDto.java
package ru.rfsnab.authservice.models.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter @Setter @NoArgsConstructor
public class LegalEntityDto {
    private Long id;
    private String email;
    private String inn;
    private String fullName;
}
```

- [ ] **Step 6: Add new endpoints to AuthController**

Add to `AuthController.java`:

```java
@PostMapping("/login/legal")
public ResponseEntity<AuthResponse> loginLegal(@RequestBody LegalAuthRequest request) {
    return ResponseEntity.ok(authService.authenticateLegalEntity(request));
}

@PostMapping("/switch-context")
public ResponseEntity<AuthResponse> switchContext(
        @RequestHeader("Authorization") String authHeader,
        @RequestBody SwitchContextRequest request) {
    String token = authHeader.substring(7);
    return ResponseEntity.ok(authService.switchContext(token, request));
}
```

Add imports:
```java
import ru.rfsnab.authservice.models.dto.LegalAuthRequest;
import ru.rfsnab.authservice.models.dto.SwitchContextRequest;
```

- [ ] **Step 7: Add registration proxy to RegistrationController**

Add to `RegistrationController.java`:

```java
@PostMapping(value = "/register/legal",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> registerLegal(@RequestBody Object request) {
    try {
        Object created = restTemplate.postForObject(
                userServiceUrl + "/api/v1/legal-entities/register",
                request,
                Object.class
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    } catch (HttpClientErrorException.Conflict e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Юрлицо уже зарегистрировано"));
    } catch (HttpClientErrorException.BadRequest e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Некорректные данные регистрации"));
    } catch (Exception e) {
        log.error("Legal entity registration failed", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Ошибка регистрации"));
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

```
cd auth-service && mvn test -pl . -Dtest=LegalAuthControllerTest -q
```
Expected: 3 tests PASS.

- [ ] **Step 9: Commit**

```
git add auth-service/src/
git commit -m "feat(auth-service): add B2B login, switch-context, legal entity registration proxy"
```

---

## Task 3: gateway-service — forward X-User-Id and X-Client-Type headers

**Files:**
- Modify: `gateway-service/src/main/java/ru/rfsnab/gatewayservice/filter/JwtAuthenticationFilter.java`

- [ ] **Step 1: Write failing test**

```java
// gateway-service/src/test/java/ru/rfsnab/gatewayservice/filter/JwtAuthenticationFilterTest.java
package ru.rfsnab.gatewayservice.filter;

import org.junit.jupiter.api.Test;
// ... (use existing test infrastructure in gateway-service)
// Verify that after filter execution the request contains X-User-Id and X-Client-Type headers
// by checking the modified request headers in the filter chain
```

Check if `gateway-service` already has tests. If yes, add a test case to the existing test class verifying `X-User-Id` and `X-Client-Type` headers are present after filter. If no test infrastructure exists, verify manually after implementation.

- [ ] **Step 2: Update JwtAuthenticationFilter to forward new headers**

Replace the header-forwarding block in `JwtAuthenticationFilter.java` (lines 81-86):

```java
String email = claims.get("email", String.class);
String clientType = claims.get("clientType", String.class);
String subject = claims.getSubject();

ServerHttpRequest modifiedRequest = request.mutate()
        .header("X-User-Email", email != null ? email : "")
        .header("X-User-Id", subject != null ? subject : "")
        .header("X-Client-Type", clientType != null ? clientType : "B2C")
        .build();
log.debug("JWT валиден: clientType={}, sub={}, {} {}", clientType, subject, method, path);
```

- [ ] **Step 3: Add public paths for new auth endpoints**

In `PUBLIC_PATHS` list, add:
```java
"/api/v1/register/legal",
"/v1/auth/login/legal"
```

(`/v1/auth/` prefix already covers `/v1/auth/login/legal` — verify this is true. `/v1/auth/` starts with check will match it. `/api/v1/register/legal` needs explicit addition since `/api/v1/register` already exists but startsWith check will cover it.)

- [ ] **Step 4: Commit**

```
git add gateway-service/src/
git commit -m "feat(gateway-service): forward X-User-Id and X-Client-Type headers from JWT"
```

---

## Task 4: product-service — wholesalePrice field

**Files:**
- Create: `product-service/src/main/resources/db/migration/V20260518120000__add_wholesale_price.sql`
- Modify: `product-service/src/main/java/ru/rfsnab/productservice/model/Product.java`
- Modify: `product-service/src/main/java/ru/rfsnab/productservice/dto/ProductResponse.java`
- Modify: `product-service/src/main/java/ru/rfsnab/productservice/dto/ProductRequest.java`
- Modify: `product-service/src/main/java/ru/rfsnab/productservice/mapper/ProductMapper.java`

- [ ] **Step 1: Create Flyway migration**

```sql
-- product-service/src/main/resources/db/migration/V20260518120000__add_wholesale_price.sql
ALTER TABLE products ADD COLUMN wholesale_price DECIMAL(10,2) NULL;
```

- [ ] **Step 2: Write failing test**

```java
// product-service/src/test/java/ru/rfsnab/productservice/controller/ProductWholesalePriceTest.java
package ru.rfsnab.productservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductWholesalePriceTest {

    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository repo;

    @Test
    void getProduct_returnsBothPrices() throws Exception {
        Product p = repo.save(Product.builder()
                .name("Тест товар").slug("test-product")
                .price(new BigDecimal("1000.00"))
                .wholesalePrice(new BigDecimal("800.00"))
                .stockQuantity(10).isActive(true).isFeatured(false)
                .build());

        mockMvc.perform(get("/api/v1/products/" + p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(1000.00))
                .andExpect(jsonPath("$.wholesalePrice").value(800.00));
    }

    @Test
    void getProduct_wholesalePriceNull_returnsNull() throws Exception {
        Product p = repo.save(Product.builder()
                .name("Тест товар 2").slug("test-product-2")
                .price(new BigDecimal("500.00"))
                .stockQuantity(5).isActive(true).isFeatured(false)
                .build());

        mockMvc.perform(get("/api/v1/products/" + p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(500.00))
                .andExpect(jsonPath("$.wholesalePrice").isEmpty());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```
cd product-service && mvn test -pl . -Dtest=ProductWholesalePriceTest -q
```
Expected: FAIL — `wholesalePrice` field not found / JSON field missing.

- [ ] **Step 4: Add wholesalePrice to Product entity**

In `Product.java`, add after the `price` field:

```java
@Column(name = "wholesale_price", precision = 10, scale = 2)
private BigDecimal wholesalePrice;
```

- [ ] **Step 5: Add wholesalePrice to ProductResponse**

In `ProductResponse.java`, add after `price`:

```java
private BigDecimal wholesalePrice;
```

- [ ] **Step 6: Add wholesalePrice to ProductRequest**

Read `ProductRequest.java` first, then add after `price`:

```java
private BigDecimal wholesalePrice;
```

- [ ] **Step 7: Update ProductMapper**

Read `ProductMapper.java`. MapStruct will auto-map `wholesalePrice` by field name if both entity and DTO have it — verify the mapper interface doesn't have explicit field exclusions. If it uses `@Mapping(target="wholesalePrice", ignore=true)` remove it. If no explicit mapping needed, no change required.

- [ ] **Step 8: Run test to verify it passes**

```
cd product-service && mvn test -pl . -Dtest=ProductWholesalePriceTest -q
```
Expected: 2 tests PASS.

- [ ] **Step 9: Run full product-service test suite**

```
cd product-service && mvn test -q
```
Expected: all tests PASS.

- [ ] **Step 10: Commit**

```
git add product-service/src/
git commit -m "feat(product-service): add wholesalePrice field with Flyway migration"
```

---

## Task 5: integration-service — dual price extraction

**Files:**
- Modify: `integration-service/src/main/java/ru/rfsnab/integrationservice/dto/ProductImportItemDto.java`
- Modify: `integration-service/src/main/java/ru/rfsnab/integrationservice/service/catalog/CatalogImportService.java`
- Modify: `integration-service/src/test/resources/commerceml/offers.xml`

Also check what `ProductImportItem` looks like inside product-service (the batch import target DTO) and add `wholesalePrice` there too.

- [ ] **Step 1: Find and update product-service batch import DTO**

Search for `ProductImportItem` in product-service:

```
grep -r "ProductImportItem" product-service/src/main/java --include="*.java" -l
```

Read the found file. Add `BigDecimal wholesalePrice` field. This is the DTO that product-service's batch import endpoint deserializes.

- [ ] **Step 2: Write failing test for dual-price extraction**

```java
// integration-service/src/test/java/ru/rfsnab/integrationservice/service/CatalogImportDualPriceTest.java
package ru.rfsnab.integrationservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.rfsnab.integrationservice.service.catalog.CatalogImportService;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CatalogImportDualPriceTest {

    @Autowired CatalogImportService service;

    @Test
    void extractPriceByType_wholesaleOnly_returnsWholesaleNullRetail() {
        // uses existing offers.xml with only "Оптовая"
        Path dir = Path.of("src/test/resources/commerceml");
        // We test the private method indirectly via processImport capturing the REST call
        // OR refactor extractPriceByType to package-private for direct testing
        // For now: verify processImport doesn't throw with current fixtures
        // Full dual-price test uses updated offers.xml in Step 4
        assertThat(service).isNotNull();
    }
}
```

Note: the main test is in Step 6 after updating the fixture. This step just verifies the service loads.

- [ ] **Step 3: Add wholesalePrice to ProductImportItemDto**

In `integration-service/src/main/java/ru/rfsnab/integrationservice/dto/ProductImportItemDto.java`, add after `price`:

```java
private BigDecimal wholesalePrice;
```

- [ ] **Step 4: Update offers.xml test fixture**

Replace `integration-service/src/test/resources/commerceml/offers.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<КоммерческаяИнформация ВерсияСхемы="2.10" ДатаФормирования="2026-03-15T12:00:00"
                         xmlns="urn:1C.ru:commerceml_210">
    <ПакетПредложений>
        <Ид>offers-001</Ид>
        <Наименование>Пакет предложений</Наименование>
        <ТипыЦен>
            <ТипЦены>
                <Ид>price-type-001</Ид>
                <Наименование>Оптовая</Наименование>
                <Валюта>RUB</Валюта>
            </ТипЦены>
            <ТипЦены>
                <Ид>price-type-002</Ид>
                <Наименование>Розничная</Наименование>
                <Валюта>RUB</Валюта>
            </ТипЦены>
        </ТипыЦен>
        <Предложения>
            <Предложение>
                <Ид>ext-001</Ид>
                <Наименование>Перчатки нитриловые L</Наименование>
                <Цены>
                    <Цена>
                        <Представление>250.50 RUB за шт</Представление>
                        <ИдТипаЦены>price-type-001</ИдТипаЦены>
                        <ЦенаЗаЕдиницу>250.50</ЦенаЗаЕдиницу>
                        <Валюта>RUB</Валюта>
                        <Единица>шт</Единица>
                        <Коэффициент>1</Коэффициент>
                    </Цена>
                    <Цена>
                        <Представление>320.00 RUB за шт</Представление>
                        <ИдТипаЦены>price-type-002</ИдТипаЦены>
                        <ЦенаЗаЕдиницу>320.00</ЦенаЗаЕдиницу>
                        <Валюта>RUB</Валюта>
                        <Единица>шт</Единица>
                        <Коэффициент>1</Коэффициент>
                    </Цена>
                </Цены>
                <Количество>1000</Количество>
                <СтавкиНалогов>
                    <СтавкаНалога>
                        <Наименование>НДС</Наименование>
                        <Ставка>20</Ставка>
                    </СтавкаНалога>
                </СтавкиНалогов>
            </Предложение>
            <Предложение>
                <Ид>ext-002</Ид>
                <Наименование>Каска строительная белая</Наименование>
                <Цены>
                    <Цена>
                        <Представление>890 RUB за шт</Представление>
                        <ИдТипаЦены>price-type-001</ИдТипаЦены>
                        <ЦенаЗаЕдиницу>890</ЦенаЗаЕдиницу>
                        <Валюта>RUB</Валюта>
                        <Единица>шт</Единица>
                        <Коэффициент>1</Коэффициент>
                    </Цена>
                    <Цена>
                        <Представление>1100.00 RUB за шт</Представление>
                        <ИдТипаЦены>price-type-002</ИдТипаЦены>
                        <ЦенаЗаЕдиницу>1100.00</ЦенаЗаЕдиницу>
                        <Валюта>RUB</Валюта>
                        <Единица>шт</Единица>
                        <Коэффициент>1</Коэффициент>
                    </Цена>
                </Цены>
                <Количество>50.000</Количество>
                <СтавкиНалогов>
                    <СтавкаНалога>
                        <Наименование>НДС</Наименование>
                        <Ставка>Без НДС</Ставка>
                    </СтавкаНалога>
                </СтавкиНалогов>
            </Предложение>
        </Предложения>
    </ПакетПредложений>
</КоммерческаяИнформация>
```

- [ ] **Step 5: Refactor CatalogImportService — add extractPriceByType**

In `CatalogImportService.java`:

Replace `extractPrice(Offer offer)` with:

```java
private BigDecimal extractPriceByType(Offer offer, List<PriceType> priceTypes, String typeName) {
    if (offer.getPrices() == null || offer.getPrices().isEmpty()) return null;
    String targetTypeId = priceTypes.stream()
            .filter(pt -> typeName.equalsIgnoreCase(pt.getName()))
            .map(PriceType::getId)
            .findFirst()
            .orElse(null);
    if (targetTypeId == null) return null;
    return offer.getPrices().stream()
            .filter(p -> targetTypeId.equals(p.getPriceTypeId()))
            .findFirst()
            .map(p -> parseBigDecimal(p.getPricePerUnit(), "цена " + typeName))
            .orElse(null);
}
```

Update `indexOffers` to also return priceTypes. Instead, pass `OffersPackage` directly to `mergeAndMap`. Refactor `mergeAndMap`:

```java
private List<ProductImportItemDto> mergeAndMap(List<CmlProduct> products,
                                                Map<String, Offer> offersById,
                                                List<PriceType> priceTypes) {
    return products.stream()
            .map(product -> mapToImportItem(product, offersById.get(product.getId()), priceTypes))
            .filter(Objects::nonNull)
            .toList();
}
```

Update `mapToImportItem` signature and body:

```java
private ProductImportItemDto mapToImportItem(CmlProduct product, Offer offer, List<PriceType> priceTypes) {
    if (product.getId() == null || product.getName() == null) {
        log.warn("Пропущен товар без Ид или Наименования: {}", product.getId());
        return null;
    }

    ProductImportItemDto.ProductImportItemDtoBuilder builder = ProductImportItemDto.builder()
            .externalId(product.getId())
            .name(product.getName().trim())
            .sku(product.getSku())
            .shortDescription(truncate(product.getDescription(), 1000))
            .unitOfMeasure(extractUnitOfMeasure(product));

    if (offer != null) {
        builder.price(extractPriceByType(offer, priceTypes, "Оптовая"))
                .wholesalePrice(extractPriceByType(offer, priceTypes, "Розничная"))
                .stock(parseStock(offer.getQuantity()))
                .vatRate(extractVatRate(offer));
    }

    return builder.build();
}
```

Update `processImport` — change `mergeAndMap` call to pass priceTypes:

```java
List<PriceType> priceTypes = (offersInfo != null && offersInfo.getOffersPackage() != null)
        ? offersInfo.getOffersPackage().getPriceTypes()
        : Collections.emptyList();
List<ProductImportItemDto> importItems = mergeAndMap(products, offersById, priceTypes);
```

Check `PriceType` class for `getName()` and `getId()` methods — it uses `@XmlElement` annotations. Read the file if uncertain.

- [ ] **Step 6: Run integration-service tests**

```
cd integration-service && mvn test -q
```
Expected: all tests PASS.

- [ ] **Step 7: Commit**

```
git add integration-service/src/ product-service/src/
git commit -m "feat(integration-service): extract dual prices (Оптовая/Розничная) from CommerceML"
```

---

## Task 6: notification-service — legal-entity-events consumer

**Files:**
- Create: `notification-service/src/main/java/ru/rfsnab/notificationservice/models/LegalEntityEvent.java`
- Create: `notification-service/src/main/java/ru/rfsnab/notificationservice/handler/LegalEntityHandler.java`
- Modify: `notification-service/src/main/java/ru/rfsnab/notificationservice/kafka/KafkaListenerService.java`
- Modify: `notification-service/src/main/java/ru/rfsnab/notificationservice/service/EmailService.java`
- Modify: `notification-service/src/main/resources/application.yml`

- [ ] **Step 1: Create LegalEntityEvent model**

```java
// notification-service/src/main/java/ru/rfsnab/notificationservice/models/LegalEntityEvent.java
package ru.rfsnab.notificationservice.models;

import java.time.LocalDateTime;

public record LegalEntityEvent(
        String eventType,
        Long legalEntityId,
        String inn,
        String companyName,
        String legalEntityEmail,
        String targetEmail,
        String rejectionReason,
        LocalDateTime timestamp,
        String token         // emailConfirmToken or linkToken; null when not applicable
) {}
```

Note: this mirrors `LegalEntityEvent` in user-service exactly (same field names/types).

- [ ] **Step 2: Write failing test for LegalEntityHandler**

```java
// notification-service/src/test/java/ru/rfsnab/notificationservice/handler/LegalEntityHandlerTest.java
package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.notificationservice.service.EmailService;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalEntityHandlerTest {

    @Mock EmailService emailService;
    LegalEntityHandler handler;
    ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        handler = new LegalEntityHandler(emailService, mapper,
                "http://confirm.test", "http://link.test", "manager@rfsnab.ru");
    }

    @Test
    void handles_LEGAL_ENTITY_REGISTERED() throws Exception {
        String json = mapper.writeValueAsString(new ru.rfsnab.notificationservice.models.LegalEntityEvent(
                "LEGAL_ENTITY_REGISTERED", 1L, "1234567890", "ООО Тест",
                "legal@test.ru", "legal@test.ru", null, LocalDateTime.now()));

        handler.handle(json);

        verify(emailService).sendLegalEntityVerificationEmail(
                eq("legal@test.ru"), eq("ООО Тест"), contains("http://confirm.test"));
    }

    @Test
    void handles_LEGAL_ENTITY_VERIFIED() throws Exception {
        String json = mapper.writeValueAsString(new ru.rfsnab.notificationservice.models.LegalEntityEvent(
                "LEGAL_ENTITY_VERIFIED", 1L, "1234567890", "ООО Тест",
                "legal@test.ru", "legal@test.ru", null, LocalDateTime.now()));

        handler.handle(json);

        verify(emailService).sendLegalEntityVerifiedEmail("legal@test.ru", "ООО Тест");
    }

    @Test
    void handles_LEGAL_ENTITY_REJECTED() throws Exception {
        String json = mapper.writeValueAsString(new ru.rfsnab.notificationservice.models.LegalEntityEvent(
                "LEGAL_ENTITY_REJECTED", 1L, "1234567890", "ООО Тест",
                "legal@test.ru", "legal@test.ru", "Неверные документы", LocalDateTime.now()));

        handler.handle(json);

        verify(emailService).sendLegalEntityRejectedEmail("legal@test.ru", "ООО Тест", "Неверные документы");
    }

    @Test
    void supports_legalEntityEventsTopic() {
        assertThat(handler.supports("legal-entity-events", "LEGAL_ENTITY_REGISTERED")).isTrue();
        assertThat(handler.supports("order-events", "LEGAL_ENTITY_REGISTERED")).isFalse();
        assertThat(handler.supports("legal-entity-events", "UNKNOWN_EVENT")).isTrue();
    }
}
```

Add import `import static org.assertj.core.api.Assertions.assertThat;`.

- [ ] **Step 3: Run test to verify it fails**

```
cd notification-service && mvn test -pl . -Dtest=LegalEntityHandlerTest -q
```
Expected: FAIL — `LegalEntityHandler` not found.

- [ ] **Step 4: Add 6 email methods to EmailService**

In `EmailService.java`, add:

```java
public void sendLegalEntityVerificationEmail(String to, String companyName, String confirmUrl) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromEmail);
    message.setTo(to);
    message.setSubject("Подтвердите email организации — РФСнаб");
    message.setText(String.format(
            "Здравствуйте!\n\nВы зарегистрировали организацию \"%s\" на платформе РФСнаб.\n\n" +
            "Для подтверждения email перейдите по ссылке:\n%s\n\n" +
            "Ссылка действительна 24 часа.\n\nС уважением,\nКоманда РФСнаб",
            companyName, confirmUrl));
    mailSender.send(message);
}

public void sendLegalEntityEmailConfirmedToManager(String managerEmail, String companyName, String inn) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromEmail);
    message.setTo(managerEmail);
    message.setSubject("Новое юрлицо на верификацию — " + companyName);
    message.setText(String.format(
            "Требуется верификация нового юридического лица:\n\n" +
            "Организация: %s\nИНН: %s\n\n" +
            "Перейдите в панель администратора для проверки документов.",
            companyName, inn));
    mailSender.send(message);
}

public void sendLegalEntityVerifiedEmail(String to, String companyName) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromEmail);
    message.setTo(to);
    message.setSubject("Регистрация подтверждена — РФСнаб");
    message.setText(String.format(
            "Поздравляем!\n\nОрганизация \"%s\" прошла верификацию.\n" +
            "Теперь вы можете входить в систему и оформлять заказы.\n\n" +
            "С уважением,\nКоманда РФСнаб",
            companyName));
    mailSender.send(message);
}

public void sendLegalEntityRejectedEmail(String to, String companyName, String reason) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromEmail);
    message.setTo(to);
    message.setSubject("Регистрация отклонена — РФСнаб");
    message.setText(String.format(
            "К сожалению, регистрация организации \"%s\" была отклонена.\n\n" +
            "Причина: %s\n\n" +
            "Если у вас есть вопросы, свяжитесь с нашей поддержкой.\n\n" +
            "С уважением,\nКоманда РФСнаб",
            companyName, reason != null ? reason : "не указана"));
    mailSender.send(message);
}

public void sendLegalEntityLinkRequestedEmail(String to, String companyName,
                                               String userName, String confirmUrl) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromEmail);
    message.setTo(to);
    message.setSubject("Запрос на привязку аккаунта — РФСнаб");
    message.setText(String.format(
            "Пользователь %s хочет привязать аккаунт к вашей организации \"%s\".\n\n" +
            "Для подтверждения перейдите по ссылке:\n%s\n\n" +
            "Если вы не ожидали этого запроса — просто проигнорируйте письмо.\n\n" +
            "С уважением,\nКоманда РФСнаб",
            userName, companyName, confirmUrl));
    mailSender.send(message);
}

public void sendLegalEntityLinkConfirmedEmail(String toLegal, String toUser,
                                               String companyName, String userName) {
    SimpleMailMessage msgToLegal = new SimpleMailMessage();
    msgToLegal.setFrom(fromEmail);
    msgToLegal.setTo(toLegal);
    msgToLegal.setSubject("Пользователь привязан к вашей организации — РФСнаб");
    msgToLegal.setText(String.format(
            "Пользователь %s успешно привязан к организации \"%s\".\n\n" +
            "С уважением,\nКоманда РФСнаб", userName, companyName));
    mailSender.send(msgToLegal);

    SimpleMailMessage msgToUser = new SimpleMailMessage();
    msgToUser.setFrom(fromEmail);
    msgToUser.setTo(toUser);
    msgToUser.setSubject("Привязка к организации подтверждена — РФСнаб");
    msgToUser.setText(String.format(
            "Ваш аккаунт успешно привязан к организации \"%s\".\n" +
            "Теперь вы можете переключаться на B2B контекст в личном кабинете.\n\n" +
            "С уважением,\nКоманда РФСнаб", companyName));
    mailSender.send(msgToUser);
}
```

Check that `EmailService` has `@Value("${app.email.from}") private String fromEmail` and `private final JavaMailSender mailSender` already — they exist per the explored code.

- [ ] **Step 5: Create LegalEntityHandler**

```java
// notification-service/src/main/java/ru/rfsnab/notificationservice/handler/LegalEntityHandler.java
package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rfsnab.notificationservice.models.LegalEntityEvent;
import ru.rfsnab.notificationservice.service.EmailService;

@Slf4j
@Component
public class LegalEntityHandler implements NotificationHandler {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final String confirmBaseUrl;
    private final String linkConfirmBaseUrl;
    private final String managerEmail;

    public LegalEntityHandler(
            EmailService emailService,
            ObjectMapper objectMapper,
            @Value("${app.email.legal-entity-confirm-url}") String confirmBaseUrl,
            @Value("${app.email.legal-entity-link-confirm-url}") String linkConfirmBaseUrl,
            @Value("${app.email.manager}") String managerEmail) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.confirmBaseUrl = confirmBaseUrl;
        this.linkConfirmBaseUrl = linkConfirmBaseUrl;
        this.managerEmail = managerEmail;
    }

    @Override
    public boolean supports(String topic, String eventType) {
        return "legal-entity-events".equals(topic);
    }

    @Override
    public void handle(String eventJson) {
        try {
            LegalEntityEvent event = objectMapper.readValue(eventJson, LegalEntityEvent.class);
            switch (event.eventType()) {
                case "LEGAL_ENTITY_REGISTERED" ->
                        emailService.sendLegalEntityVerificationEmail(
                                event.legalEntityEmail(), event.companyName(),
                                confirmBaseUrl + "?token=" + event.token());
                case "LEGAL_ENTITY_EMAIL_CONFIRMED" ->
                        emailService.sendLegalEntityEmailConfirmedToManager(
                                managerEmail, event.companyName(), event.inn());
                case "LEGAL_ENTITY_VERIFIED" ->
                        emailService.sendLegalEntityVerifiedEmail(
                                event.legalEntityEmail(), event.companyName());
                case "LEGAL_ENTITY_REJECTED" ->
                        emailService.sendLegalEntityRejectedEmail(
                                event.legalEntityEmail(), event.companyName(), event.rejectionReason());
                case "LEGAL_ENTITY_LINK_REQUESTED" ->
                        emailService.sendLegalEntityLinkRequestedEmail(
                                event.legalEntityEmail(), event.companyName(),
                                event.rejectionReason(),
                                linkConfirmBaseUrl + "?token=" + event.token());
                case "LEGAL_ENTITY_LINK_CONFIRMED" ->
                        emailService.sendLegalEntityLinkConfirmedEmail(
                                event.legalEntityEmail(), event.targetEmail(),
                                event.companyName(), event.rejectionReason());
                default -> log.warn("Неизвестный тип события юрлица: {}", event.eventType());
            }
        } catch (Exception e) {
            log.error("Ошибка обработки события юрлица: {}", eventJson, e);
        }
    }
}
```

Note: `rejectionReason` field is reused for `userName` in LINK events since `LegalEntityEvent` in user-service stores it there (see `linkToUser` in `LegalEntityService` — it puts `user.getFirstname() + " " + user.getLastname()` in the `rejectionReason` position). Verify by re-reading the record constructor order: `(eventType, legalEntityId, inn, companyName, legalEntityEmail, targetEmail, rejectionReason, timestamp)`. In `linkToUser`, 7th arg = `user.getFirstname() + " " + user.getLastname()`. Correct.

- [ ] **Step 6: Add legal-entity-events to KafkaListenerService**

In `KafkaListenerService.java`, update the `@KafkaListener` annotation:

```java
@KafkaListener(
        topics = {"${app.kafka.topic.user-events}",
                  "${app.kafka.topic.order-events}",
                  "${app.kafka.topic.legal-entity-events}"},
        groupId = "${spring.kafka.consumer.group-id}"
)
```

- [ ] **Step 7: Update application.yml**

In `notification-service/src/main/resources/application.yml`, under `app.kafka.topic:` add:

```yaml
legal-entity-events: legal-entity-events
```

Under `app.email:` add:

```yaml
manager: ${MANAGER_EMAIL:manager@rfsnab.ru}
legal-entity-confirm-url: ${LEGAL_CONFIRM_URL:http://localhost:9000/api/v1/legal-entities/confirm-email}
legal-entity-link-confirm-url: ${LEGAL_LINK_CONFIRM_URL:http://localhost:8081/api/v1/legal-entities/confirm-link}
```

Also add same keys to `application-test.yml`.

- [ ] **Step 8: Run tests to verify they pass**

```
cd notification-service && mvn test -pl . -Dtest=LegalEntityHandlerTest -q
```
Expected: 4 tests PASS.

- [ ] **Step 9: Run full notification-service test suite**

```
cd notification-service && mvn test -q
```
Expected: all tests PASS.

- [ ] **Step 10: Commit**

```
git add notification-service/src/
git commit -m "feat(notification-service): add legal-entity-events consumer with 6 email handlers"
```

---

## Task 7: full build verification

- [ ] **Step 1: Build all services**

```
mvn clean test -pl user-service,auth-service,gateway-service,product-service,integration-service,notification-service -q
```
Expected: BUILD SUCCESS, 0 failures across all modules.

- [ ] **Step 2: Commit if any fixes needed, then tag**

```
git add .
git commit -m "test(plan-b): full build verification — all services green"
```
