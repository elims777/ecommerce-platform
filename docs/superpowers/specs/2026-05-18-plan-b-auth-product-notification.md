# Design: Plan B — Auth, Product, Notification (B2B support)

**Date:** 2026-05-18
**Status:** Draft — pending user approval
**Branch:** feature/plan-b-auth-product-notification (new branch from master)
**Depends on:** Plan A (user-service B2B legal entities) — merged to master

---

## Context

Plan A added LegalEntity support to user-service (registration, verification, bank accounts, addresses, linking). Plan B wires up authentication for legal entities, adds wholesale pricing to products, and adds email notifications for legal entity events.

---

## Section 1 — JWT Structure (unified)

Single JWT structure for both B2C and B2B. `sub` carries the ID of the active entity.

**B2C token (physical user):**
```json
{
  "sub": "42",
  "email": "user@mail.ru",
  "clientType": "B2C",
  "roles": ["ROLE_USER"]
}
```

**B2B token (legal entity — standalone login or after switch-context):**
```json
{
  "sub": "7",
  "email": "company@mail.ru",
  "clientType": "B2B",
  "roles": ["ROLE_USER"]
}
```

`sub` is always the ID of the currently active entity. `clientType` tells downstream services how to interpret `sub`. No B2B-specific fields (inn, companyName) in the token — services that need them call user-service directly.

---

## Section 2 — auth-service

### New endpoints

```
POST /api/v1/register/legal     — register legal entity (proxy to user-service POST /api/v1/legal-entities/register)
POST /v1/auth/login/legal       — login by email+password or INN+password
POST /v1/auth/switch-context    — switch active context (physical user → linked legal entity)
```

### Login flow — legal entity (`POST /v1/auth/login/legal`)

Request body:
```json
{ "login": "company@mail.ru OR 1234567890", "password": "..." }
```

1. Detect login type: 10/12 digits → INN, otherwise → email
2. Call user-service `POST /v1/legal-entities/authenticate` with `{login, password}`
3. user-service validates credentials + checks `verificationStatus == VERIFIED` (returns 403 if PENDING/REJECTED)
4. auth-service generates JWT: `sub=legalEntityId`, `email=legalEntity.email`, `clientType=B2B`, `roles=["ROLE_USER"]`
5. Return same `AuthResponse` structure as B2C login

### Switch-context flow (`POST /v1/auth/switch-context`)

Request requires valid B2C JWT (physical user). Body:
```json
{ "legalEntityId": 7 }
```

1. Extract `userId` from current JWT (`sub`)
2. Call user-service `GET /v1/users/{userId}/legal-entities/{legalEntityId}/link-status`
3. Validate link status == CONFIRMED (403 if not)
4. Fetch legal entity email from user-service
5. Issue new JWT: `sub=legalEntityId`, `email=legalEntity.email`, `clientType=B2B`, `roles=["ROLE_USER"]`

**Switch-context is one-directional:** physical user → legal entity only, no re-auth required.
Switching back from legal entity to physical user requires fresh login with physical user credentials.

### Registration proxy (`POST /api/v1/register/legal`)

Thin proxy — forwards request body to user-service `POST /api/v1/legal-entities/register`, returns response as-is. No auth-service logic.

### Modified files

- `JWTService.java` — add `generateToken(Long id, String email, String clientType)` overload; keep existing method for B2C
- `AuthController.java` — add `/login/legal`, `/switch-context`
- `RegistrationController.java` — add `/register/legal` proxy
- `AuthService.java` — add `authenticateLegalEntity()`, `switchContext()` methods
- New DTO: `LegalAuthRequest.java` (`login` + `password`), `SwitchContextRequest.java` (`legalEntityId`)

### New user-service internal endpoints (called by auth-service only)

```
POST /v1/legal-entities/authenticate         — validate credentials, return LegalEntityDto
GET  /v1/users/{userId}/legal-entities/{legalEntityId}/link-status  — check CONFIRMED link
```

These are internal endpoints, not exposed through gateway.

---

## Section 3 — gateway-service

Add `X-Client-Type` header forwarding alongside existing `X-User-Email`.

Gateway JWT filter currently forwards only `X-User-Email`. Plan B extends it to also forward:
- `X-User-Id: {sub}` — the ID from JWT subject (userId for B2C, legalEntityId for B2B)
- `X-Client-Type: B2C` or `X-Client-Type: B2B`

Downstream services use `X-Client-Type` to determine context. If they need more data (inn, companyName) they call user-service with `X-User-Id`.

---

## Section 4 — product-service

### DB migration

`V20260518120000__add_wholesale_price.sql`:
```sql
ALTER TABLE products ADD COLUMN wholesale_price DECIMAL(10,2) NULL;
```

Existing `price` column = оптовая цена (B2B, from 1С "Оптовая").
New `wholesale_price` column = розничная цена (B2C, from 1С "Розничная").

### Modified files

- `Product.java` — add `BigDecimal wholesalePrice`
- `ProductRequest.java` — add `BigDecimal wholesalePrice` (nullable)
- `ProductResponse.java` — add `BigDecimal wholesalePrice` (nullable, always returned)
- `ProductMapper.java` — map `wholesalePrice` in both directions
- `JwtService.java` — add `extractClientType()` method

