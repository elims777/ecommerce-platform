# Legal Entity (B2B) — user-service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add full LegalEntity support to user-service — registration, email verification, manager verification, linking to physical users, bank accounts, and actual addresses.

**Architecture:** `LegalEntity` is an independent entity with its own email/password. Physical users can link to multiple legal entities via `UserLegalEntity` join table. All async notifications go through a new `legal-entity-events` Kafka topic. Tests follow existing pattern: unit tests with Mockito, controller tests with MockMvc + H2.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Data JPA, Flyway, Spring Kafka, H2 (tests), Lombok, MapStruct

**Spec:** `docs/superpowers/specs/2026-05-17-b2b-b2c-separation-design.md`

---

## File Map

### New files — main
| File | Responsibility |
|------|---------------|
| `models/LegalEntity.java` | JPA entity for legal entities |
| `models/LegalEntityBankAccount.java` | JPA entity for bank accounts |
| `models/LegalEntityAddress.java` | JPA entity for actual addresses |
| `models/UserLegalEntity.java` | Join entity: physical user ↔ legal entity link |
| `models/enums/VerificationStatus.java` | PENDING, VERIFIED, REJECTED |
| `models/enums/LinkStatus.java` | PENDING, CONFIRMED |
| `models/dto/legal/RegisterLegalEntityRequest.java` | Registration DTO with full validation |
| `models/dto/legal/LegalEntityDto.java` | Response DTO |
| `models/dto/legal/LegalEntityAddressDto.java` | Address DTO |
| `models/dto/legal/BankAccountDto.java` | Bank account DTO |
| `models/dto/legal/SaveBankAccountRequest.java` | Create/update bank account |
| `models/dto/legal/SaveLegalEntityAddressRequest.java` | Create address |
| `models/kafka/LegalEntityEvent.java` | Kafka event record |
| `repository/LegalEntityRepository.java` | JPA repository |
| `repository/LegalEntityBankAccountRepository.java` | JPA repository |
| `repository/LegalEntityAddressRepository.java` | JPA repository |
| `repository/UserLegalEntityRepository.java` | JPA repository |
| `services/LegalEntityService.java` | Core business logic |
| `services/LegalEntityKafkaProducerService.java` | Kafka producer for legal entity events |
| `mappers/LegalEntityMapper.java` | Entity ↔ DTO mapping |
| `controllers/LegalEntityController.java` | REST endpoints for legal entity management |
| `controllers/LegalEntityAdminController.java` | Admin endpoints: verify/reject |
| `controllers/UserLegalEntityController.java` | Endpoints for physical user ↔ legal entity linking |
| `exceptions/LegalEntityNotFoundException.java` | |
| `exceptions/LegalEntityAlreadyExistsException.java` | |
| `exceptions/LegalEntityNotVerifiedException.java` | |

### New files — migrations
| File | Content |
|------|---------|
| `resources/db/migration/V20260517180000__add_legal_entities.sql` | All 4 new tables |

### New files — tests
| File | What it tests |
|------|--------------|
| `test/.../services/LegalEntityServiceTest.java` | Unit tests: registration, verification, linking |
| `test/.../controllers/LegalEntityControllerTest.java` | HTTP tests: all endpoints |
| `test/.../controllers/LegalEntityAdminControllerTest.java` | HTTP tests: verify/reject |

### Modified files
| File | Change |
|------|--------|
| `models/kafka/UserEvent.java` | Add `LegalEntityEvent` alongside (or keep separate) |
| `services/KafkaProducerService.java` | Add `sendLegalEntityEvent(LegalEntityEvent)` |
| `resources/application.yml` | Add `legal-entity-events` topic |
| `resources/application-test.yml` | Add topic config for tests |

---

## Task 1: Flyway Migration — 4 New Tables

**Files:**
- Create: `order-service/src/main/resources/db/migration/V20260517180000__add_legal_entities.sql`

> Note: file goes in user-service, not order-service. Path:
> `user-service/src/main/resources/db/migration/V20260517180000__add_legal_entities.sql`

- [ ] **Step 1: Create migration file**

```sql
-- legal_entities: independent entity with own credentials
CREATE TABLE legal_entities (
    id                  BIGSERIAL PRIMARY KEY,
    inn                 VARCHAR(12)  NOT NULL UNIQUE,
    ogrn                VARCHAR(15)  NOT NULL,
    full_name           VARCHAR(255) NOT NULL,
    director            VARCHAR(255) NOT NULL,
    phone               VARCHAR(20)  NOT NULL,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password            VARCHAR(255) NOT NULL,
    -- legal address (embedded, always one)
    legal_city          VARCHAR(150) NOT NULL,
    legal_street        VARCHAR(150) NOT NULL,
    legal_building      VARCHAR(20)  NOT NULL,
    legal_postal_code   VARCHAR(10),
    -- verification
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    verified_at         TIMESTAMP,
    verified_by         VARCHAR(255),
    -- email confirmation token
    email_confirm_token VARCHAR(36) UNIQUE,
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- user_legal_entities: many-to-many link between physical users and legal entities
CREATE TABLE user_legal_entities (
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    legal_entity_id BIGINT NOT NULL REFERENCES legal_entities(id) ON DELETE CASCADE,
    link_status     VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (link_status IN ('PENDING', 'CONFIRMED')),
    link_token      VARCHAR(36) UNIQUE,
    linked_at       TIMESTAMP,
    PRIMARY KEY (user_id, legal_entity_id)
);

-- legal_entity_bank_accounts
CREATE TABLE legal_entity_bank_accounts (
    id                    BIGSERIAL PRIMARY KEY,
    legal_entity_id       BIGINT NOT NULL REFERENCES legal_entities(id) ON DELETE CASCADE,
    bank_name             VARCHAR(255) NOT NULL,
    bik                   VARCHAR(9)   NOT NULL,
    correspondent_account VARCHAR(20)  NOT NULL,
    settlement_account    VARCHAR(20)  NOT NULL,
    is_primary            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

-- legal_entity_addresses: multiple actual (shipping) addresses per legal entity
CREATE TABLE legal_entity_addresses (
    id              BIGSERIAL PRIMARY KEY,
    legal_entity_id BIGINT NOT NULL REFERENCES legal_entities(id) ON DELETE CASCADE,
    city            VARCHAR(150) NOT NULL,
    street          VARCHAR(150) NOT NULL,
    building        VARCHAR(20)  NOT NULL,
    apartment       VARCHAR(20),
    postal_code     VARCHAR(10),
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
```

- [ ] **Step 2: Commit**

