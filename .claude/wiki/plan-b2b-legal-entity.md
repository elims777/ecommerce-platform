# Plan: B2B Legal Entity — user-service (Plan A)

Created: 2026-05-17
Status: ✅ DONE (2026-05-18)

Full plan file: `docs/superpowers/plans/2026-05-17-b2b-legal-entity-user-service.md`
Spec file: `docs/superpowers/specs/2026-05-17-b2b-b2c-separation-design.md`

---

## Context

Задача: разделение B2B/B2C клиентов. Полный дизайн согласован (spec). Это Plan A из 3 планов.

**Plan A** — user-service (этот файл)
**Plan B** — auth-service (login/register/switch-context) + product-service (wholesalePrice) — не написан
**Plan C** — order-service (snapshot) + notification-service + frontend — не написан

---

## Key Design Decisions

- `LegalEntity` — самостоятельная сущность, существует без физлица
- Физлицо может привязать несколько юрлиц (many-to-many через `user_legal_entities`)
- Email юрлица ≠ email физлица (проверяется при регистрации)
- Верификация: PENDING → (email confirmed) → менеджер → VERIFIED/REJECTED
- Вход заблокирован до VERIFIED
- Переключение контекста B2C↔B2B — через `POST /auth/switch-context` (новый JWT) — в Plan B

---

## Task List (11 tasks)

| # | Task | Status |
|---|------|--------|
| 1 | Flyway migration — 4 tables | ✅ |
| 2 | Enums (VerificationStatus, LinkStatus) + LegalEntityEvent | ✅ |
| 3 | JPA entities (LegalEntity, BankAccount, Address, UserLegalEntity) | ✅ |
| 4 | Repositories (4 repos) | ✅ |
| 5 | DTOs + LegalEntityMapper | ✅ |
| 6 | LegalEntityKafkaProducerService + topic config | ✅ |
| 7 | Exceptions + UserRepository.existsByEmail | ✅ |
| 8 | LegalEntityService (TDD — tests first) | ✅ |
| 9 | REST Controllers (3 controllers) | ✅ |
| 10 | Controller tests | ✅ |
| 11 | Full build verification | ✅ |

---

## New Files Summary

### Migrations
- `user-service/src/main/resources/db/migration/V20260517180000__add_legal_entities.sql`

### Entities
- `models/LegalEntity.java`
- `models/LegalEntityBankAccount.java`
- `models/LegalEntityAddress.java`
- `models/UserLegalEntity.java`
- `models/UserLegalEntityId.java`

### Enums
- `models/enums/VerificationStatus.java` — PENDING, VERIFIED, REJECTED
- `models/enums/LinkStatus.java` — PENDING, CONFIRMED

### Kafka
- `models/kafka/LegalEntityEvent.java`
- `services/LegalEntityKafkaProducerService.java`
- New topic: `legal-entity-events`

### DTOs
- `models/dto/legal/RegisterLegalEntityRequest.java`
- `models/dto/legal/LegalEntityDto.java`
- `models/dto/legal/LegalEntityAddressDto.java`
- `models/dto/legal/SaveLegalEntityAddressRequest.java`
- `models/dto/legal/BankAccountDto.java`
- `models/dto/legal/SaveBankAccountRequest.java`

### Service & Mapper
- `services/LegalEntityService.java`
- `mappers/LegalEntityMapper.java`

### Controllers
- `controllers/LegalEntityController.java` — регистрация, подтверждение email, адреса, счета
- `controllers/LegalEntityAdminController.java` — верификация/отклонение
- `controllers/UserLegalEntityController.java` — привязка юрлица из ЛК

### Exceptions
- `exceptions/LegalEntityNotFoundException.java`
- `exceptions/LegalEntityAlreadyExistsException.java`
- `exceptions/LegalEntityNotVerifiedException.java`

### Modified files
- `repository/UserRepository.java` — добавить `existsByEmail(String)`
- `exceptions/GlobalExceptionHandler.java` — 3 новых handler
- `resources/application.yml` — топик `legal-entity-events`
- `resources/application-test.yml` — топик `legal-entity-events`

---

## Kafka Events (топик: legal-entity-events)

| Event | Кому уходит | Что в письме |
|-------|-------------|--------------|
| LEGAL_ENTITY_REGISTERED | юрлицу | подтвердите email (ссылка) |
| LEGAL_ENTITY_EMAIL_CONFIRMED | менеджеру | новое юрлицо на верификацию |
| LEGAL_ENTITY_VERIFIED | юрлицу | регистрация подтверждена |
| LEGAL_ENTITY_REJECTED | юрлицу | регистрация отклонена + причина |
| LEGAL_ENTITY_LINK_REQUESTED | юрлицу | пользователь хочет привязать, подтвердите |
| LEGAL_ENTITY_LINK_CONFIRMED | физлицу + юрлицу | привязка подтверждена |

---

## Endpoints

```
POST   /api/v1/legal-entities/register              — регистрация юрлица
GET    /api/v1/legal-entities/confirm-email?token=  — подтверждение email
POST   /api/v1/legal-entities/confirm-link?token=   — подтверждение привязки
GET    /api/v1/legal-entities/{id}                  — профиль
GET    /api/v1/legal-entities/{id}/addresses
POST   /api/v1/legal-entities/{id}/addresses
DELETE /api/v1/legal-entities/{id}/addresses/{aid}
GET    /api/v1/legal-entities/{id}/bank-accounts
POST   /api/v1/legal-entities/{id}/bank-accounts
DELETE /api/v1/legal-entities/{id}/bank-accounts/{bid}

GET    /api/v1/admin/legal-entities?status=PENDING  — список на верификацию
POST   /api/v1/admin/legal-entities/{id}/verify
POST   /api/v1/admin/legal-entities/{id}/reject

POST   /api/v1/users/me/legal-entities/link         — привязать юрлицо по ИНН
GET    /api/v1/users/me/legal-entities               — мои юрлица
```

---

## Next Session Start

1. Прочитай этот файл
2. Открой полный план: `docs/superpowers/plans/2026-05-17-b2b-legal-entity-user-service.md`
3. Начни с Task 1 (Flyway migration)
4. Выполняй задачи последовательно, каждая заканчивается коммитом