No pricing selection logic in product-service — both prices always returned in response. Frontend selects which to display based on `clientType` from JWT/authStore.

---

## Section 5 — integration-service

### Price type mapping

CommerceML `ТипЦены.Наименование`:
- `"Оптовая"` → `price` (B2B price)
- `"Розничная"` → `wholesalePrice` (B2C price)

Matching by name string. If a price type is absent in the offer — field stays null.

### Modified files

- `CatalogImportService.java`:
  - Replace `extractPrice(offer)` with `extractPriceByType(offer, priceTypes, "Оптовая")` → `price`
  - Add `extractPriceByType(offer, priceTypes, "Розничная")` → `wholesalePrice`
  - Pass `priceTypes` list from `OffersPackage` into `mapToImportItem()`
- `ProductImportItemDto.java` — add `BigDecimal wholesalePrice`
- `BatchImportRequest` / product-service batch import endpoint — accept `wholesalePrice`

### Test fixtures

Update `src/test/resources/commerceml/offers.xml`:
- Add second `ТипЦены` "Розничная" with id `price-type-002`
- Add second `<Цена>` block per offer with `ИдТипаЦены=price-type-002` and a retail price value

---

## Section 6 — notification-service

### New Kafka consumer

Add listener for topic `legal-entity-events` in `KafkaListenerService`.

Add `LegalEntityEvent.java` model (mirror of user-service's `LegalEntityEvent` record):
```java
public record LegalEntityEvent(
    String eventType,
    Long legalEntityId,
    String email,
    String companyName,
    String inn,
    String token,        // for email confirmation / link confirmation links
    String reason,       // for REJECTED events
    String userName,     // for LINK_REQUESTED/LINK_CONFIRMED events
    String userEmail     // for LINK_CONFIRMED — second recipient
) {}
```

### New handler: `LegalEntityHandler.java`

Routes by `eventType`:

| Event | Email recipient | Subject |
|---|---|---|
| `LEGAL_ENTITY_REGISTERED` | legalEntity.email | Подтвердите email организации |
| `LEGAL_ENTITY_EMAIL_CONFIRMED` | manager email (from config) | Новое юрлицо на верификацию |
| `LEGAL_ENTITY_VERIFIED` | legalEntity.email | Регистрация подтверждена |
| `LEGAL_ENTITY_REJECTED` | legalEntity.email | Регистрация отклонена |
| `LEGAL_ENTITY_LINK_REQUESTED` | legalEntity.email | Запрос на привязку пользователя |
| `LEGAL_ENTITY_LINK_CONFIRMED` | legalEntity.email + user email | Привязка подтверждена |

### New EmailService methods

```java
sendLegalEntityVerificationEmail(String to, String companyName, String confirmUrl)
sendLegalEntityEmailConfirmedToManager(String managerEmail, String companyName, String inn)
sendLegalEntityVerifiedEmail(String to, String companyName)
sendLegalEntityRejectedEmail(String to, String companyName, String reason)
sendLegalEntityLinkRequestedEmail(String to, String companyName, String userName, String confirmUrl)
sendLegalEntityLinkConfirmedEmail(String toLegal, String toUser, String companyName, String userName)
```

### Config additions

`application.yml`:
```yaml
app.kafka.topic:
  legal-entity-events: legal-entity-events

app.email:
  manager: ${MANAGER_EMAIL}                        # receives LEGAL_ENTITY_EMAIL_CONFIRMED
  legal-entity-confirm-url: ${LEGAL_CONFIRM_URL}   # base URL for email confirmation links
  legal-entity-link-confirm-url: ${LEGAL_LINK_CONFIRM_URL}
```

---

## Section 7 — Testing

### auth-service

- B2B login by email → JWT with `clientType=B2B`, `sub=legalEntityId`
- B2B login by INN → same result
- Login blocked for PENDING legal entity → 403
- Login blocked for REJECTED legal entity → 403
- switch-context with CONFIRMED link → new B2B JWT
- switch-context with no link → 403
- register/legal proxy → delegates to user-service

### product-service

- `ProductResponse` contains both `price` and `wholesalePrice` (both nullable)
- Flyway migration applies cleanly

### integration-service

- Import with dual prices → `price` from "Оптовая", `wholesalePrice` from "Розничная"
- Import with only "Оптовая" → `wholesalePrice` is null
- Import with only "Розничная" → `price` is null

### notification-service

- All 6 `legal-entity-events` event types → correct email sent
- `LEGAL_ENTITY_LINK_CONFIRMED` → two emails sent (legal entity + user)
- Unknown event type → logged and skipped, no exception

---

## Section 8 — Implementation Order

```
1. user-service       — add internal auth endpoints (/v1/legal-entities/authenticate, link-status check)
2. auth-service       — JWT extension, login/legal, switch-context, register/legal proxy
3. gateway-service    — forward X-Client-Type header
4. product-service    — wholesalePrice field, Flyway migration, DTO update
5. integration-service — dual price extraction, test fixture update
6. notification-service — legal-entity-events consumer, LegalEntityHandler, 6 email methods
```