```
git add user-service/src/main/resources/db/migration/V20260517180000__add_legal_entities.sql
git commit -m "feat(user-service): add legal entities migration — 4 new tables"
```

---

## Task 2: Enums and Kafka Event

**Files:**
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/enums/VerificationStatus.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/enums/LinkStatus.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/kafka/LegalEntityEvent.java`

- [ ] **Step 1: Create VerificationStatus enum**

```java
package ru.rfsnab.userservice.models.enums;

public enum VerificationStatus {
    PENDING, VERIFIED, REJECTED
}
```

- [ ] **Step 2: Create LinkStatus enum**

```java
package ru.rfsnab.userservice.models.enums;

public enum LinkStatus {
    PENDING, CONFIRMED
}
```

- [ ] **Step 3: Create LegalEntityEvent Kafka record**

```java
package ru.rfsnab.userservice.models.kafka;

import java.time.LocalDateTime;

public record LegalEntityEvent(
        String eventType,       // LEGAL_ENTITY_REGISTERED, LEGAL_ENTITY_EMAIL_CONFIRMED,
                                // LEGAL_ENTITY_VERIFIED, LEGAL_ENTITY_REJECTED,
                                // LEGAL_ENTITY_LINK_REQUESTED, LEGAL_ENTITY_LINK_CONFIRMED
        Long legalEntityId,
        String inn,
        String companyName,
        String legalEntityEmail,
        String targetEmail,     // email for notification (manager, physical user, etc.)
        String rejectionReason, // nullable, for REJECTED events
        LocalDateTime timestamp
) {}
```

- [ ] **Step 4: Commit**

```
git add user-service/src/main/java/ru/rfsnab/userservice/models/enums/
git add user-service/src/main/java/ru/rfsnab/userservice/models/kafka/LegalEntityEvent.java
git commit -m "feat(user-service): add VerificationStatus, LinkStatus enums and LegalEntityEvent"
```

---

## Task 3: JPA Entities

**Files:**
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/LegalEntity.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/LegalEntityBankAccount.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/LegalEntityAddress.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/UserLegalEntity.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/UserLegalEntityId.java`

- [ ] **Step 1: Create LegalEntity entity**

```java
package ru.rfsnab.userservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.rfsnab.userservice.models.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "legal_entities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LegalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String inn;

    @Column(nullable = false, length = 15)
    private String ogrn;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String director;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    // Legal address (embedded — always one)
    @Column(nullable = false, length = 150)
    private String legalCity;

    @Column(nullable = false, length = 150)
    private String legalStreet;

    @Column(nullable = false, length = 20)
    private String legalBuilding;

    @Column(length = 10)
    private String legalPostalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    private LocalDateTime verifiedAt;
    private String verifiedBy;

    @Column(length = 36, unique = true)
    private String emailConfirmToken;

    @Column(nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @OneToMany(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LegalEntityBankAccount> bankAccounts = new ArrayList<>();

    @OneToMany(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LegalEntityAddress> addresses = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Create LegalEntityBankAccount entity**

```java
package ru.rfsnab.userservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "legal_entity_bank_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LegalEntityBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @Column(nullable = false)
    private String bankName;

    @Column(nullable = false, length = 9)
    private String bik;

    @Column(nullable = false, length = 20)
    private String correspondentAccount;

    @Column(nullable = false, length = 20)
    private String settlementAccount;

    @Column(nullable = false)
    @Builder.Default
    private boolean primary = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Create LegalEntityAddress entity**

```java
package ru.rfsnab.userservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "legal_entity_addresses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LegalEntityAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @Column(nullable = false, length = 150)
    private String city;

    @Column(nullable = false, length = 150)
    private String street;

    @Column(nullable = false, length = 20)
    private String building;

    @Column(length = 20)
    private String apartment;

    @Column(length = 10)
    private String postalCode;

    @Column(nullable = false)
    @Builder.Default
    private boolean primary = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Create composite PK class for UserLegalEntity**

```java
package ru.rfsnab.userservice.models;

import java.io.Serializable;
import java.util.Objects;

public class UserLegalEntityId implements Serializable {
    private Long user;
    private Long legalEntity;

    public UserLegalEntityId() {}

    public UserLegalEntityId(Long user, Long legalEntity) {
        this.user = user;
        this.legalEntity = legalEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserLegalEntityId that)) return false;
        return Objects.equals(user, that.user) && Objects.equals(legalEntity, that.legalEntity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, legalEntity);
    }
}
```

- [ ] **Step 5: Create UserLegalEntity join entity**

```java
package ru.rfsnab.userservice.models;

import jakarta.persistence.*;
import lombok.*;
import ru.rfsnab.userservice.models.enums.LinkStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_legal_entities")
@IdClass(UserLegalEntityId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserLegalEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LinkStatus linkStatus = LinkStatus.PENDING;

    @Column(length = 36, unique = true)
    private String linkToken;

    private LocalDateTime linkedAt;
}
```

- [ ] **Step 6: Commit**

```
git add user-service/src/main/java/ru/rfsnab/userservice/models/
git commit -m "feat(user-service): add LegalEntity, BankAccount, Address, UserLegalEntity JPA entities"
```

---

## Task 4: Repositories

**Files:**
- Create: `user-service/src/main/java/ru/rfsnab/userservice/repository/LegalEntityRepository.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/repository/LegalEntityBankAccountRepository.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/repository/LegalEntityAddressRepository.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/repository/UserLegalEntityRepository.java`

- [ ] **Step 1: Create LegalEntityRepository**

```java
package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.enums.VerificationStatus;

import java.util.List;
import java.util.Optional;

public interface LegalEntityRepository extends JpaRepository<LegalEntity, Long> {
    Optional<LegalEntity> findByEmail(String email);
    Optional<LegalEntity> findByInn(String inn);
    Optional<LegalEntity> findByEmailConfirmToken(String token);
    boolean existsByInn(String inn);
    boolean existsByEmail(String email);
    List<LegalEntity> findAllByVerificationStatus(VerificationStatus status);
}
```

- [ ] **Step 2: Create LegalEntityBankAccountRepository**

```java
package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.userservice.models.LegalEntityBankAccount;

import java.util.List;
import java.util.Optional;

public interface LegalEntityBankAccountRepository extends JpaRepository<LegalEntityBankAccount, Long> {
    List<LegalEntityBankAccount> findAllByLegalEntityId(Long legalEntityId);
    Optional<LegalEntityBankAccount> findByIdAndLegalEntityId(Long id, Long legalEntityId);
}
```

- [ ] **Step 3: Create LegalEntityAddressRepository**

```java
package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.userservice.models.LegalEntityAddress;

