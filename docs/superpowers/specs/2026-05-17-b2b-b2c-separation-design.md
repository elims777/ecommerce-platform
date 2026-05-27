# Design: B2B/B2C Client Separation

**Date:** 2026-05-17  
**Status:** Approved  
**Scope:** user-service, auth-service, product-service, order-service, notification-service, frontend

---

## Context

The platform currently has a single `UserEntity` model with no distinction between individual (B2C) and legal entity (B2B) clients. The frontend already has separate registration UI for both types. This spec covers the backend implementation of full B2B/B2C separation.

Key requirements:
- Legal entities are independent entities — they can exist without a linked physical user
- One physical user can link multiple legal entities and switch context between them
- Prices differ: retail for B2C, wholesale for B2B — both returned by backend, frontend renders the correct one
- B2B orders go through INVOICE_SENT flow; B2C goes directly to payment
- Legal entities must be verified by a manager before they can place orders

---

## Section 1 — Data Model

### user-service — new tables

**`legal_entities`**

| Field | Type | Notes |
|-------|------|-------|
| id | BIGINT, PK | |
| inn | VARCHAR(12) | unique, not null |
| ogrn | VARCHAR(15) | not null |
| full_name | VARCHAR(255) | legal company name |
| director | VARCHAR(255) | CEO full name (string) |
| phone | VARCHAR(20) | not null |
| email | VARCHAR(255) | unique, not null — must differ from any UserEntity.email |
| password | VARCHAR(255) | for standalone login |
| legal_city | VARCHAR(150) | legal address (embedded, always one) |
| legal_street | VARCHAR(150) | |
| legal_building | VARCHAR(20) | |
| legal_postal_code | VARCHAR(10) | |
| verification_status | ENUM `PENDING, VERIFIED, REJECTED` | default PENDING |
| verified_at | TIMESTAMP | nullable |
| verified_by | VARCHAR(255) | manager email, nullable |
| created_at, updated_at | TIMESTAMP | |

**`user_legal_entities`** (many-to-many: physical user ↔ legal entity)

| Field | Type | Notes |
|-------|------|-------|
| user_id | BIGINT, FK → users | |
| legal_entity_id | BIGINT, FK → legal_entities | |
| link_status | ENUM `PENDING, CONFIRMED` | |
| linked_at | TIMESTAMP | nullable |
| PK | (user_id, legal_entity_id) | composite |

**`legal_entity_bank_accounts`**

| Field | Type | Notes |
|-------|------|-------|
| id | BIGINT, PK | |
| legal_entity_id | BIGINT, FK → legal_entities | |
| bank_name | VARCHAR(255) | |
| bik | VARCHAR(9) | |
| correspondent_account | VARCHAR(20) | |
| settlement_account | VARCHAR(20) | |
| is_primary | BOOLEAN | |

**`legal_entity_addresses`** (actual/shipping addresses, multiple per legal entity)

| Field | Type | Notes |
|-------|------|-------|
| id | BIGINT, PK | |
| legal_entity_id | BIGINT, FK → legal_entities | |
| city | VARCHAR(150) | |
| street | VARCHAR(150) | |
| building | VARCHAR(20) | |
| apartment | VARCHAR(20) | nullable |
| postal_code | VARCHAR(10) | |
| is_primary | BOOLEAN | |

These addresses are returned alongside `UserAddress` entries when a user selects a delivery address during checkout.

### user-service — existing tables unchanged

`users` — no changes. Client type is determined by JWT context, not a field on the user.

### product-service — existing table changes

`products` — add fields:
- `retail_price` DECIMAL(12,2) NULL — renamed from current `price` (or kept as-is, see migration note)
- `wholesale_price` DECIMAL(12,2) NULL — new field, wholesale price

Both fields are nullable. Frontend shows whichever is set based on current context.

`ProductDto` — returns both: `retailPrice` + `wholesalePrice`.

### order-service — existing table changes

`orders` — add snapshot fields for B2B orders (all nullable, null for B2C):

| Field | Type |
|-------|------|
| customer_type | ENUM `B2C, B2B` |
| customer_inn | VARCHAR(12) |
| customer_ogrn | VARCHAR(15) |
| customer_company_name | VARCHAR(255) |
| customer_company_email | VARCHAR(255) |

Existing `customerEmail` field is reused for B2C. For B2B, `customer_company_email` holds the legal entity email.  
`DeliveryAddress.recipientName` + `phone` remain as-is — the recipient can be anyone.

---

## Section 2 — Authentication and JWT

### Two login paths in auth-service

**B2C:** `email + password` → search in `users`  
**B2B:** `email + password` OR `inn + password` → search in `legal_entities`

Both paths return a JWT. Login is blocked for legal entities with `verification_status != VERIFIED`.

### JWT claims

**B2C token:**
```json
{
  "sub": "userId",
  "email": "user@example.com",
  "clientType": "B2C",
  "roles": ["ROLE_USER"]
}
```

**B2B token (standalone legal entity login):**
```json
{
  "sub": "legalEntityId",
  "email": "company@example.com",
  "clientType": "B2B",
  "inn": "1234567890",
  "companyName": "ООО Ромашка"
}
```

**B2B token (physical user switched to legal entity context):**
```json
{
  "sub": "userId",
  "email": "user@example.com",
  "clientType": "B2B",
  "legalEntityId": 123,
  "inn": "1234567890",
  "companyName": "ООО Ромашка"
}
```

### Context switching

Physical users with confirmed linked legal entities can switch context:

```
POST /auth/switch-context
Body: { "legalEntityId": 123 }   // switch to B2B
Body: { "legalEntityId": null }  // switch back to B2C
```

auth-service validates that `user_legal_entities` contains a `CONFIRMED` link before issuing the B2B token. Frontend stores the new token and re-renders prices.

### Legal entity verification flow

1. Legal entity registers → `verification_status = PENDING`, email confirmation sent
2. Legal entity confirms email → manager notification sent
3. Manager verifies via admin panel → `verification_status = VERIFIED`, confirmation email sent to legal entity
4. Legal entity can now log in and place orders

---

## Section 3 — API Endpoints

### auth-service — new endpoints

```
POST /auth/register/legal       — register legal entity (verification_status = PENDING)
POST /auth/login/legal          — login by email or INN + password
POST /auth/switch-context       — switch B2C ↔ B2B context, returns new JWT
```

### user-service — new endpoints

**Legal entity management:**
```
GET    /api/v1/legal-entities/{id}                        — get legal entity profile
PUT    /api/v1/legal-entities/{id}                        — update details
GET    /api/v1/legal-entities/{id}/addresses              — list actual addresses
POST   /api/v1/legal-entities/{id}/addresses              — add address
DELETE /api/v1/legal-entities/{id}/addresses/{addressId}  — remove address
GET    /api/v1/legal-entities/{id}/bank-accounts          — list bank accounts
POST   /api/v1/legal-entities/{id}/bank-accounts          — add bank account
DELETE /api/v1/legal-entities/{id}/bank-accounts/{accountId}
```

**Physical user ↔ legal entity linking (from personal cabinet):**
```
POST   /api/v1/users/me/legal-entities/link   — link by INN (creates PENDING record, sends email to legal entity)
GET    /api/v1/users/me/legal-entities         — list confirmed linked legal entities
DELETE /api/v1/users/me/legal-entities/{id}    — unlink
```

**Link confirmation (from email link):**
```
POST   /api/v1/legal-entities/confirm-link?token=...
```

**Admin — legal entity verification:**
```
GET    /api/v1/admin/legal-entities?status=PENDING
POST   /api/v1/admin/legal-entities/{id}/verify
POST   /api/v1/admin/legal-entities/{id}/reject
```

### product-service — changes to existing endpoints

```
GET /api/v1/products       — add wholesalePrice to response DTO
GET /api/v1/products/{id}  — same
```

No new endpoints. Both price fields always returned; frontend selects which to display.

### order-service — changes to existing endpoints

```
POST /api/v1/orders   — no changes to request body
```

order-service reads `clientType` and `legalEntityId` / `inn` / `companyName` directly from the JWT token (forwarded by gateway as claims). If `clientType = B2B`, saves the snapshot fields (`customer_type`, `customer_inn`, etc.) into the order — no extra call to user-service needed.

---

## Section 4 — Kafka Events and Notifications

### New Kafka topic: `legal-entity-events`

Produced by user-service, consumed by notification-service.

| Event | Trigger |
|-------|---------|
| `LEGAL_ENTITY_REGISTERED` | Legal entity submitted registration form |
| `LEGAL_ENTITY_EMAIL_CONFIRMED` | Legal entity confirmed email |
| `LEGAL_ENTITY_VERIFIED` | Manager approved |
| `LEGAL_ENTITY_REJECTED` | Manager rejected |
| `LEGAL_ENTITY_LINK_REQUESTED` | Physical user requested link by INN |
| `LEGAL_ENTITY_LINK_CONFIRMED` | Legal entity confirmed the link |

### Email notifications

**On registration:**
- → legal entity email: "Подтвердите адрес электронной почты" (with confirmation link)

**After email confirmed:**
- → manager email: "Новое юрлицо на верификацию: ООО Ромашка, ИНН 1234567890"

**On manager verification:**
- → legal entity email: "Регистрация подтверждена, можете входить и заказывать"

**On manager rejection:**
- → legal entity email: "Регистрация отклонена" + reason

**On link request:**
- → legal entity email: "Пользователь Иванов Иван хочет привязать ваш аккаунт, подтвердите по ссылке"

**On link confirmed:**
- → physical user email: "ООО Ромашка успешно привязана к вашему аккаунту"
- → legal entity email: "Пользователь Иванов Иван успешно привязан"

---

## Section 5 — Testing

Strategy: Testcontainers (PostgreSQL, Kafka), mocks for external services — same as order-service.

**user-service tests:**
- Legal entity registration → status PENDING, email sent
- Login blocked until VERIFIED
- Manager verification → status VERIFIED
- Link flow: PENDING → CONFIRMED
- INN uniqueness enforced
- Legal entity email must differ from any UserEntity email

**auth-service tests:**
- Login by email for legal entity
- Login by INN for legal entity
- `switch-context` → JWT contains correct claims (legalEntityId, inn, companyName)
- Login blocked for PENDING/REJECTED legal entities

**product-service tests:**
- ProductDto returns both `retailPrice` and `wholesalePrice` (both nullable)

**order-service tests:**
- B2B order → legal entity snapshot fields populated
- B2C order → snapshot fields null

---

## Section 6 — Implementation Order

```
1. user-service        — LegalEntity entity, BankAccount, Address, Flyway migrations,
                         registration/verification flow, linking flow, admin endpoints
2. auth-service        — POST /auth/register/legal, POST /auth/login/legal,
                         POST /auth/switch-context, JWT claims update
3. product-service     — wholesalePrice field, Flyway migration, ProductDto update
4. order-service       — snapshot fields, Flyway migration, CreateOrderRequest update,
                         user-service client call for legal entity details
5. notification-service — legal-entity-events consumer, new email templates
6. frontend            — context switcher UI, price display logic,
                         legal entity registration form, ЛК linking flow
```