import java.util.List;
import java.util.Optional;

public interface LegalEntityAddressRepository extends JpaRepository<LegalEntityAddress, Long> {
    List<LegalEntityAddress> findAllByLegalEntityId(Long legalEntityId);
    Optional<LegalEntityAddress> findByIdAndLegalEntityId(Long id, Long legalEntityId);
}
```

- [ ] **Step 4: Create UserLegalEntityRepository**

```java
package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.userservice.models.UserLegalEntity;
import ru.rfsnab.userservice.models.UserLegalEntityId;
import ru.rfsnab.userservice.models.enums.LinkStatus;

import java.util.List;
import java.util.Optional;

public interface UserLegalEntityRepository extends JpaRepository<UserLegalEntity, UserLegalEntityId> {
    List<UserLegalEntity> findAllByUserIdAndLinkStatus(Long userId, LinkStatus status);
    Optional<UserLegalEntity> findByLinkToken(String token);
    boolean existsByUserIdAndLegalEntityId(Long userId, Long legalEntityId);
}
```

- [ ] **Step 5: Commit**

```
git add user-service/src/main/java/ru/rfsnab/userservice/repository/LegalEntity*
git add user-service/src/main/java/ru/rfsnab/userservice/repository/UserLegalEntityRepository.java
git commit -m "feat(user-service): add repositories for LegalEntity, BankAccount, Address, UserLegalEntity"
```

---

## Task 5: DTOs and Mapper

**Files:**
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/dto/legal/RegisterLegalEntityRequest.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/dto/legal/LegalEntityDto.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/dto/legal/LegalEntityAddressDto.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/dto/legal/SaveLegalEntityAddressRequest.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/dto/legal/BankAccountDto.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/models/dto/legal/SaveBankAccountRequest.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/mappers/LegalEntityMapper.java`

- [ ] **Step 1: Create RegisterLegalEntityRequest**

```java
package ru.rfsnab.userservice.models.dto.legal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterLegalEntityRequest(
        @NotBlank @Size(min = 10, max = 12, message = "ИНН должен содержать 10 или 12 цифр")
        @Pattern(regexp = "\\d{10}|\\d{12}", message = "ИНН должен содержать только цифры")
        String inn,

        @NotBlank @Size(min = 13, max = 15)
        @Pattern(regexp = "\\d{13}|\\d{15}", message = "ОГРН должен содержать 13 или 15 цифр")
        String ogrn,

        @NotBlank String fullName,
        @NotBlank String director,

        @NotBlank @Pattern(regexp = "\\+?[0-9]{11}", message = "Некорректный формат телефона")
        String phone,

        @NotBlank @Email String email,

        @NotBlank @Size(min = 8, max = 100) String password,

        // Legal address
        @NotBlank String legalCity,
        @NotBlank String legalStreet,
        @NotBlank String legalBuilding,
        String legalPostalCode
) {}
```

- [ ] **Step 2: Create LegalEntityAddressDto**

```java
package ru.rfsnab.userservice.models.dto.legal;

public record LegalEntityAddressDto(
        Long id,
        String city,
        String street,
        String building,
        String apartment,
        String postalCode,
        boolean primary
) {}
```

- [ ] **Step 3: Create SaveLegalEntityAddressRequest**

```java
package ru.rfsnab.userservice.models.dto.legal;

import jakarta.validation.constraints.NotBlank;

public record SaveLegalEntityAddressRequest(
        @NotBlank String city,
        @NotBlank String street,
        @NotBlank String building,
        String apartment,
        String postalCode,
        boolean primary
) {}
```

- [ ] **Step 4: Create BankAccountDto**

```java
package ru.rfsnab.userservice.models.dto.legal;

public record BankAccountDto(
        Long id,
        String bankName,
        String bik,
        String correspondentAccount,
        String settlementAccount,
        boolean primary
) {}
```

- [ ] **Step 5: Create SaveBankAccountRequest**

```java
package ru.rfsnab.userservice.models.dto.legal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveBankAccountRequest(
        @NotBlank String bankName,
        @NotBlank @Size(min = 9, max = 9) @Pattern(regexp = "\\d{9}") String bik,
        @NotBlank @Size(min = 20, max = 20) String correspondentAccount,
        @NotBlank @Size(min = 20, max = 20) String settlementAccount,
        boolean primary
) {}
```

- [ ] **Step 6: Create LegalEntityDto**

```java
package ru.rfsnab.userservice.models.dto.legal;

import ru.rfsnab.userservice.models.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record LegalEntityDto(
        Long id,
        String inn,
        String ogrn,
        String fullName,
        String director,
        String phone,
        String email,
        String legalCity,
        String legalStreet,
        String legalBuilding,
        String legalPostalCode,
        VerificationStatus verificationStatus,
        LocalDateTime verifiedAt,
        List<BankAccountDto> bankAccounts,
        List<LegalEntityAddressDto> addresses,
        LocalDateTime createdAt
) {}
```

- [ ] **Step 7: Create LegalEntityMapper**

```java
package ru.rfsnab.userservice.mappers;

import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.LegalEntityAddress;
import ru.rfsnab.userservice.models.LegalEntityBankAccount;
import ru.rfsnab.userservice.models.dto.legal.*;

import java.util.List;

public class LegalEntityMapper {

    public static LegalEntityDto toDto(LegalEntity entity) {
        return new LegalEntityDto(
                entity.getId(),
                entity.getInn(),
                entity.getOgrn(),
                entity.getFullName(),
                entity.getDirector(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getLegalCity(),
                entity.getLegalStreet(),
                entity.getLegalBuilding(),
                entity.getLegalPostalCode(),
                entity.getVerificationStatus(),
                entity.getVerifiedAt(),
                entity.getBankAccounts().stream().map(LegalEntityMapper::toBankAccountDto).toList(),
                entity.getAddresses().stream().map(LegalEntityMapper::toAddressDto).toList(),
                entity.getCreatedAt()
        );
    }

    public static BankAccountDto toBankAccountDto(LegalEntityBankAccount account) {
        return new BankAccountDto(
                account.getId(),
                account.getBankName(),
                account.getBik(),
                account.getCorrespondentAccount(),
                account.getSettlementAccount(),
                account.isPrimary()
        );
    }

    public static LegalEntityAddressDto toAddressDto(LegalEntityAddress address) {
        return new LegalEntityAddressDto(
                address.getId(),
                address.getCity(),
                address.getStreet(),
                address.getBuilding(),
                address.getApartment(),
                address.getPostalCode(),
                address.isPrimary()
        );
    }
}
```

- [ ] **Step 8: Commit**

```
git add user-service/src/main/java/ru/rfsnab/userservice/models/dto/legal/
git add user-service/src/main/java/ru/rfsnab/userservice/mappers/LegalEntityMapper.java
git commit -m "feat(user-service): add LegalEntity DTOs and mapper"
```

---

## Task 6: Kafka Producer for Legal Entity Events

**Files:**
- Create: `user-service/src/main/java/ru/rfsnab/userservice/services/LegalEntityKafkaProducerService.java`
- Modify: `user-service/src/main/resources/application.yml`
- Modify: `user-service/src/main/resources/application-test.yml`

- [ ] **Step 1: Create LegalEntityKafkaProducerService**

```java
package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.rfsnab.userservice.models.kafka.LegalEntityEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalEntityKafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.legal-entity-events}")
    private String topic;

    public void send(LegalEntityEvent event) {
        kafkaTemplate.send(topic, event.legalEntityId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send LegalEntityEvent: {}", event.eventType(), ex);
                    }
                });
    }
}
```

- [ ] **Step 2: Add topic to application.yml**

Add under `app.kafka.topic`:
```yaml
app:
  kafka:
    topic:
      user-events: user-events
      legal-entity-events: legal-entity-events
```

- [ ] **Step 3: Add topic to application-test.yml**

Add under `app.kafka.topic`:
```yaml
app:
  kafka:
    topic:
      user-events: user-events
      legal-entity-events: legal-entity-events
```

- [ ] **Step 4: Commit**

```
git add user-service/src/main/java/ru/rfsnab/userservice/services/LegalEntityKafkaProducerService.java
git add user-service/src/main/resources/application.yml
git add user-service/src/main/resources/application-test.yml
git commit -m "feat(user-service): add LegalEntityKafkaProducerService and topic config"
```

---

## Task 7: Exceptions + UserRepository

**Files:**
- Modify: `user-service/src/main/java/ru/rfsnab/userservice/repository/UserRepository.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/exceptions/LegalEntityNotFoundException.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/exceptions/LegalEntityAlreadyExistsException.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/exceptions/LegalEntityNotVerifiedException.java`
- Modify: `user-service/src/main/java/ru/rfsnab/userservice/exceptions/GlobalExceptionHandler.java`

- [ ] **Step 0: Add existsByEmail to UserRepository**

The spec requires that a legal entity email must not coincide with any physical user's email. Add the method:

```java
// in UserRepository.java — add after findByEmail:
boolean existsByEmail(String email);
```

- [ ] **Step 1: Create exceptions**

```java
// LegalEntityNotFoundException.java
package ru.rfsnab.userservice.exceptions;
public class LegalEntityNotFoundException extends RuntimeException {
    public LegalEntityNotFoundException(String message) { super(message); }
}

// LegalEntityAlreadyExistsException.java
package ru.rfsnab.userservice.exceptions;
public class LegalEntityAlreadyExistsException extends RuntimeException {
    public LegalEntityAlreadyExistsException(String message) { super(message); }
}

// LegalEntityNotVerifiedException.java
package ru.rfsnab.userservice.exceptions;
public class LegalEntityNotVerifiedException extends RuntimeException {
    public LegalEntityNotVerifiedException(String message) { super(message); }
}
```

- [ ] **Step 2: Add handlers to GlobalExceptionHandler**

Open `GlobalExceptionHandler.java` and add:

```java
@ExceptionHandler(LegalEntityNotFoundException.class)
@ResponseStatus(HttpStatus.NOT_FOUND)
public ErrorResponse handleLegalEntityNotFound(LegalEntityNotFoundException ex) {
    return new ErrorResponse(ex.getMessage());
}

@ExceptionHandler(LegalEntityAlreadyExistsException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public ErrorResponse handleLegalEntityAlreadyExists(LegalEntityAlreadyExistsException ex) {
    return new ErrorResponse(ex.getMessage());
}

@ExceptionHandler(LegalEntityNotVerifiedException.class)
@ResponseStatus(HttpStatus.FORBIDDEN)
public ErrorResponse handleLegalEntityNotVerified(LegalEntityNotVerifiedException ex) {
    return new ErrorResponse(ex.getMessage());
}
```

- [ ] **Step 3: Commit**

```
git add user-service/src/main/java/ru/rfsnab/userservice/exceptions/LegalEntity*
git add user-service/src/main/java/ru/rfsnab/userservice/exceptions/GlobalExceptionHandler.java
git commit -m "feat(user-service): add LegalEntity exceptions and handlers"
```

---

## Task 8: LegalEntityService — Unit Tests First

**Files:**
- Create: `user-service/src/test/java/ru/rfsnab/userservice/services/LegalEntityServiceTest.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/services/LegalEntityService.java`

- [ ] **Step 1: Write failing tests**

```java
package ru.rfsnab.userservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.rfsnab.userservice.exceptions.LegalEntityAlreadyExistsException;
import ru.rfsnab.userservice.exceptions.LegalEntityNotFoundException;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.LegalEntityBankAccount;
import ru.rfsnab.userservice.models.LegalEntityAddress;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.UserLegalEntity;
import ru.rfsnab.userservice.models.dto.legal.*;
import ru.rfsnab.userservice.models.enums.LinkStatus;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.models.kafka.LegalEntityEvent;
import ru.rfsnab.userservice.repository.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LegalEntityService — unit tests")
class LegalEntityServiceTest {

    @Mock LegalEntityRepository legalEntityRepository;
    @Mock LegalEntityBankAccountRepository bankAccountRepository;
    @Mock LegalEntityAddressRepository addressRepository;
    @Mock UserLegalEntityRepository userLegalEntityRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock LegalEntityKafkaProducerService kafkaProducerService;

    @InjectMocks LegalEntityService legalEntityService;

    private RegisterLegalEntityRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new RegisterLegalEntityRequest(
                "1234567890", "1234567890123",
                "ООО Ромашка", "Иванов Иван Иванович",
                "+79001112233", "company@example.com", "password123",
                "Сыктывкар", "Октябрьский проспект", "1", "167000"
        );
    }

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("регистрирует юрлицо с PENDING статусом")
        void shouldRegisterLegalEntity() {
            when(legalEntityRepository.existsByInn("1234567890")).thenReturn(false);
            when(legalEntityRepository.existsByEmail("company@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("encoded");
            when(legalEntityRepository.save(any())).thenAnswer(inv -> {
                LegalEntity e = inv.getArgument(0);
                e = LegalEntity.builder()
                        .id(1L).inn(e.getInn()).ogrn(e.getOgrn())
                        .fullName(e.getFullName()).director(e.getDirector())
                        .phone(e.getPhone()).email(e.getEmail()).password(e.getPassword())
                        .legalCity(e.getLegalCity()).legalStreet(e.getLegalStreet())
                        .legalBuilding(e.getLegalBuilding())
                        .verificationStatus(VerificationStatus.PENDING)
                        .emailVerified(false)
                        .build();
                return e;
            });
            doNothing().when(kafkaProducerService).send(any(LegalEntityEvent.class));

            LegalEntity result = legalEntityService.register(validRequest);

            assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
            assertThat(result.isEmailVerified()).isFalse();
            assertThat(result.getPassword()).isEqualTo("encoded");
            verify(kafkaProducerService).send(argThat(e -> "LEGAL_ENTITY_REGISTERED".equals(e.eventType())));
        }

        @Test
        @DisplayName("выбрасывает исключение при дублирующем ИНН")
        void shouldThrowWhenInnExists() {
            when(legalEntityRepository.existsByInn("1234567890")).thenReturn(true);

            assertThatThrownBy(() -> legalEntityService.register(validRequest))
                    .isInstanceOf(LegalEntityAlreadyExistsException.class)
                    .hasMessageContaining("ИНН");
        }

        @Test
        @DisplayName("выбрасывает исключение при дублирующем email")
        void shouldThrowWhenEmailExists() {
            when(legalEntityRepository.existsByInn("1234567890")).thenReturn(false);
            when(legalEntityRepository.existsByEmail("company@example.com")).thenReturn(true);

            assertThatThrownBy(() -> legalEntityService.register(validRequest))
                    .isInstanceOf(LegalEntityAlreadyExistsException.class)
                    .hasMessageContaining("email");
        }
    }

    @Nested
    @DisplayName("confirmEmail")
    class ConfirmEmailTests {

        @Test
        @DisplayName("подтверждает email и отправляет уведомление менеджеру")
        void shouldConfirmEmail() {
            LegalEntity entity = LegalEntity.builder()
                    .id(1L).email("company@example.com").fullName("ООО Ромашка").inn("1234567890")
                    .emailConfirmToken("test-token").emailVerified(false)
                    .verificationStatus(VerificationStatus.PENDING)
                    .build();
            when(legalEntityRepository.findByEmailConfirmToken("test-token")).thenReturn(Optional.of(entity));
            when(legalEntityRepository.save(any())).thenReturn(entity);
            doNothing().when(kafkaProducerService).send(any());

            legalEntityService.confirmEmail("test-token");

            assertThat(entity.isEmailVerified()).isTrue();
            assertThat(entity.getEmailConfirmToken()).isNull();
            verify(kafkaProducerService).send(argThat(e -> "LEGAL_ENTITY_EMAIL_CONFIRMED".equals(e.eventType())));
        }

        @Test
        @DisplayName("выбрасывает исключение при невалидном токене")
        void shouldThrowWhenTokenInvalid() {
            when(legalEntityRepository.findByEmailConfirmToken("bad-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> legalEntityService.confirmEmail("bad-token"))
                    .isInstanceOf(LegalEntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("verify / reject")
    class VerificationTests {

        @Test
        @DisplayName("менеджер верифицирует юрлицо → VERIFIED")
        void shouldVerifyLegalEntity() {
            LegalEntity entity = LegalEntity.builder()
                    .id(1L).email("company@example.com").fullName("ООО Ромашка").inn("1234567890")
                    .verificationStatus(VerificationStatus.PENDING).emailVerified(true)
                    .build();
            when(legalEntityRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(legalEntityRepository.save(any())).thenReturn(entity);
            doNothing().when(kafkaProducerService).send(any());

            legalEntityService.verify(1L, "manager@rfsnab.ru");

            assertThat(entity.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
            assertThat(entity.getVerifiedBy()).isEqualTo("manager@rfsnab.ru");
            assertThat(entity.getVerifiedAt()).isNotNull();
            verify(kafkaProducerService).send(argThat(e -> "LEGAL_ENTITY_VERIFIED".equals(e.eventType())));
        }

        @Test
        @DisplayName("менеджер отклоняет юрлицо → REJECTED")
        void shouldRejectLegalEntity() {
            LegalEntity entity = LegalEntity.builder()
                    .id(1L).email("company@example.com").fullName("ООО Ромашка").inn("1234567890")
                    .verificationStatus(VerificationStatus.PENDING).emailVerified(true)
                    .build();
            when(legalEntityRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(legalEntityRepository.save(any())).thenReturn(entity);
            doNothing().when(kafkaProducerService).send(any());

            legalEntityService.reject(1L, "manager@rfsnab.ru", "Недействительный ИНН");

            assertThat(entity.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
            verify(kafkaProducerService).send(argThat(e ->
                    "LEGAL_ENTITY_REJECTED".equals(e.eventType()) &&
                    "Недействительный ИНН".equals(e.rejectionReason())));
        }
    }

    @Nested
    @DisplayName("linkToUser")
    class LinkTests {

        @Test
        @DisplayName("физлицо привязывает юрлицо по ИНН → PENDING")
        void shouldCreatePendingLink() {
            UserEntity user = UserEntity.builder().id(1L).email("user@example.com")
                    .firstname("Иван").lastname("Иванов").build();
            LegalEntity entity = LegalEntity.builder()
                    .id(10L).inn("1234567890").email("company@example.com")
                    .fullName("ООО Ромашка").verificationStatus(VerificationStatus.VERIFIED)
                    .build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(legalEntityRepository.findByInn("1234567890")).thenReturn(Optional.of(entity));
            when(userLegalEntityRepository.existsByUserIdAndLegalEntityId(1L, 10L)).thenReturn(false);
            when(userLegalEntityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(kafkaProducerService).send(any());

            legalEntityService.linkToUser(1L, "1234567890");

            verify(userLegalEntityRepository).save(argThat(link ->
                    link.getLinkStatus() == LinkStatus.PENDING));
            verify(kafkaProducerService).send(argThat(e -> "LEGAL_ENTITY_LINK_REQUESTED".equals(e.eventType())));
        }

        @Test
        @DisplayName("подтверждение привязки по токену → CONFIRMED")
        void shouldConfirmLink() {
            UserEntity user = UserEntity.builder().id(1L).email("user@example.com")
                    .firstname("Иван").lastname("Иванов").build();
            LegalEntity entity = LegalEntity.builder()
                    .id(10L).email("company@example.com").fullName("ООО Ромашка").build();
            UserLegalEntity link = UserLegalEntity.builder()
                    .user(user).legalEntity(entity)
                    .linkStatus(LinkStatus.PENDING).linkToken("link-token")
                    .build();
            when(userLegalEntityRepository.findByLinkToken("link-token")).thenReturn(Optional.of(link));
            when(userLegalEntityRepository.save(any())).thenReturn(link);
            doNothing().when(kafkaProducerService).send(any());

            legalEntityService.confirmLink("link-token");

            assertThat(link.getLinkStatus()).isEqualTo(LinkStatus.CONFIRMED);
            assertThat(link.getLinkedAt()).isNotNull();
            verify(kafkaProducerService).send(argThat(e -> "LEGAL_ENTITY_LINK_CONFIRMED".equals(e.eventType())));
        }
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure (LegalEntityService doesn't exist yet)**

```
cd user-service
mvn test -pl . -Dtest=LegalEntityServiceTest -q 2>&1 | head -20
```

Expected: compilation error — `LegalEntityService` not found.

- [ ] **Step 3: Implement LegalEntityService**

```java
package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.userservice.exceptions.*;
import ru.rfsnab.userservice.models.*;
import ru.rfsnab.userservice.models.dto.legal.*;
import ru.rfsnab.userservice.models.enums.*;
import ru.rfsnab.userservice.models.kafka.LegalEntityEvent;
import ru.rfsnab.userservice.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalEntityService {

    private final LegalEntityRepository legalEntityRepository;
    private final LegalEntityBankAccountRepository bankAccountRepository;
    private final LegalEntityAddressRepository addressRepository;
    private final UserLegalEntityRepository userLegalEntityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LegalEntityKafkaProducerService kafkaProducerService;

    @Transactional
    public LegalEntity register(RegisterLegalEntityRequest request) {
        if (legalEntityRepository.existsByInn(request.inn())) {
            throw new LegalEntityAlreadyExistsException("Юрлицо с таким ИНН уже зарегистрировано");
        }
        if (legalEntityRepository.existsByEmail(request.email())) {
            throw new LegalEntityAlreadyExistsException("Юрлицо с таким email уже зарегистрировано");
        }
        // Spec requirement: legal entity email must not match any physical user's email
        if (userRepository.existsByEmail(request.email())) {
            throw new LegalEntityAlreadyExistsException("Email уже используется физическим лицом");
        }

        String confirmToken = UUID.randomUUID().toString();

        LegalEntity entity = LegalEntity.builder()
                .inn(request.inn())
                .ogrn(request.ogrn())
                .fullName(request.fullName())
                .director(request.director())
                .phone(request.phone())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .legalCity(request.legalCity())
                .legalStreet(request.legalStreet())
                .legalBuilding(request.legalBuilding())
                .legalPostalCode(request.legalPostalCode())
                .verificationStatus(VerificationStatus.PENDING)
                .emailConfirmToken(confirmToken)
                .emailVerified(false)
                .build();

        entity = legalEntityRepository.save(entity);

        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_REGISTERED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), entity.getEmail(),
                null, LocalDateTime.now()
        ));

        log.info("Legal entity registered: inn={}, id={}", entity.getInn(), entity.getId());
        return entity;
    }

    @Transactional
    public void confirmEmail(String token) {
        LegalEntity entity = legalEntityRepository.findByEmailConfirmToken(token)
                .orElseThrow(() -> new LegalEntityNotFoundException("Недействительная ссылка подтверждения"));

        entity.setEmailVerified(true);
        entity.setEmailConfirmToken(null);
        legalEntityRepository.save(entity);

        // Notify manager
        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_EMAIL_CONFIRMED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), "manager@rfsnab.ru",
                null, LocalDateTime.now()
        ));

        log.info("Legal entity email confirmed: id={}", entity.getId());
    }

    @Transactional
    public LegalEntity verify(Long id, String managerEmail) {
        LegalEntity entity = getById(id);
        entity.setVerificationStatus(VerificationStatus.VERIFIED);
        entity.setVerifiedBy(managerEmail);
        entity.setVerifiedAt(LocalDateTime.now());
        entity = legalEntityRepository.save(entity);

        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_VERIFIED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), entity.getEmail(),
                null, LocalDateTime.now()
        ));

        log.info("Legal entity verified: id={} by {}", id, managerEmail);
        return entity;
    }

    @Transactional
    public LegalEntity reject(Long id, String managerEmail, String reason) {
        LegalEntity entity = getById(id);
        entity.setVerificationStatus(VerificationStatus.REJECTED);
        entity.setVerifiedBy(managerEmail);
        entity.setVerifiedAt(LocalDateTime.now());
        entity = legalEntityRepository.save(entity);

        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_REJECTED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), entity.getEmail(),
                reason, LocalDateTime.now()
        ));

        log.info("Legal entity rejected: id={} by {}, reason: {}", id, managerEmail, reason);
        return entity;
    }

    @Transactional
    public void linkToUser(Long userId, String inn) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new LegalEntityNotFoundException("Пользователь не найден"));
        LegalEntity entity = legalEntityRepository.findByInn(inn)
                .orElseThrow(() -> new LegalEntityNotFoundException("Юрлицо с ИНН " + inn + " не найдено"));

        if (entity.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new LegalEntityNotVerifiedException("Юрлицо не прошло верификацию");
        }
        if (userLegalEntityRepository.existsByUserIdAndLegalEntityId(userId, entity.getId())) {
            throw new LegalEntityAlreadyExistsException("Юрлицо уже привязано к этому аккаунту");
        }

        String linkToken = UUID.randomUUID().toString();
        UserLegalEntity link = UserLegalEntity.builder()
                .user(user)
                .legalEntity(entity)
                .linkStatus(LinkStatus.PENDING)
                .linkToken(linkToken)
                .build();
        userLegalEntityRepository.save(link);

        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_LINK_REQUESTED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), entity.getEmail(),
                user.getFirstname() + " " + user.getLastname(), LocalDateTime.now()
        ));

        log.info("Link requested: userId={} → legalEntityId={}", userId, entity.getId());
    }

    @Transactional
    public void confirmLink(String token) {
        UserLegalEntity link = userLegalEntityRepository.findByLinkToken(token)
                .orElseThrow(() -> new LegalEntityNotFoundException("Недействительная ссылка привязки"));

        link.setLinkStatus(LinkStatus.CONFIRMED);
        link.setLinkToken(null);
        link.setLinkedAt(LocalDateTime.now());
        userLegalEntityRepository.save(link);

        LegalEntity entity = link.getLegalEntity();
        UserEntity user = link.getUser();

        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_LINK_CONFIRMED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), user.getEmail(),
                null, LocalDateTime.now()
        ));

        log.info("Link confirmed: userId={} → legalEntityId={}", user.getId(), entity.getId());
    }

    @Transactional(readOnly = true)
    public LegalEntity getById(Long id) {
        return legalEntityRepository.findById(id)
                .orElseThrow(() -> new LegalEntityNotFoundException("Юрлицо не найдено: " + id));
    }

    @Transactional(readOnly = true)
    public List<LegalEntity> getByVerificationStatus(VerificationStatus status) {
        return legalEntityRepository.findAllByVerificationStatus(status);
    }

    @Transactional(readOnly = true)
    public List<UserLegalEntity> getConfirmedLinksForUser(Long userId) {
        return userLegalEntityRepository.findAllByUserIdAndLinkStatus(userId, LinkStatus.CONFIRMED);
    }

    // Bank accounts

    @Transactional
    public LegalEntityBankAccount addBankAccount(Long legalEntityId, SaveBankAccountRequest request) {
        LegalEntity entity = getById(legalEntityId);
        LegalEntityBankAccount account = LegalEntityBankAccount.builder()
                .legalEntity(entity)
                .bankName(request.bankName())
                .bik(request.bik())
                .correspondentAccount(request.correspondentAccount())
                .settlementAccount(request.settlementAccount())
                .primary(request.primary())
                .build();
        return bankAccountRepository.save(account);
    }

    @Transactional
    public void deleteBankAccount(Long legalEntityId, Long accountId) {
        LegalEntityBankAccount account = bankAccountRepository
                .findByIdAndLegalEntityId(accountId, legalEntityId)
                .orElseThrow(() -> new LegalEntityNotFoundException("Банковский счёт не найден"));
        bankAccountRepository.delete(account);
    }

    @Transactional(readOnly = true)
    public List<LegalEntityBankAccount> getBankAccounts(Long legalEntityId) {
        return bankAccountRepository.findAllByLegalEntityId(legalEntityId);
    }

    // Addresses

    @Transactional
    public LegalEntityAddress addAddress(Long legalEntityId, SaveLegalEntityAddressRequest request) {
        LegalEntity entity = getById(legalEntityId);
        LegalEntityAddress address = LegalEntityAddress.builder()
                .legalEntity(entity)
                .city(request.city())
                .street(request.street())
                .building(request.building())
                .apartment(request.apartment())
                .postalCode(request.postalCode())
                .primary(request.primary())
                .build();
        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(Long legalEntityId, Long addressId) {
        LegalEntityAddress address = addressRepository
                .findByIdAndLegalEntityId(addressId, legalEntityId)
                .orElseThrow(() -> new LegalEntityNotFoundException("Адрес не найден"));
        addressRepository.delete(address);
    }

    @Transactional(readOnly = true)
    public List<LegalEntityAddress> getAddresses(Long legalEntityId) {
        return addressRepository.findAllByLegalEntityId(legalEntityId);
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```
cd user-service
mvn test -pl . -Dtest=LegalEntityServiceTest -q
```

Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```
git add user-service/src/test/java/ru/rfsnab/userservice/services/LegalEntityServiceTest.java
git add user-service/src/main/java/ru/rfsnab/userservice/services/LegalEntityService.java
git commit -m "feat(user-service): implement LegalEntityService with TDD — registration, verification, linking"
```

---

## Task 9: REST Controllers

**Files:**
- Create: `user-service/src/main/java/ru/rfsnab/userservice/controllers/LegalEntityController.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/controllers/LegalEntityAdminController.java`
- Create: `user-service/src/main/java/ru/rfsnab/userservice/controllers/UserLegalEntityController.java`

- [ ] **Step 1: Create LegalEntityController**

```java
package ru.rfsnab.userservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.userservice.mappers.LegalEntityMapper;
import ru.rfsnab.userservice.models.dto.legal.*;
import ru.rfsnab.userservice.services.LegalEntityService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/legal-entities")
@RequiredArgsConstructor
public class LegalEntityController {

    private final LegalEntityService legalEntityService;

    @PostMapping("/register")
    public ResponseEntity<LegalEntityDto> register(@Valid @RequestBody RegisterLegalEntityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LegalEntityMapper.toDto(legalEntityService.register(request)));
    }

    @GetMapping("/confirm-email")
    public ResponseEntity<Void> confirmEmail(@RequestParam String token) {
        legalEntityService.confirmEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/confirm-link")
    public ResponseEntity<Void> confirmLink(@RequestParam String token) {
        legalEntityService.confirmLink(token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LegalEntityDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(LegalEntityMapper.toDto(legalEntityService.getById(id)));
    }

    @GetMapping("/{id}/addresses")
    public ResponseEntity<List<LegalEntityAddressDto>> getAddresses(@PathVariable Long id) {
        return ResponseEntity.ok(
                legalEntityService.getAddresses(id).stream()
                        .map(LegalEntityMapper::toAddressDto).toList());
    }

    @PostMapping("/{id}/addresses")
    public ResponseEntity<LegalEntityAddressDto> addAddress(
            @PathVariable Long id,
            @Valid @RequestBody SaveLegalEntityAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LegalEntityMapper.toAddressDto(legalEntityService.addAddress(id, request)));
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id, @PathVariable Long addressId) {
        legalEntityService.deleteAddress(id, addressId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/bank-accounts")
    public ResponseEntity<List<BankAccountDto>> getBankAccounts(@PathVariable Long id) {
        return ResponseEntity.ok(
                legalEntityService.getBankAccounts(id).stream()
                        .map(LegalEntityMapper::toBankAccountDto).toList());
    }

    @PostMapping("/{id}/bank-accounts")
    public ResponseEntity<BankAccountDto> addBankAccount(
            @PathVariable Long id,
            @Valid @RequestBody SaveBankAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LegalEntityMapper.toBankAccountDto(legalEntityService.addBankAccount(id, request)));
    }

    @DeleteMapping("/{id}/bank-accounts/{accountId}")
    public ResponseEntity<Void> deleteBankAccount(@PathVariable Long id, @PathVariable Long accountId) {
        legalEntityService.deleteBankAccount(id, accountId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Create LegalEntityAdminController**

```java
package ru.rfsnab.userservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.userservice.mappers.LegalEntityMapper;
import ru.rfsnab.userservice.models.dto.legal.LegalEntityDto;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.services.LegalEntityService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/legal-entities")
@RequiredArgsConstructor
public class LegalEntityAdminController {

    private final LegalEntityService legalEntityService;

    @GetMapping
    public ResponseEntity<List<LegalEntityDto>> getByStatus(
            @RequestParam(defaultValue = "PENDING") VerificationStatus status) {
        return ResponseEntity.ok(
                legalEntityService.getByVerificationStatus(status).stream()
                        .map(LegalEntityMapper::toDto).toList());
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<LegalEntityDto> verify(
            @PathVariable Long id,
            @RequestParam String managerEmail) {
        return ResponseEntity.ok(LegalEntityMapper.toDto(legalEntityService.verify(id, managerEmail)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<LegalEntityDto> reject(
            @PathVariable Long id,
            @RequestParam String managerEmail,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(LegalEntityMapper.toDto(
                legalEntityService.reject(id, managerEmail, body.get("reason"))));
    }
}
```

- [ ] **Step 3: Create UserLegalEntityController**

```java
package ru.rfsnab.userservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.userservice.mappers.LegalEntityMapper;
import ru.rfsnab.userservice.models.dto.legal.LegalEntityDto;
import ru.rfsnab.userservice.services.LegalEntityService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/me/legal-entities")
@RequiredArgsConstructor
public class UserLegalEntityController {

    private final LegalEntityService legalEntityService;

    @PostMapping("/link")
    public ResponseEntity<Void> link(Authentication authentication,
                                     @RequestBody Map<String, String> body) {
        Long userId = Long.parseLong(authentication.getName());
        legalEntityService.linkToUser(userId, body.get("inn"));
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<LegalEntityDto>> getMyLegalEntities(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                legalEntityService.getConfirmedLinksForUser(userId).stream()
                        .map(link -> LegalEntityMapper.toDto(link.getLegalEntity()))
                        .toList());
    }
}
```

- [ ] **Step 4: Commit**

```
git add user-service/src/main/java/ru/rfsnab/userservice/controllers/LegalEntity*
git add user-service/src/main/java/ru/rfsnab/userservice/controllers/UserLegalEntityController.java
git commit -m "feat(user-service): add LegalEntity, Admin, and UserLegalEntity REST controllers"
```

---

## Task 10: Controller Tests

**Files:**
- Create: `user-service/src/test/java/ru/rfsnab/userservice/controllers/LegalEntityControllerTest.java`

- [ ] **Step 1: Write controller tests**

```java
package ru.rfsnab.userservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.userservice.controllers.TestSecurityConfig;
import ru.rfsnab.userservice.exceptions.LegalEntityAlreadyExistsException;
import ru.rfsnab.userservice.exceptions.LegalEntityNotFoundException;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.dto.legal.RegisterLegalEntityRequest;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.services.LegalEntityService;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("LegalEntityController")
class LegalEntityControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean LegalEntityService legalEntityService;

    private static final String REGISTER_JSON = """
            {
                "inn": "1234567890",
                "ogrn": "1234567890123",
                "fullName": "ООО Ромашка",
                "director": "Иванов Иван Иванович",
                "phone": "+79001112233",
                "email": "company@example.com",
                "password": "password123",
                "legalCity": "Сыктывкар",
                "legalStreet": "Октябрьский проспект",
                "legalBuilding": "1",
                "legalPostalCode": "167000"
            }
            """;

    @Nested
    @DisplayName("POST /api/v1/legal-entities/register")
    class RegisterTests {

        @Test
        @DisplayName("201 Created — юрлицо зарегистрировано")
        void shouldRegister() throws Exception {
            LegalEntity entity = LegalEntity.builder()
                    .id(1L).inn("1234567890").ogrn("1234567890123")
                    .fullName("ООО Ромашка").director("Иванов Иван Иванович")
                    .phone("+79001112233").email("company@example.com").password("encoded")
                    .legalCity("Сыктывкар").legalStreet("Октябрьский проспект").legalBuilding("1")
                    .verificationStatus(VerificationStatus.PENDING)
                    .emailVerified(false).createdAt(LocalDateTime.now())
                    .build();
            when(legalEntityService.register(any(RegisterLegalEntityRequest.class))).thenReturn(entity);

            mockMvc.perform(post("/api/v1/legal-entities/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.inn").value("1234567890"))
                    .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
        }

        @Test
        @DisplayName("409 Conflict — ИНН уже существует")
        void shouldReturn409WhenInnExists() throws Exception {
            when(legalEntityService.register(any())).thenThrow(
                    new LegalEntityAlreadyExistsException("ИНН уже зарегистрирован"));

            mockMvc.perform(post("/api/v1/legal-entities/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_JSON))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("400 Bad Request — невалидный ИНН")
        void shouldReturn400WhenInnInvalid() throws Exception {
            String json = REGISTER_JSON.replace("\"1234567890\"", "\"123\"");
            mockMvc.perform(post("/api/v1/legal-entities/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/legal-entities/confirm-email")
    class ConfirmEmailTests {

        @Test
        @DisplayName("200 OK — email подтверждён")
        void shouldConfirmEmail() throws Exception {
            mockMvc.perform(get("/api/v1/legal-entities/confirm-email")
                            .param("token", "valid-token"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("404 Not Found — невалидный токен")
        void shouldReturn404WhenTokenInvalid() throws Exception {
            org.mockito.Mockito.doThrow(new LegalEntityNotFoundException("Токен не найден"))
                    .when(legalEntityService).confirmEmail("bad-token");

            mockMvc.perform(get("/api/v1/legal-entities/confirm-email")
                            .param("token", "bad-token"))
                    .andExpect(status().isNotFound());
        }
    }
}
```

- [ ] **Step 2: Run tests**

```
cd user-service
mvn test -pl . -Dtest=LegalEntityControllerTest -q
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 3: Commit**

```
git add user-service/src/test/java/ru/rfsnab/userservice/controllers/LegalEntityControllerTest.java
git commit -m "test(user-service): add LegalEntityController HTTP tests"
```

---

## Task 11: Full Build Verification

- [ ] **Step 1: Run all user-service tests**

```
cd user-service
mvn test -q
```

Expected: all tests pass, 0 failures.

- [ ] **Step 2: Build the service**

```
cd user-service
mvn package -DskipTests -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Final commit**

```
git add .
git commit -m "feat(user-service): complete B2B LegalEntity support — entities, service, controllers, tests"
```
